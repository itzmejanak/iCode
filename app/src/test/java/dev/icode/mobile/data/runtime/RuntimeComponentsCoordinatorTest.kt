package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.RuntimeComponentsState
import dev.icode.mobile.domain.WorkbenchIntent
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

class RuntimeComponentsCoordinatorTest {
    private val originalWriter = BackendLog.writer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workbench = WorkbenchStateHolder()
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
    private val offer: File = File.createTempFile("components-offer", "").apply { deleteOnExit() }
    private val coordinator = RuntimeComponentsCoordinator(workbench, scope, installer, offer)

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun components(): RuntimeComponentsState = workbench.current.components

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; components: ${components()}" }
            Thread.sleep(5)
        }
    }

    @Test fun `the page sees what dpkg has, what the rest would download, and an install that lands`() {
        coordinator.start()
        until { components().installedPackages == setOf("git") }

        coordinator.handle(WorkbenchIntent.MeasureComponents)
        until { !components().measuring && components().downloads.isNotEmpty() }
        assertFalse("an installed component is not measured", "git" in components().downloads)
        assertEquals(mapOf("ripgrep_1_arm64.deb" to 2000L), components().downloads.getValue("ripgrep").archives)

        coordinator.handle(WorkbenchIntent.ApplyComponents(setOf("git", "ripgrep")))
        until { "ripgrep" in components().installedPackages.orEmpty() && components().work == null }
        assertTrue("what is installed changed: the sizes are measured again", components().downloads.isEmpty())
    }

    @Test fun `the offer after a build is answered once, by Skip or by Apply`() {
        coordinator.start()
        workbench.update { it.copy(components = it.components.copy(offerPending = true)) }
        coordinator.handle(WorkbenchIntent.SkipComponents)
        assertFalse(components().offerPending)
        assertFalse("not offered again", offer.exists())

        offer.createNewFile()
        workbench.update { it.copy(components = it.components.copy(offerPending = true)) }
        coordinator.handle(WorkbenchIntent.ApplyComponents(setOf("git", "ripgrep")))
        assertFalse(components().offerPending)
        assertFalse(offer.exists())
    }
}
