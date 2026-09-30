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
        assertTrue(BackgroundWorkRules.of(empty, installingPackage = null).isEmpty())
    }

    @Test fun `a task says its counts, its item, and the Cancel it allows`() {
        val item = BackgroundWorkRules.of(empty.copy(tasks = listOf(task(12, 1207, 11813, "node_modules/a/b.js", cancellable = true))), null).single()
        assertEquals("task:12", item.key)
        assertEquals("Deleting… (1207 / 11813)", item.heading)
        assertEquals("node_modules/a/b.js", item.detail)
        assertEquals(WorkbenchIntent.CancelTask(12), item.cancel)
        assertEquals(BackgroundPlace.Task(12), item.place)
        assertTrue(item.running && item.watched)
        assertEquals(12, item.number)
    }

    @Test fun `while counting it says so, a delete has no Cancel, and a hidden task is out of view`() {
        val item = BackgroundWorkRules.of(empty.copy(tasks = listOf(task(3, hidden = true))), null).single()
        assertEquals("Deleting…", item.heading)
        assertEquals("Counting node_modules", item.detail)
        assertNull(item.fraction)
        assertNull(item.cancel)
        assertFalse(item.watched)
    }

    @Test fun `a failed task stops running and says why`() {
        val item = BackgroundWorkRules.of(empty.copy(tasks = listOf(task(4, failure = "Permission denied"))), null).single()
        assertFalse(item.running)
        assertEquals("Permission denied", item.failure)
    }

    @Test fun `a clone is carried with Git's phase and share, and opens its own page`() {
        val snapshot = empty.copy(
            cloneName = "icode",
            gitOperation = GitOperationState.Running(GitTransferLabel.CLONING, GitProgress("Receiving objects", 0.45f)),
        )
        val item = BackgroundWorkRules.of(snapshot, null).single()
        assertEquals("Cloning… 45%", item.heading)
        assertEquals("icode · Receiving objects", item.detail)
        assertEquals(WorkbenchIntent.CancelGitTransfer, item.cancel)
        assertEquals(BackgroundPlace.Clone, item.place)
        assertEquals(900, item.number)
    }

    @Test fun `a pull opens the sync page, and a finished transfer is not carried`() {
        val pulling = empty.copy(activeWorkspace = "app", gitOperation = GitOperationState.Running(GitTransferLabel.PULLING))
        assertEquals(BackgroundPlace.Sync, BackgroundWorkRules.of(pulling, null).single().place)
        val done = empty.copy(gitOperation = GitOperationState.Succeeded(GitTransferLabel.PULLING))
        assertTrue(BackgroundWorkRules.of(done, null).isEmpty())
    }

    @Test fun `the runtime install and a package install are carried, neither cancellable`() {
        val snapshot = empty.copy(
            runtime = RuntimeState.Downloading(50, 100, "Ubuntu base"),
            runtimeInstallReason = RuntimeInstallReason.Repair,
        )
        val items = BackgroundWorkRules.of(snapshot, installingPackage = "git")
        assertEquals(listOf("runtime", "package"), items.map { it.key })
        assertEquals("Repairing the Linux runtime 50%", items[0].heading)
        assertEquals(BackgroundPlace.Runtime, items[0].place)
        assertEquals("Installing git", items[1].title)
        assertTrue(items.all { it.cancel == null && it.running })
    }

    @Test fun `a place survives the notification that carries it`() {
        listOf(BackgroundPlace.Task(7), BackgroundPlace.Clone, BackgroundPlace.Sync, BackgroundPlace.Runtime, BackgroundPlace.App).forEach {
            assertEquals(it, BackgroundPlace.decode(it.encoded()))
        }
        assertNull(BackgroundPlace.decode("task:x"))
        assertNull(BackgroundPlace.decode(null))
    }
}
