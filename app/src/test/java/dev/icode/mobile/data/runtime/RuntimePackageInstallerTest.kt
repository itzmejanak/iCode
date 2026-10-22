package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.AptPhase
import dev.icode.mobile.domain.RuntimeInventory
import dev.icode.mobile.domain.RuntimeTool
import dev.icode.mobile.domain.RuntimeToolAction
import dev.icode.mobile.domain.RuntimeToolRules
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.ToolSetupProgress
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimePackageInstallerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val calls = Collections.synchronizedList(mutableListOf<String>())
    private val running = AtomicInteger()
    private val mostAtOnce = AtomicInteger()

    @get:Rule val temporaryFolder = TemporaryFolder()
    private val receipts by lazy { File(temporaryFolder.root, "receipts") }

    @After fun stop() = scope.cancel()

    /**
     * A scripted apt, printing what apt prints (as recorded on the phone): the plan, `Get:` per
     * archive, dpkg's `Unpacking` / `Setting up` / `Removing`, `Remv` in a simulation, and
     * `--print-uris` lines. [needs] says which installed package needs which.
     */
    private inner class FakeApt(
        val available: Set<String> = setOf("git", "ripgrep", "openssh-client", "curl", "ca-certificates", "libcurl4t64"),
        var listsFresh: Boolean = true,
        val updateFails: Boolean = false,
        val needs: Map<String, Set<String>> = emptyMap(),
    ) {
        val installed: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val auto: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        /** Held before a download prints its first archive, before an install unpacks, and once it has set up. */
        var downloadGate: CompletableDeferred<Unit>? = null
        var installGate: CompletableDeferred<Unit>? = null
        var installedGate: CompletableDeferred<Unit>? = null

        fun installer(runtimeReady: Boolean = true, setups: List<RuntimeToolSetup> = emptyList()) = RuntimePackageInstaller(
            scope,
            { runtimeReady },
            { installed.toSet() },
            { argv, onLine ->
                val now = running.incrementAndGet()
                mostAtOnce.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                try {
                    calls += argv.joinToString(" ")
                    delay(10)
                    respond(argv, onLine)
                } finally {
                    running.decrementAndGet()
                }
            },
            ToolSetups(setups, receipts),
        )

        /** As apt: what no manually installed package needs, directly or through another. */
        private fun orphans(): List<String> {
            val kept = mutableSetOf<String>()
            val pending = installed.filterNot { it in auto }.toMutableList()
            while (pending.isNotEmpty()) {
                val name = pending.removeAt(pending.lastIndex)
                if (kept.add(name)) pending += needs[name].orEmpty().filter { it in installed }
            }
            return installed.filterNot { it in kept }
        }

        private suspend fun respond(argv: List<String>, onLine: (String) -> Boolean): GuestCommand.Result {
            val packages = argv.filterNot { it.startsWith("-") }.drop(2)
            val missing = packages.firstOrNull { !listsFresh || it !in available }
            fun ok() = GuestCommand.Result(0, "", false)
            fun unknown() = GuestCommand.Result(100, "E: Unable to locate package $missing", false)
            return when {
                argv[0] == "apt-mark" -> ok().also { if (argv[1] == "auto") auto += packages else auto -= packages.toSet() }
                argv[1] == "update" && updateFails ->
                    GuestCommand.Result(100, "E: Failed to fetch http://ports.ubuntu.com  Temporary failure resolving", false)
                argv[1] == "update" -> ok().also { listsFresh = true }
                argv[1] == "autoremove" -> ok().also { orphans().forEach { onLine("Remv $it [1]") } }
                argv[1] == "remove" -> {
                    onLine("0 upgraded, 0 newly installed, ${packages.size} to remove and 0 not upgraded.")
                    packages.forEach { onLine("Removing $it (1) ..."); installed -= it; auto -= it }
                    ok()
                }
                missing != null -> unknown()
                "--print-uris" in argv -> ok().also {
                    packages.filterNot { it in installed }.forEach { onLine("'http://ports.ubuntu.com/$it.deb' ${it}_1_arm64.deb 1000 MD5Sum:0") }
                }
                "-d" in argv -> {
                    val fresh = packages.filterNot { it in installed }
                    onLine("0 upgraded, ${fresh.size} newly installed, 0 to remove and 0 not upgraded.")
                    onLine("Need to get ${fresh.size} kB of archives.")
                    downloadGate?.await()
                    fresh.forEachIndexed { index, name -> onLine("Get:${index + 1} http://ports.ubuntu.com/ubuntu-ports noble/main arm64 $name arm64 1 [1 kB]") }
                    ok()
                }
                else -> {
                    val fresh = packages.filterNot { it in installed }
                    onLine("0 upgraded, ${fresh.size} newly installed, 0 to remove and 0 not upgraded.")
                    installGate?.await()
                    fresh.forEach { onLine("Unpacking $it (1) ...") }
                    fresh.forEach { onLine("Setting up $it (1) ..."); installed += it }
                    installedGate?.await()
                    ok()
                }
            }
        }
    }

    private fun installOf(name: String) = listOf(
        "apt-get install -d -y --no-install-recommends $name",
        "apt-get install -y --no-install-recommends $name",
    )

    @Test fun `an installed tool needs no apt`() = runBlocking {
        val apt = FakeApt().apply { installed += "git" }
        assertEquals(RuntimePackageInstaller.Outcome.Ready, apt.installer().ensure(RuntimeTools.Git))
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun `without the runtime nothing is attempted`() = runBlocking {
        assertEquals(
            RuntimePackageInstaller.Outcome.Failed("Install the Linux runtime first"),
            FakeApt().installer(runtimeReady = false).ensure(RuntimeTools.Git),
        )
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun `it downloads first, then installs what it downloaded`() = runBlocking {
        val apt = FakeApt()
        assertEquals(RuntimePackageInstaller.Outcome.Ready, apt.installer().ensure(RuntimeTools.Ripgrep))
        assertEquals(installOf("ripgrep"), calls)
        assertTrue("ripgrep" in apt.installed)
    }

    @Test fun `missing lists are read before downloading again`() = runBlocking {
        val installer = FakeApt(listsFresh = false).installer()
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer.ensure(RuntimeTools.Git))
        assertEquals(
            listOf("apt-get install -d -y --no-install-recommends git", "apt-get update") + installOf("git"),
            calls,
        )
    }

    @Test fun `a failed update says what apt said, and a feature's failure is not kept`() = runBlocking {
        val installer = FakeApt(listsFresh = false, updateFails = true).installer()
        assertEquals(
            RuntimePackageInstaller.Outcome.Failed(
                "Could not update runtime packages: E: Failed to fetch http://ports.ubuntu.com  Temporary failure resolving",
            ),
            installer.ensure(RuntimeTools.Git),
        )
        assertNull(installer.work.value)
    }

    @Test fun `a package apt does not know is reported with the tool's name`() = runBlocking {
        val nope = RuntimeTool("nope", "Nope", "", null, listOf("nope"))
        assertEquals(
            RuntimePackageInstaller.Outcome.Failed("Could not download Nope: E: Unable to locate package nope"),
            FakeApt().installer().ensure(nope),
        )
    }

    @Test fun `requests for the same tool share one install`() = runBlocking {
        val apt = FakeApt().apply { downloadGate = CompletableDeferred() }
        val installer = apt.installer()
        val requests = List(3) { async { installer.ensure(RuntimeTools.Git) } }
        delay(100)
        apt.downloadGate!!.complete(Unit)
        assertEquals(List(3) { RuntimePackageInstaller.Outcome.Ready }, withTimeout(2_000) { requests.awaitAll() })
        assertEquals(installOf("git"), calls)
    }

    @Test fun `different tools never run apt at the same time`() = runBlocking {
        val installer = FakeApt().installer()
        val outcomes = withTimeout(2_000) {
            listOf(RuntimeTools.Git, RuntimeTools.Ripgrep, RuntimeTools.SshClient)
                .map { tool -> async { installer.ensure(tool) } }.awaitAll()
        }
        assertEquals(List(3) { RuntimePackageInstaller.Outcome.Ready }, outcomes)
        assertEquals(1, mostAtOnce.get())
        assertEquals(6, calls.size)
    }

    @Test fun `a cancelled request does not stop the install`() = runBlocking {
        val apt = FakeApt().apply { downloadGate = CompletableDeferred() }
        val installer = apt.installer()
        val request = launch { installer.ensure(RuntimeTools.Ripgrep) }
        delay(100)
        request.cancelAndJoin()
        apt.downloadGate!!.complete(Unit)
        withTimeout(2_000) { while ("ripgrep" !in apt.installed) delay(10) }
        assertEquals(installOf("ripgrep"), calls)
    }

    @Test fun `its progress is published as apt prints it, and goes when it is done`() = runBlocking {
        val apt = FakeApt().apply {
            downloadGate = CompletableDeferred(); installGate = CompletableDeferred(); installedGate = CompletableDeferred()
        }
        val installer = apt.installer()
        val outcome = async { installer.ensure(RuntimeTools.SshClient) }
        withTimeout(2_000) { while (installer.work.value?.progress?.downloadSize == null) delay(5) }
        assertEquals("Installing SSH client", installer.work.value?.title)
        assertEquals("apt has printed its plan; no archive yet", "Reading package lists", installer.work.value?.progress?.step)
        assertTrue("reading and downloading may be cancelled", installer.work.value!!.cancellable)

        apt.downloadGate!!.complete(Unit)
        withTimeout(2_000) { while (installer.work.value?.progress?.phase != AptPhase.Installing) delay(5) }
        val installing = installer.work.value!!
        assertFalse("installing may not", installing.cancellable)
        assertTrue(installing.log.any { it.startsWith("Get:1 ") && " openssh-client " in it })

        apt.installGate!!.complete(Unit)
        withTimeout(2_000) { while (installer.work.value?.progress?.done != 1) delay(5) }
        assertEquals("1 / 1", installer.work.value?.progress?.counts)
        assertEquals("Setting up openssh-client (1) ...", installer.work.value?.log?.last())

        apt.installedGate!!.complete(Unit)
        assertEquals(RuntimePackageInstaller.Outcome.Ready, withTimeout(2_000) { outcome.await() })
        assertNull(installer.work.value)
        assertEquals(RuntimeInventory(setOf("openssh-client")), installer.installed.value)
    }

    @Test fun `a cancel while downloading stops before anything is installed`() = runBlocking {
        val apt = FakeApt().apply { downloadGate = CompletableDeferred() }
        val installer = apt.installer()
        val outcome = async { installer.ensure(RuntimeTools.Git) }
        withTimeout(2_000) { while (installer.work.value?.progress?.total == null) delay(5) }
        installer.cancel()
        assertEquals(RuntimePackageInstaller.Outcome.Cancelled, withTimeout(2_000) { outcome.await() })
        assertEquals(listOf("apt-get install -d -y --no-install-recommends git"), calls)
        assertFalse("git" in apt.installed)
        assertNull(installer.work.value)
    }

    @Test fun `a cancel once dpkg runs is refused`() = runBlocking {
        val apt = FakeApt().apply { installGate = CompletableDeferred() }
        val installer = apt.installer()
        val outcome = async { installer.ensure(RuntimeTools.Git) }
        withTimeout(2_000) { while (installer.work.value?.progress?.phase != AptPhase.Installing) delay(5) }
        installer.cancel()
        apt.installGate!!.complete(Unit)
        assertEquals(RuntimePackageInstaller.Outcome.Ready, withTimeout(2_000) { outcome.await() })
        assertTrue("git" in apt.installed)
    }

    @Test fun `apply removes what is unticked with what only it brought, then installs what is ticked`() = runBlocking {
        val apt = FakeApt(needs = mapOf("curl" to setOf("libcurl4t64"))).apply {
            installed += setOf("curl", "ca-certificates", "libcurl4t64")
            auto += "libcurl4t64"
        }
        val installer = apt.installer()
        val plan = RuntimeToolRules.plan(setOf("git"), RuntimeInventory(apt.installed.toSet()))
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer.apply(plan, emptySet()))
        assertEquals(setOf("git"), apt.installed.toSet())
        assertTrue(calls.contains("apt-get remove -y ca-certificates curl libcurl4t64"))
        assertNull(installer.work.value)
    }

    @Test fun `what another installed package needs is not removed, and the failure stays until read`() = runBlocking {
        val apt = FakeApt(needs = mapOf("git" to setOf("curl", "ca-certificates"))).apply {
            installed += setOf("git", "curl", "ca-certificates")
        }
        val installer = apt.installer()
        val outcome = installer.apply(RuntimeToolRules.plan(setOf("git"), RuntimeInventory(apt.installed.toSet())), emptySet())
        assertEquals(RuntimePackageInstaller.Outcome.Failed("curl cannot be removed: other installed packages need it"), outcome)
        assertEquals(setOf("git", "curl", "ca-certificates"), apt.installed.toSet())
        assertTrue("its marks are put back", apt.auto.isEmpty())
        val kept = installer.work.value!!
        assertEquals(RuntimeToolAction.Remove, kept.action)
        assertEquals("curl cannot be removed: other installed packages need it", kept.failure)
        installer.dismiss()
        assertNull(installer.work.value)
    }

    @Test fun `leftovers of an earlier removal are left alone`() = runBlocking {
        val apt = FakeApt().apply {
            installed += setOf("ripgrep", "libcurl4t64")
            auto += "libcurl4t64"
        }
        assertEquals(RuntimePackageInstaller.Outcome.Ready, apt.installer().apply(RuntimeToolRules.plan(emptySet(), RuntimeInventory(apt.installed.toSet())), emptySet()))
        assertEquals(setOf("libcurl4t64"), apt.installed.toSet())
    }

    @Test fun `measuring reads what each tool would download, reading the lists once when apt has none`() = runBlocking {
        val apt = FakeApt(listsFresh = false).apply { installed += "git" }
        val measured = apt.installer().measure(listOf(RuntimeTools.Git, RuntimeTools.Ripgrep, RuntimeTools.Curl))
        assertNull(measured.failure)
        assertEquals(emptyMap<String, Long>(), measured.downloads.getValue("git").archives)
        assertEquals(mapOf("ripgrep_1_arm64.deb" to 1000L), measured.downloads.getValue("ripgrep").archives)
        assertEquals(2, measured.downloads.getValue("curl").archives.size)
        assertEquals(1, calls.count { it == "apt-get update" })
    }

    @Test fun `a tool apt cannot find is not measured, and says why`() = runBlocking {
        val nope = RuntimeTool("nope", "Nope", "", null, listOf("nope"))
        val measured = FakeApt().installer().measure(listOf(nope, RuntimeTools.Ripgrep))
        assertEquals(setOf("ripgrep"), measured.downloads.keys)
        assertEquals("Could not measure Nope: E: Unable to locate package nope", measured.failure)
    }

    /** A scripted setup: it says a step, prints a line, and waits on [gate] where a test holds it. */
    private inner class FakeSetup : RuntimeToolSetup {
        override val id = "kit"
        override val receipt = "kit 1\n"
        var gate: CompletableDeferred<Unit>? = null
        var fails: String? = null

        override fun measure() = mapOf("kit.zip" to 5_000L)

        override suspend fun install(onProgress: (ToolSetupProgress) -> Unit, onLine: (String) -> Unit) {
            calls += "setup install"
            onProgress(ToolSetupProgress("Downloading the kit", 0.5f, "1 of 2 MB", cancellable = true))
            onLine("kit: fetched")
            gate?.await()
            fails?.let { throw IOException(it) }
        }

        override fun configure() {
            calls += "setup configure"
        }

        override suspend fun remove() {
            calls += "setup remove"
        }
    }

    private val kit = RuntimeTool("kit", "Kit", "", null, listOf("git"), setup = "kit", licence = "kit-licence")

    private fun planFor(installer: RuntimePackageInstaller, chosen: Set<String>) =
        RuntimeToolRules.plan(chosen, installer.run { refresh(); installed.value!! }, listOf(kit))

    @Test fun `a tool's setup runs once apt is done, its receipt is kept, and it reads installed`() = runBlocking {
        val apt = FakeApt()
        val setup = FakeSetup()
        val installer = apt.installer(setups = listOf(setup))
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer.apply(planFor(installer, setOf("kit")), setOf("kit-licence")))
        assertEquals(installOf("git") + listOf("setup install", "setup configure"), calls)
        assertEquals("kit 1\n", File(receipts, "kit").readText())
        assertEquals(RuntimeInventory(setOf("git"), setOf("kit")), installer.installed.value)
        assertTrue(installer.isInstalled(kit))
        assertNull(installer.work.value)
    }

    @Test fun `a receipt that matches skips the setup but not its configuration, as after a Repair`() = runBlocking {
        val apt = FakeApt()
        val setup = FakeSetup()
        File(receipts.apply { mkdirs() }, "kit").writeText(setup.receipt)
        val installer = apt.installer(setups = listOf(setup))
        assertFalse("the packages went with the system", installer.isInstalled(kit))
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer.apply(planFor(installer, setOf("kit")), setOf("kit-licence")))
        assertEquals(installOf("git") + "setup configure", calls)
    }

    @Test fun `a receipt for other pins reads as not installed, and installing runs the setup again`() = runBlocking {
        val apt = FakeApt().apply { installed += "git" }
        val setup = FakeSetup()
        File(receipts.apply { mkdirs() }, "kit").writeText("kit 0\n")
        val installer = apt.installer(setups = listOf(setup))
        assertFalse(installer.isInstalled(kit))
        installer.apply(planFor(installer, setOf("kit")), setOf("kit-licence"))
        assertTrue("setup install" in calls)
        assertTrue(installer.isInstalled(kit))
    }

    @Test fun `a tool whose licence was not accepted is not installed, and nothing runs`() = runBlocking {
        val apt = FakeApt()
        val setup = FakeSetup()
        val installer = apt.installer(setups = listOf(setup))
        val failed = RuntimePackageInstaller.Outcome.Failed("Kit installs only once its licence is accepted, in Runtime Tools")
        assertEquals(failed, installer.apply(planFor(installer, setOf("kit")), emptySet()))
        assertEquals(failed, installer.ensure(kit))
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun `a setup's progress and lines are published, and a cancel where it says so stops it without a receipt`() = runBlocking {
        val apt = FakeApt()
        val setup = FakeSetup().apply { gate = CompletableDeferred() }
        val installer = apt.installer(setups = listOf(setup))
        val outcome = async { installer.apply(planFor(installer, setOf("kit")), setOf("kit-licence")) }
        withTimeout(2_000) { while (installer.work.value?.setup == null) delay(5) }
        val work = installer.work.value!!
        assertEquals("Downloading the kit", work.step)
        assertEquals(0.5f, work.fraction)
        assertEquals("1 of 2 MB", work.item)
        withTimeout(2_000) { while (installer.work.value?.log?.lastOrNull() != "kit: fetched") delay(5) }
        assertTrue(installer.work.value!!.cancellable)
        installer.cancel()
        assertEquals(RuntimePackageInstaller.Outcome.Cancelled, withTimeout(2_000) { outcome.await() })
        assertFalse(File(receipts, "kit").exists())
        assertFalse(installer.isInstalled(kit))
    }

    @Test fun `a setup's failure is said in its words and kept, without a receipt`() = runBlocking {
        val apt = FakeApt()
        val setup = FakeSetup().apply { fails = "sdkmanager could not install the SDK packages: Connection refused" }
        val installer = apt.installer(setups = listOf(setup))
        val outcome = installer.apply(planFor(installer, setOf("kit")), setOf("kit-licence"))
        assertEquals(RuntimePackageInstaller.Outcome.Failed("sdkmanager could not install the SDK packages: Connection refused"), outcome)
        assertEquals("sdkmanager could not install the SDK packages: Connection refused", installer.work.value?.failure)
        assertFalse(File(receipts, "kit").exists())
    }

    @Test fun `a removal takes the setup away and its receipt, then the packages`() = runBlocking {
        val apt = FakeApt().apply { installed += "git" }
        val setup = FakeSetup()
        File(receipts.apply { mkdirs() }, "kit").writeText(setup.receipt)
        val installer = apt.installer(setups = listOf(setup))
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer.apply(planFor(installer, emptySet()), emptySet()))
        assertEquals("setup remove", calls.first())
        assertTrue(calls.contains("apt-get remove -y git"))
        assertFalse(File(receipts, "kit").exists())
        assertEquals(RuntimeInventory(emptySet()), installer.installed.value)
    }

    @Test fun `a tool apt has nothing for is its setup alone, measured, installed and removed without apt`() = runBlocking {
        val apt = FakeApt()
        val setup = FakeSetup()
        val files = RuntimeTool("kit", "Kit", "", null, emptyList(), setup = "kit", licence = "kit-licence")
        val installer = apt.installer(setups = listOf(setup))
        assertEquals(mapOf("kit.zip" to 5_000L), installer.measure(listOf(files)).downloads.getValue("kit").archives)
        val install = RuntimeToolRules.plan(setOf("kit"), installer.run { refresh(); installed.value!! }, listOf(files))
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer.apply(install, setOf("kit-licence")))
        assertEquals(listOf("setup install", "setup configure"), calls)
        val remove = RuntimeToolRules.plan(emptySet(), installer.run { refresh(); installed.value!! }, listOf(files))
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer.apply(remove, emptySet()))
        assertEquals(listOf("setup install", "setup configure", "setup remove"), calls)
        assertFalse(File(receipts, "kit").exists())
    }

    @Test fun `a tool's download is apt's archives and its setup's pinned files, until its receipt matches`() = runBlocking {
        val apt = FakeApt()
        val setup = FakeSetup()
        val installer = apt.installer(setups = listOf(setup))
        assertEquals(mapOf("git_1_arm64.deb" to 1000L, "kit.zip" to 5_000L), installer.measure(listOf(kit)).downloads.getValue("kit").archives)
        File(receipts.apply { mkdirs() }, "kit").writeText(setup.receipt)
        assertEquals(mapOf("git_1_arm64.deb" to 1000L), installer.measure(listOf(kit)).downloads.getValue("kit").archives)
    }
}
