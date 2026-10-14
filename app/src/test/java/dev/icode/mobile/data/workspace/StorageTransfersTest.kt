package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitCheckoutOperations
import dev.icode.mobile.data.git.GitCheckoutRepair
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ItemClipboard
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageTransfersTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateHolder = ICodeStateHolder()
    private val tasks = TaskRunner(stateHolder, scope)
    private val messages = Collections.synchronizedList(mutableListOf<String>())
    private val git = FakeGit()
    private var room = true
    private var access = true
    private var copying: CountDownLatch? = null

    private val root by lazy { File(temp.root, "files/workspaces").apply { mkdirs() } }
    private val phone by lazy { temp.newFolder("emulated", "0").canonicalFile }
    private val download by lazy { File(phone, "Download").apply { mkdirs() } }
    private val picked by lazy {
        File(phone, "Projects/app").apply {
            File(this, "src").mkdirs()
            File(this, "src/App.kt").writeText("fun main() {}")
            File(this, "gradlew").writeText("#!/bin/sh")
        }
    }
    private val workspace by lazy { File(root, "ws").apply { File(this, "tool").mkdirs() } }
    private val tool by lazy {
        File(workspace, "tool").apply {
            File(this, "run.sh").writeText("echo run")
            File(this, "notes.md").writeText("# notes")
        }
    }
    private val transfers by lazy {
        StorageTransfers(
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
        )
    }

    private class FakeGit : GitCheckoutOperations {
        var available = true
        val restored = Collections.synchronizedList(mutableListOf<String>())
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
            stateHolder.effects.collect { effect -> if (effect is ICodeEffect.Message) messages += effect.text }
        }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun held(item: File, cut: Boolean = false) = ItemClipboard(item.path, item.name, cut, folder = item.isDirectory, workspace = false)

    /** [held] pasted into [into]; where it landed once the task ended, or null. */
    private fun paste(held: ItemClipboard, into: File): File? {
        val landed = CompletableDeferred<File?>()
        transfers.paste(held, into.path) { landed.complete(it) }
        return runBlocking { withTimeout(5_000) { landed.await() } }
    }

    private fun failure() = stateHolder.current.tasks.singleOrNull()?.failure

    @Test fun `only a paste from one place into the other crosses`() {
        assertTrue(transfers.crosses(picked.path, root.path))
        assertTrue(transfers.crosses(tool.path, download.path))
        assertFalse(transfers.crosses(picked.path, download.path))
        assertFalse(transfers.crosses(tool.path, root.path))
    }

    @Test fun `a folder from phone storage comes in as Import's did — staged, then in place — and the original stays`() {
        val target = paste(held(picked), root)
        assertEquals(File(root, "app"), target)
        assertEquals("fun main() {}", File(root, "app/src/App.kt").readText())
        assertEquals(listOf("app"), root.list()!!.toList())
        assertTrue(File(picked, "src/App.kt").isFile)
        assertTrue("everything was carried: nothing to say", messages.isEmpty())
        assertTrue("not a repository: Git is not asked", git.restored.isEmpty())
    }

    @Test fun `a repository's checkout is restored while staged, before it is in place`() {
        File(picked, ".git").mkdirs()
        paste(held(picked), workspace)
        assertTrue(File(workspace, "app/.git").isDirectory)
        assertEquals(1, git.restored.size)
        assertTrue(git.restored.single().startsWith(".import-"))
    }

    @Test fun `without Git it still comes in, and says the checkout was not restored`() {
        File(picked, ".git").mkdirs()
        git.available = false
        assertEquals(File(root, "app"), paste(held(picked), root))
        assertEquals(listOf("Copied app. Git's executables and links were not restored: The runtime is not installed"), messages.toList())
    }

    @Test fun `onto phone storage links and names that differ only in case are left out, and said`() {
        Files.createSymbolicLink(File(tool, "latest").toPath(), File(tool, "run.sh").toPath())
        File(tool, "README.md").writeText("upper")
        File(tool, "readme.md").writeText("lower")
        val target = paste(held(tool), download)!!
        assertEquals(setOf("README.md", "notes.md", "run.sh"), target.list()!!.toSet())
        assertEquals("upper", File(target, "README.md").readText())
        assertEquals(listOf("Copied tool without 2 items phone storage cannot hold (links, or names that differ only in case)"), messages.toList())
    }

    @Test fun `a cut that cannot carry all of it is refused before anything is written`() {
        Files.createSymbolicLink(File(tool, "latest").toPath(), File(tool, "run.sh").toPath())
        assertNull(paste(held(tool, cut = true), download))
        assertEquals("tool holds 1 item phone storage cannot hold (links, or names that differ only in case) — copy it instead", failure())
        assertTrue(download.list()!!.isEmpty())
        assertTrue(File(tool, "run.sh").isFile)
    }

    @Test fun `a cut carries all of it, then removes the original`() {
        val target = paste(held(tool, cut = true), download)
        assertEquals(File(download, "tool"), target)
        assertEquals("echo run", File(download, "tool/run.sh").readText())
        assertFalse(tool.exists())
        assertTrue(messages.isEmpty())
    }

    @Test fun `a file goes too, and a copy takes a free name rather than overwrite`() {
        val note = File(phone, "notes.txt").apply { writeText("new") }
        File(workspace, "notes.txt").writeText("kept")
        assertEquals(File(workspace, "notes copy.txt"), paste(held(note), workspace))
        assertEquals("kept", File(workspace, "notes.txt").readText())
        assertEquals("new", File(workspace, "notes copy.txt").readText())
    }

    @Test fun `too little space refuses it before anything is copied, and nothing is left`() {
        room = false
        assertNull(paste(held(picked), root))
        assertTrue(failure()!!.startsWith("Not enough storage"))
        assertTrue(root.list()!!.isEmpty())
    }

    @Test fun `without All Files Access nothing is copied`() {
        access = false
        assertNull(paste(held(tool), download))
        assertEquals("Allow access to phone storage first", failure())
        assertTrue(download.list()!!.isEmpty())
    }

    @Test fun `a cancelled copy leaves nothing behind, and a cancelled cut leaves the original`() {
        copying = CountDownLatch(1)
        val landed = CompletableDeferred<File?>()
        transfers.paste(held(picked, cut = true), root.path) { landed.complete(it) }
        val task = stateHolder.current.tasks.single()
        tasks.handle(ICodeIntent.CancelTask(task.id))
        copying!!.countDown()
        assertNull(runBlocking { withTimeout(5_000) { landed.await() } })
        assertTrue(root.list()!!.isEmpty())
        assertTrue(File(picked, "src/App.kt").isFile)
    }
}
