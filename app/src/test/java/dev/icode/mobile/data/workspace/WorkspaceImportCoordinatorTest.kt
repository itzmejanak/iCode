package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitCheckoutOperations
import dev.icode.mobile.data.git.GitCheckoutRepair
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.storage.DiskImportTree
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.ImportSource
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceImportCoordinatorTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workbench = WorkbenchStateHolder()
    private val tasks = TaskRunner(workbench, scope)
    private val messages = Collections.synchronizedList(mutableListOf<String>())
    private val opened = Collections.synchronizedList(mutableListOf<String>())
    private val effects = Collections.synchronizedList(mutableListOf<WorkbenchEffect>())
    private val git = FakeGit()
    private var room = true
    private var copying: CountDownLatch? = null

    private val root by lazy { File(temp.root, "files/workspaces") }
    private val picked by lazy {
        temp.newFolder("phone", "app").apply {
            File(this, "src").mkdirs()
            File(this, "src/App.kt").writeText("fun main() {}")
            File(this, "gradlew").writeText("#!/bin/sh")
        }
    }
    private val coordinator by lazy {
        WorkspaceImportCoordinator(
            workbench = workbench,
            tasks = tasks,
            paths = WorkspacePaths(root),
            git = git,
            openTree = { uri ->
                copying?.await(5, TimeUnit.SECONDS)
                DiskImportTree(File(uri))
            },
            isOwnTree = { uri -> uri.startsWith("content://own") },
            reserveSpace = { room },
            freeBytes = { 1024 },
            open = { location -> opened += location },
        )
    }

    private class FakeGit : GitCheckoutOperations {
        var available = true
        var restored = mutableListOf<String>()
        override suspend fun isAvailable() = available
        override suspend fun provision(): GitOutcome<Unit> = GitOutcome.Failure("The runtime is not installed", 1)
        override suspend fun restoreCheckout(top: String): GitCheckoutRepair.Restored {
            restored += File(top).name
            return GitCheckoutRepair.Restored(1, 0)
        }
    }

    @Before fun collect() {
        BackendLog.writer = { _, _, _ -> }
        scope.launch(Dispatchers.Unconfined) {
            workbench.effects.collect { effect ->
                effects += effect
                if (effect is WorkbenchEffect.Message) messages += effect.text
            }
        }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; tasks: ${workbench.current.tasks}, messages: $messages" }
            Thread.sleep(5)
        }
    }

    private fun chosen() = runBlocking { coordinator.choose(picked.path) }

    @Test fun `the picked folder's name is proposed, and a folder of iCode's own is refused`() = runBlocking {
        coordinator.choose("content://own/tree/app")
        assertNull(workbench.current.importSource)
        assertEquals(listOf("This folder is already in iCode's workspaces"), messages.toList())
        chosen()
        assertEquals(ImportSource(picked.path, "app"), workbench.current.importSource)
        assertTrue(WorkbenchEffect.ImportSourceChosen in effects)
    }

    @Test fun `an import becomes the workspace in one rename and opens`() = runBlocking {
        chosen()
        coordinator.import(" app ")
        until { opened.isNotEmpty() }
        val workspace = File(root, "app")
        assertEquals(listOf(workspace.path), opened.toList())
        assertEquals("fun main() {}", File(workspace, "src/App.kt").readText())
        assertEquals(listOf("app"), root.list()!!.toList())
        assertNull(workbench.current.importSource)
        assertEquals(listOf("Imported app"), messages.toList())
        assertTrue("not a repository: Git is not asked", git.restored.isEmpty())
    }

    @Test fun `a repository's checkout is restored before it is a workspace`() = runBlocking {
        File(picked, ".git").mkdirs()
        chosen()
        coordinator.import("app")
        until { opened.isNotEmpty() }
        assertEquals(listOf("the staging folder, before the rename"), git.restored.map { if (it.startsWith(".import-")) "the staging folder, before the rename" else it })
    }

    @Test fun `without Git the import still completes, and says the checkout was not restored`() = runBlocking {
        File(picked, ".git").mkdirs()
        git.available = false
        chosen()
        coordinator.import("app")
        until { opened.isNotEmpty() }
        assertEquals(
            listOf("Imported app. Git's executables and links were not restored: The runtime is not installed"),
            messages.toList(),
        )
    }

    @Test fun `too little space refuses it before anything is copied, and nothing is left`() = runBlocking {
        room = false
        chosen()
        coordinator.import("app")
        until { workbench.current.tasks.singleOrNull()?.failure != null }
        assertTrue(workbench.current.tasks.single().failure!!.startsWith("Not enough storage"))
        until { root.list()!!.isEmpty() }
        assertTrue(opened.isEmpty())
    }

    @Test fun `a cancelled import leaves nothing behind`() = runBlocking {
        chosen()
        copying = CountDownLatch(1)
        coordinator.import("app")
        until { workbench.current.tasks.isNotEmpty() }
        tasks.handle(WorkbenchIntent.CancelTask(workbench.current.tasks.single().id))
        copying!!.countDown()
        until { workbench.current.tasks.isEmpty() }
        until { root.list()!!.isEmpty() }
        assertTrue(opened.isEmpty())
    }

    @Test fun `a name taken or not a workspace name is refused before the task`() = runBlocking {
        chosen()
        File(root, "app").mkdirs()
        coordinator.import("app")
        coordinator.import(".hidden")
        assertTrue(workbench.current.tasks.isEmpty())
        assertEquals(2, messages.size)
        assertEquals("A workspace named app already exists. Choose another name", messages[0])
        assertFalse(File(root, ".hidden").exists())
    }
}
