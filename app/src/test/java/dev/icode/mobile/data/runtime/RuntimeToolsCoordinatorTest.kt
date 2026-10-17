package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.RuntimeToolsState
import dev.icode.mobile.domain.ICodeIntent
import java.io.File
import java.util.Collections
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

    /** apt as it answers the page: sizes for what is missing, and an install that lands. */
    private val installer = RuntimePackageInstaller(scope, { true }, { installed.toSet() }) { argv, onLine ->
        val packages = argv.filterNot { it.startsWith("-") }.drop(2)
        when {
            "--print-uris" in argv -> packages.filterNot { it in installed }
                .forEach { onLine("'http://ports.ubuntu.com/$it.deb' ${it}_1_arm64.deb 2000 MD5Sum:0") }
            "-d" in argv -> Unit
            argv[1] == "install" -> installed += packages
        }
        GuestCommand.Result(0, "", false)
    }
    private val offer: File = File.createTempFile("tools-offer", "").apply { deleteOnExit() }
    private val coordinator = RuntimeToolsCoordinator(stateHolder, scope, installer, offer)

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

    @Test fun `the page sees what dpkg has, what the rest would download, and an install that lands`() {
        coordinator.start()
        until { tools().installedPackages == setOf("git") }

        coordinator.handle(ICodeIntent.MeasureRuntimeTools)
        until { !tools().measuring && tools().downloads.isNotEmpty() }
        assertFalse("an installed tool is not measured", "git" in tools().downloads)
        assertEquals(mapOf("ripgrep_1_arm64.deb" to 2000L), tools().downloads.getValue("ripgrep").archives)

        coordinator.handle(ICodeIntent.ApplyRuntimeTools(setOf("git", "ripgrep")))
        until { "ripgrep" in tools().installedPackages.orEmpty() && tools().work == null }
        assertTrue("what is installed changed: the sizes are measured again", tools().downloads.isEmpty())
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
