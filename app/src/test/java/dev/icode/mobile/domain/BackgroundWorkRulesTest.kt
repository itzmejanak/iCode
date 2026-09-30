package dev.icode.mobile.domain

import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundWorkRulesTest {
    private val empty = WorkbenchStateHolder.emptySnapshot()

    private fun task(id: Long, done: Long = 0, total: Long? = null, item: String? = null, cancellable: Boolean = false, hidden: Boolean = false, failure: String? = null) =
        WorkTask(id, "Deleting…", "node_modules", item, done, total, cancellable, hidden, failure)

    @Test fun `nothing running is nothing to carry`() {
        assertTrue(BackgroundWorkRules.of(empty).isEmpty())
    }

    @Test fun `a task says its counts, its item, and the Cancel it allows`() {
        val item = BackgroundWorkRules.of(empty.copy(tasks = listOf(task(12, 1207, 11813, "node_modules/a/b.js", cancellable = true)))).single()
        assertEquals("task:12", item.key)
        assertEquals("Deleting… (1207 / 11813)", item.heading)
        assertEquals("node_modules/a/b.js", item.detail)
        assertEquals(WorkbenchIntent.CancelTask(12), item.cancel)
        assertEquals(BackgroundPlace.Task(12), item.place)
        assertTrue(item.running && item.watched)
        assertEquals(12, item.number)
    }

    @Test fun `while counting it says so, a delete has no Cancel, and a hidden task is out of view`() {
        val item = BackgroundWorkRules.of(empty.copy(tasks = listOf(task(3, hidden = true)))).single()
        assertEquals("Deleting…", item.heading)
        assertEquals("Counting node_modules", item.detail)
        assertNull(item.fraction)
        assertNull(item.cancel)
        assertFalse(item.watched)
    }

    @Test fun `a failed task stops running and says why`() {
        val item = BackgroundWorkRules.of(empty.copy(tasks = listOf(task(4, failure = "Permission denied")))).single()
        assertFalse(item.running)
        assertEquals("Permission denied", item.failure)
    }

    @Test fun `a clone is carried with Git's phase and share, and opens its own page`() {
        val snapshot = empty.copy(
            cloneName = "icode",
            gitOperation = GitOperationState.Running(GitTransferLabel.CLONING, GitProgress("Receiving objects", 0.45f)),
        )
        val item = BackgroundWorkRules.of(snapshot).single()
        assertEquals("Cloning… 45%", item.heading)
        assertEquals("icode · Receiving objects", item.detail)
        assertEquals(WorkbenchIntent.CancelGitTransfer, item.cancel)
        assertEquals(BackgroundPlace.Clone, item.place)
        assertEquals(900, item.number)
    }

    @Test fun `a pull opens the sync page, and a finished transfer is not carried`() {
        val pulling = empty.copy(activeWorkspace = "app", gitOperation = GitOperationState.Running(GitTransferLabel.PULLING))
        assertEquals(BackgroundPlace.Sync, BackgroundWorkRules.of(pulling).single().place)
        val done = empty.copy(gitOperation = GitOperationState.Succeeded(GitTransferLabel.PULLING))
        assertTrue(BackgroundWorkRules.of(done).isEmpty())
        val installingGit = empty.copy(gitOperation = GitOperationState.Running("Installing Git"))
        assertTrue("installing Git is the components' run, carried as theirs", BackgroundWorkRules.of(installingGit).isEmpty())
    }

    @Test fun `the runtime install is carried, not cancellable`() {
        val snapshot = empty.copy(
            runtime = RuntimeState.Downloading(50, 100, "Ubuntu base"),
            runtimeInstallReason = RuntimeInstallReason.Repair,
        )
        val item = BackgroundWorkRules.of(snapshot).single()
        assertEquals("runtime", item.key)
        assertEquals("Repairing the Linux runtime 50%", item.heading)
        assertEquals(BackgroundPlace.Runtime, item.place)
        assertTrue(item.cancel == null && item.running)
    }

    @Test fun `a components install is carried with apt's step and count, cancellable only while downloading`() {
        val downloading = ComponentWork(
            ComponentAction.Install,
            listOf(RuntimeComponents.SshClient),
            AptProgress(phase = AptPhase.Downloading, total = 6, downloadSize = "1238 kB", item = "libcbor0.10"),
        )
        val item = BackgroundWorkRules.of(empty.copy(components = RuntimeComponentsState(work = downloading))).single()
        assertEquals("components", item.key)
        assertEquals("Installing SSH client", item.heading)
        assertEquals("Downloading 1238 kB · libcbor0.10", item.detail)
        assertEquals(WorkbenchIntent.CancelComponents, item.cancel)
        assertEquals(BackgroundPlace.Components, item.place)

        val installing = downloading.copy(progress = AptProgress(phase = AptPhase.Installing, total = 6, done = 3, item = "libfido2-1"))
        val counted = BackgroundWorkRules.of(empty.copy(components = RuntimeComponentsState(work = installing))).single()
        assertEquals("Installing SSH client (3 / 6)", counted.heading)
        assertEquals(0.5f, counted.fraction)
        assertEquals(null, counted.cancel)

        val failed = installing.copy(failure = "Could not install SSH client: E: Unable to fetch")
        assertEquals(false, BackgroundWorkRules.of(empty.copy(components = RuntimeComponentsState(work = failed))).single().running)
    }

    @Test fun `a place survives the notification that carries it`() {
        listOf(BackgroundPlace.Task(7), BackgroundPlace.Clone, BackgroundPlace.Sync, BackgroundPlace.Runtime, BackgroundPlace.Components).forEach {
            assertEquals(it, BackgroundPlace.decode(it.encoded()))
        }
        assertNull(BackgroundPlace.decode("task:x"))
        assertNull(BackgroundPlace.decode(null))
    }
}
