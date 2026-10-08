package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitCheckoutOperations
import dev.icode.mobile.data.git.GitCheckoutRepair
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.ImportSource
import dev.icode.mobile.domain.SystemAccess
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
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
    private val stateHolder = ICodeStateHolder()
    private val tasks = TaskRunner(stateHolder, scope)
    private val messages = Collections.synchronizedList(mutableListOf<String>())
    private val opened = Collections.synchronizedList(mutableListOf<String>())
    private val effects = Collections.synchronizedList(mutableListOf<ICodeEffect>())
    private val git = FakeGit()
    private var room = true
    private var copying: CountDownLatch? = null

    private var access = true
    private val root by lazy { File(temp.root, "files/workspaces") }
    private val phone by lazy { temp.newFolder("emulated", "0").canonicalFile }
    private val picked by lazy {
        File(phone, "Projects/app").apply {
            File(this, "src").mkdirs()
            File(this, "src/App.kt").writeText("fun main() {}")
            File(this, "gradlew").writeText("#!/bin/sh")
        }
    }
    private val coordinator by lazy {
        WorkspaceImportCoordinator(
            stateHolder = stateHolder,
            tasks = tasks,
            paths = WorkspacePaths(root),
            git = git,
            storage = PhoneStorage(phone) { access },
            reserveSpace = {
                copying?.await(5, TimeUnit.SECONDS)
                room
            },
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
            stateHolder.effects.collect { effect ->
                effects += effect
                if (effect is ICodeEffect.Message) messages += effect.text
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
            check(System.currentTimeMillis() < deadline) { "Timed out; tasks: ${stateHolder.current.tasks}, messages: $messages" }
            Thread.sleep(5)
        }
    }

    private fun chosen() = runBlocking { coordinator.choose(picked.path) }

    @Test fun `the browser waits for access, then lists where it was asked to open`() = runBlocking {
        assertTrue(picked.isDirectory)
        access = false
        coordinator.openBrowser(File(phone, "Projects").path)
        val waiting = stateHolder.current.importBrowser!!
        assertFalse(waiting.access)
        assertEquals(listOf("sdcard", "Projects"), waiting.crumbs.map { it.name })
        access = true
        val following = coordinator.followAccess(scope)
        stateHolder.update { it.copy(systemAccess = SystemAccess(notifications = false, unrestrictedBattery = false, storage = true)) }
        until { stateHolder.current.importBrowser?.access == true }
        assertEquals(listOf("app"), stateHolder.current.importBrowser!!.folders.map { it.name })
        following.cancel()
    }

    @Test fun `a breadcrumb step goes back up, and a folder gone since falls back to sdcard`() = runBlocking {
        assertTrue(picked.isDirectory)
        coordinator.openBrowser(picked.path)
        val crumbs = stateHolder.current.importBrowser!!.crumbs
        assertEquals(listOf("sdcard", "Projects", "app"), crumbs.map { it.name })
        coordinator.browse(crumbs[1].path)
        assertEquals(File(phone, "Projects").path, stateHolder.current.importBrowser!!.currentPath)
        coordinator.browse(crumbs[0].path)
        assertTrue(stateHolder.current.importBrowser!!.atRoot)
        coordinator.browse(File(phone, "gone").path)
        assertTrue(stateHolder.current.importBrowser!!.atRoot)
        assertEquals(1, messages.size)
    }

    @Test fun `the chosen folder's name is proposed, and neither the root nor a folder elsewhere can be chosen`() = runBlocking {
        coordinator.choose(phone.path)
        coordinator.choose(temp.newFolder("elsewhere").path)
        assertNull(stateHolder.current.importSource)
        assertEquals(2, messages.size)
        chosen()
        assertEquals(ImportSource(picked.path, "app", "sdcard / Projects / app"), stateHolder.current.importSource)
        assertTrue(ICodeEffect.ImportSourceChosen in effects)
    }

    @Test fun `nothing is chosen without access`() = runBlocking {
        access = false
        chosen()
        assertNull(stateHolder.current.importSource)
        assertEquals(listOf("Allow access to phone storage first"), messages.toList())
    }

    @Test fun `an import becomes the workspace in one rename and opens`() = runBlocking {
        chosen()
        coordinator.import(" app ")
        until { opened.isNotEmpty() }
        val workspace = File(root, "app")
        assertEquals(listOf(workspace.path), opened.toList())
        assertEquals("fun main() {}", File(workspace, "src/App.kt").readText())
        assertEquals(listOf("app"), root.list()!!.toList())
        assertNull(stateHolder.current.importSource)
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
        until { stateHolder.current.tasks.singleOrNull()?.failure != null }
        assertTrue(stateHolder.current.tasks.single().failure!!.startsWith("Not enough storage"))
        until { root.list()!!.isEmpty() }
        assertTrue(opened.isEmpty())
    }

    @Test fun `a cancelled import leaves nothing behind`() = runBlocking {
        chosen()
        copying = CountDownLatch(1)
        coordinator.import("app")
        until { stateHolder.current.tasks.isNotEmpty() }
        tasks.handle(ICodeIntent.CancelTask(stateHolder.current.tasks.single().id))
        copying!!.countDown()
        until { stateHolder.current.tasks.isEmpty() }
        until { root.list()!!.isEmpty() }
        assertTrue(opened.isEmpty())
    }

    @Test fun `a name taken or not a workspace name is refused before the task`() = runBlocking {
        chosen()
        File(root, "app").mkdirs()
        coordinator.import("app")
        coordinator.import(".hidden")
        assertTrue(stateHolder.current.tasks.isEmpty())
        assertEquals(2, messages.size)
        assertEquals("A workspace named app already exists", messages[0])
        assertFalse(File(root, ".hidden").exists())
    }
}
