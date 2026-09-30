package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.AptPhase
import dev.icode.mobile.domain.ComponentAction
import dev.icode.mobile.domain.ComponentRules
import dev.icode.mobile.domain.ComponentWork
import dev.icode.mobile.domain.RuntimeComponent
import dev.icode.mobile.domain.RuntimeComponents
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
import org.junit.Test

class RuntimePackageInstallerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val calls = Collections.synchronizedList(mutableListOf<String>())
    private val running = AtomicInteger()
    private val mostAtOnce = AtomicInteger()

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

        fun installer(runtimeReady: Boolean = true) = RuntimePackageInstaller(scope, { runtimeReady }, { installed.toSet() }) { argv, onLine ->
            val now = running.incrementAndGet()
            mostAtOnce.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            try {
                calls += argv.joinToString(" ")
                delay(10)
                respond(argv, onLine)
            } finally {
                running.decrementAndGet()
            }
        }

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

    @Test fun `an installed component needs no apt`() = runBlocking {
        val apt = FakeApt().apply { installed += "git" }
        assertEquals(RuntimePackageInstaller.Outcome.Ready, apt.installer().ensure(RuntimeComponents.Git))
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun `without the runtime nothing is attempted`() = runBlocking {
        assertEquals(
            RuntimePackageInstaller.Outcome.Failed("Install the Linux runtime first"),
            FakeApt().installer(runtimeReady = false).ensure(RuntimeComponents.Git),
        )
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun `it downloads first, then installs what it downloaded`() = runBlocking {
        val apt = FakeApt()
        assertEquals(RuntimePackageInstaller.Outcome.Ready, apt.installer().ensure(RuntimeComponents.Ripgrep))
        assertEquals(installOf("ripgrep"), calls)
        assertTrue("ripgrep" in apt.installed)
    }

    @Test fun `missing lists are read before downloading again`() = runBlocking {
        val installer = FakeApt(listsFresh = false).installer()
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer.ensure(RuntimeComponents.Git))
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
            installer.ensure(RuntimeComponents.Git),
        )
        assertNull(installer.work.value)
    }

    @Test fun `a package apt does not know is reported with the component's name`() = runBlocking {
        val nope = RuntimeComponent("nope", "Nope", "", null, listOf("nope"))
        assertEquals(
            RuntimePackageInstaller.Outcome.Failed("Could not download Nope: E: Unable to locate package nope"),
            FakeApt().installer().ensure(nope),
        )
    }

    @Test fun `requests for the same component share one install`() = runBlocking {
        val apt = FakeApt().apply { downloadGate = CompletableDeferred() }
        val installer = apt.installer()
        val requests = List(3) { async { installer.ensure(RuntimeComponents.Git) } }
        delay(100)
        apt.downloadGate!!.complete(Unit)
        assertEquals(List(3) { RuntimePackageInstaller.Outcome.Ready }, withTimeout(2_000) { requests.awaitAll() })
        assertEquals(installOf("git"), calls)
    }

    @Test fun `different components never run apt at the same time`() = runBlocking {
        val installer = FakeApt().installer()
        val outcomes = withTimeout(2_000) {
            listOf(RuntimeComponents.Git, RuntimeComponents.Ripgrep, RuntimeComponents.SshClient)
                .map { component -> async { installer.ensure(component) } }.awaitAll()
        }
        assertEquals(List(3) { RuntimePackageInstaller.Outcome.Ready }, outcomes)
        assertEquals(1, mostAtOnce.get())
        assertEquals(6, calls.size)
    }

    @Test fun `a cancelled request does not stop the install`() = runBlocking {
        val apt = FakeApt().apply { downloadGate = CompletableDeferred() }
        val installer = apt.installer()
        val request = launch { installer.ensure(RuntimeComponents.Ripgrep) }
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
        val outcome = async { installer.ensure(RuntimeComponents.SshClient) }
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
        assertEquals(setOf("openssh-client"), installer.installed.value)
    }

    @Test fun `a cancel while downloading stops before anything is installed`() = runBlocking {
        val apt = FakeApt().apply { downloadGate = CompletableDeferred() }
        val installer = apt.installer()
        val outcome = async { installer.ensure(RuntimeComponents.Git) }
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
        val outcome = async { installer.ensure(RuntimeComponents.Git) }
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
        val plan = ComponentRules.plan(setOf("git"), apt.installed.toSet())
        assertEquals(RuntimePackageInstaller.Outcome.Ready, installer.apply(plan))
        assertEquals(setOf("git"), apt.installed.toSet())
        assertTrue(calls.contains("apt-get remove -y ca-certificates curl libcurl4t64"))
        assertNull(installer.work.value)
    }

    @Test fun `what another installed package needs is not removed, and the failure stays until read`() = runBlocking {
        val apt = FakeApt(needs = mapOf("git" to setOf("curl", "ca-certificates"))).apply {
            installed += setOf("git", "curl", "ca-certificates")
        }
        val installer = apt.installer()
        val outcome = installer.apply(ComponentRules.plan(setOf("git"), apt.installed.toSet()))
        assertEquals(RuntimePackageInstaller.Outcome.Failed("curl cannot be removed: other installed packages need it"), outcome)
        assertEquals(setOf("git", "curl", "ca-certificates"), apt.installed.toSet())
        assertTrue("its marks are put back", apt.auto.isEmpty())
        val kept = installer.work.value!!
        assertEquals(ComponentAction.Remove, kept.action)
        assertEquals("curl cannot be removed: other installed packages need it", kept.failure)
        installer.dismiss()
        assertNull(installer.work.value)
    }

    @Test fun `leftovers of an earlier removal are left alone`() = runBlocking {
        val apt = FakeApt().apply {
            installed += setOf("ripgrep", "libcurl4t64")
            auto += "libcurl4t64"
        }
        assertEquals(RuntimePackageInstaller.Outcome.Ready, apt.installer().apply(ComponentRules.plan(emptySet(), apt.installed.toSet())))
        assertEquals(setOf("libcurl4t64"), apt.installed.toSet())
    }

    @Test fun `measuring reads what each component would download, reading the lists once when apt has none`() = runBlocking {
        val apt = FakeApt(listsFresh = false).apply { installed += "git" }
        val measured = apt.installer().measure(listOf(RuntimeComponents.Git, RuntimeComponents.Ripgrep, RuntimeComponents.Curl))
        assertNull(measured.failure)
        assertEquals(emptyMap<String, Long>(), measured.downloads.getValue("git").archives)
        assertEquals(mapOf("ripgrep_1_arm64.deb" to 1000L), measured.downloads.getValue("ripgrep").archives)
        assertEquals(2, measured.downloads.getValue("curl").archives.size)
        assertEquals(1, calls.count { it == "apt-get update" })
    }

    @Test fun `a component apt cannot find is not measured, and says why`() = runBlocking {
        val nope = RuntimeComponent("nope", "Nope", "", null, listOf("nope"))
        val measured = FakeApt().installer().measure(listOf(nope, RuntimeComponents.Ripgrep))
        assertEquals(setOf("ripgrep"), measured.downloads.keys)
        assertEquals("Could not measure Nope: E: Unable to locate package nope", measured.failure)
    }
}
