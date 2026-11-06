package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RuntimeInventory
import dev.icode.mobile.domain.RuntimeToolMeasuring
import dev.icode.mobile.domain.RuntimeToolsState
import dev.icode.mobile.domain.ToolSetupProgress
import java.io.File
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuntimeToolsCoordinatorTest {
    private val originalWriter = BackendLog.writer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateHolder = ICodeStateHolder()
    private val installed: MutableSet<String> = Collections.synchronizedSet(mutableSetOf("git"))

    /** A catalog setup, as the page measures it: [file] its one download. */
    private fun setup(id: String, file: String, bytes: Long) = object : RuntimeToolSetup {
        override val id = id
        override val receipt = "$id 1\n"
        override fun measure() = mapOf(file to bytes)
        override suspend fun install(onProgress: (ToolSetupProgress) -> Unit, onLine: (String) -> Unit) = Unit
        override fun configure() = Unit
        override suspend fun remove() = Unit
    }

    /** The catalog's setups: the Android SDK, CMake, the NDKs, Node.js and uv. */
    private val setups = listOf(
        setup("android-sdk", "sdk.zip", 7_000L),
        setup("cmake", "cmake.tar.gz", 3_000L),
        setup("android-ndk-28", "ndk-r28c.tar.xz", 9_000L),
        setup("android-ndk-27", "ndk-r27.tar.xz", 8_000L),
        setup("nodejs", "node-linux-arm64.tar.xz", 5_000L),
        setup("uv", "uv-manylinux_2_28_aarch64.whl", 4_000L),
    )

    /** Held before a size is printed, where a test holds it. */
    @Volatile private var printGate: CompletableDeferred<Unit>? = null

    /** apt as it answers the page: sizes for what is missing, and an install that lands. */
    private val installer = RuntimePackageInstaller(
        scope,
        { true },
        { installed.toSet() },
        { argv, onLine ->
            val packages = argv.filterNot { it.startsWith("-") }.drop(2)
            when {
                "--print-uris" in argv -> {
                    printGate?.await()
                    packages.filterNot { it in installed }.forEach { onLine("'http://ports.ubuntu.com/$it.deb' ${it}_1_arm64.deb 2000 MD5Sum:0") }
                }
                "-d" in argv -> Unit
                argv[1] == "install" -> installed += packages
            }
            GuestCommand.Result(0, "", false)
        },
        ToolSetups(setups, File.createTempFile("receipts", "").apply { delete(); deleteOnExit() }),
    )
    private val offer: File = File.createTempFile("tools-offer", "").apply { deleteOnExit() }
    private val coordinator = RuntimeToolsCoordinator(stateHolder, scope, installer, offer) { "The text of $it" }

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun tools(): RuntimeToolsState = stateHolder.current.runtimeTools

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; tools: ${tools()}" }
            Thread.sleep(5)
        }
    }

    @Test fun `the page sees what is installed, what the rest would download, and an install that lands`() {
        coordinator.start()
        until { tools().installed == RuntimeInventory(setOf("git")) }

        coordinator.handle(ICodeIntent.MeasureRuntimeTools)
        until { tools().measuring == null && tools().downloads.isNotEmpty() }
        assertFalse("an installed tool is not measured", "git" in tools().downloads)
        assertEquals(mapOf("ripgrep_1_arm64.deb" to 2000L), tools().downloads.getValue("ripgrep").archives)
        assertEquals(
            "apt's archives, then its setup's",
            mapOf("openjdk-17-jdk-headless_1_arm64.deb" to 2000L, "sdk.zip" to 7_000L),
            tools().downloads.getValue("android-sdk").archives,
        )
        assertEquals("a tool apt has nothing for is its setup's files", mapOf("cmake.tar.gz" to 3_000L), tools().downloads.getValue("cmake").archives)
        assertEquals(mapOf("xz-utils_1_arm64.deb" to 2000L, "ndk-r27.tar.xz" to 8_000L), tools().downloads.getValue("android-ndk-27").archives)
        assertEquals("a pinned download is its own file", mapOf("node-linux-arm64.tar.xz" to 5_000L), tools().downloads.getValue("nodejs").archives)

        coordinator.handle(ICodeIntent.ApplyRuntimeTools(setOf("git", "ripgrep")))
        until { "ripgrep" in tools().installed?.packages.orEmpty() && tools().work == null }
        assertTrue("what is installed changed: the sizes are measured again", tools().downloads.isEmpty())
    }

    @Test fun `a size measured against what was installed before is dropped, and the measure asked for meanwhile follows`() {
        printGate = CompletableDeferred()
        coordinator.start()
        until { tools().installed == RuntimeInventory(setOf("git")) }
        coordinator.handle(ICodeIntent.MeasureRuntimeTools)
        until { tools().measuring == RuntimeToolMeasuring.Sizes }
        // Installed elsewhere, as the terminal's apt would, while ripgrep's size is being measured.
        installed += "ripgrep"
        installer.refresh()
        until { "ripgrep" in tools().installed?.packages.orEmpty() }
        coordinator.handle(ICodeIntent.MeasureRuntimeTools)
        printGate?.complete(Unit)
        until { tools().measuring == null && "curl" in tools().downloads }
        assertFalse("measured before ripgrep was installed: dropped", "ripgrep" in tools().downloads)
        assertEquals(mapOf("openssh-client_1_arm64.deb" to 2000L), tools().downloads.getValue("ssh-client").archives)
    }

    @Test fun `each licence a tool accepts is read for the License Agreement`() {
        coordinator.start()
        until { tools().licences.isNotEmpty() }
        assertEquals(mapOf("android-sdk-license" to "The text of android-sdk-license"), tools().licences)
    }

    @Test fun `the Android SDK is installed only with its licence accepted`() {
        coordinator.start()
        until { tools().installed != null }
        coordinator.handle(ICodeIntent.ApplyRuntimeTools(setOf("git", "android-sdk")))
        until { tools().work?.failure != null }
        assertEquals("Android SDK installs only once its licence is accepted, in Runtime Tools", tools().work?.failure)
        coordinator.handle(ICodeIntent.DismissRuntimeTools)
        coordinator.handle(ICodeIntent.ApplyRuntimeTools(setOf("git", "android-sdk"), accepted = setOf("android-sdk-license")))
        until { tools().installed?.setups == setOf("android-sdk") && tools().work == null }
        assertTrue("openjdk-17-jdk-headless" in tools().installed!!.packages)
    }

    @Test fun `the offer after a build is answered once, by Skip or by Apply`() {
        coordinator.start()
        stateHolder.update { it.copy(runtimeTools = it.runtimeTools.copy(offerPending = true)) }
        coordinator.handle(ICodeIntent.SkipRuntimeTools)
        assertFalse(tools().offerPending)
        assertFalse("not offered again", offer.exists())

        offer.createNewFile()
        stateHolder.update { it.copy(runtimeTools = it.runtimeTools.copy(offerPending = true)) }
        coordinator.handle(ICodeIntent.ApplyRuntimeTools(setOf("git", "ripgrep")))
        assertFalse(tools().offerPending)
        assertFalse(offer.exists())
    }
}
