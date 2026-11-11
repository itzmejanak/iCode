package dev.icode.mobile.data.files

import dev.icode.mobile.data.git.GitCheckoutOperations
import dev.icode.mobile.data.git.GitCheckoutRepair
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.MemoryWorkspaceStore
import dev.icode.mobile.data.workspace.WorkspaceLifecycleCoordinator.Reopen
import dev.icode.mobile.data.workspace.WorkspaceUsage
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.notices.NoticeKeep
import dev.icode.mobile.domain.files.CompressFormat
import dev.icode.mobile.domain.files.ConflictAnswer
import dev.icode.mobile.domain.files.PasswordAsk
import dev.icode.mobile.domain.files.RenameRule
import java.io.File
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The explorers' operations on temporary Private and Shared folders (docs/explorer-gaps-plan.md §6,
 * F3): many copied, moved and duplicated; each answer to the question; a merge leaving `.git`;
 * Trash and restore with folders gone and names taken; undo and redo, and their refusal once
 * something changed; Recent and Favorites following.
 */
class FileOperationsTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateHolder = ICodeStateHolder()
    private val tasks = TaskRunner(stateHolder, scope)
    /** What was said — refusals, notes, failures — which ends an operation as a refresh does. */
    private val messages = Collections.synchronizedList(mutableListOf<String>())

    /** The changes kept as done, told before an operation has finished following them. */
    private val kept = Collections.synchronizedList(mutableListOf<String>())

    private val workspaces by lazy { temp.newFolder("files", "workspaces").canonicalFile }
    private val phone by lazy { temp.newFolder("emulated", "0").canonicalFile }
    private val app by lazy { folder(workspaces, "app") }
    private val follower by lazy { RecordingFollower(workspaces) }
    private val shortcuts by lazy { FileShortcuts(stateHolder, MemoryWorkspaceStore(), WorkspacePaths(workspaces)) }
    private val history: FileHistory by lazy { FileHistory(stateHolder) { steps -> files.follow(steps) } }
    private val files: FileOperationsCoordinator by lazy {
        val storage = PhoneStorage(phone) { true }
        val places = FilePlaces(workspaces, storage)
        val trash = FileTrash(File(workspaces.parentFile, "trash"), File(phone, "Android/media/dev.icode.mobile/Trash"), storage, WorkspacePaths(workspaces))
        val transfers = StorageTransfers(stateHolder, WorkspacePaths(workspaces), NoGit, storage, { true }, { Long.MAX_VALUE })
        val usage = WorkspaceUsage(stateHolder, tasks, { false }, { false }, { null }, { null })
        val operator = FileOperator(stateHolder, places, transfers, trash, follower)
        val questions = FileQuestions(stateHolder)
        val refusals = FileRefusals(places, trash, tasks, usage, follower, workspaces)
        val operations = FileTasks(stateHolder, tasks, operator, questions, usage, follower, history)
        val archives = FileArchives(stateHolder, places, trash, transfers, refusals, operations, history)
        val insights = FileInsightsCoordinator(stateHolder, scope, places, follower, StorageScan(places, trash), WorkspacePaths(workspaces))
        FileOperationsCoordinator(stateHolder, scope, follower, history, operator, questions, refusals, operations, archives, insights, shortcuts)
    }

    private object NoGit : GitCheckoutOperations {
        override suspend fun isAvailable() = false
        override suspend fun provision(): GitOutcome<Unit> = GitOutcome.Failure("No Git here", 1)
        override suspend fun restoreCheckout(top: String) = GitCheckoutRepair.Restored(0, 0)
    }

    /** What follows each change, said back: a whole workspace is a folder at Private's top. */
    private class RecordingFollower(private val workspaces: File) : FileFollower {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        @Volatile var refreshes = 0

        override fun isWorkspace(location: String) = File(location).parentFile == workspaces
        override suspend fun leaving(location: String): Reopen? = null.also { calls += "leaving ${File(location).name}" }
        override suspend fun stayed(location: String, reopen: Reopen?) {
            calls += "stayed ${File(location).name}"
        }
        override suspend fun moved(from: String, to: String, reopen: Reopen?) {
            calls += "moved ${File(from).name} ${File(to).name}"
        }
        override suspend fun removed(location: String) {
            calls += "removed ${File(location).name}"
        }
        override suspend fun refresh(touched: Collection<String>) {
            refreshes++
        }
        override suspend fun relist() {
            calls += "relist"
        }
    }

    @Before fun listen() {
        BackendLog.writer = { _, _, _ -> }
        scope.launch(Dispatchers.Unconfined) {
            stateHolder.effects.collect { effect ->
                val notice = (effect as? ICodeEffect.Told)?.notice ?: return@collect
                if (notice.keep == NoticeKeep.Done) kept += notice.title else messages += notice.title
            }
        }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun folder(parent: File, name: String) = File(parent, name).apply { mkdirs() }

    private fun file(parent: File, name: String, text: String = name) = File(parent, name).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    private fun waitUntil(condition: () -> Boolean) {
        await { condition().takeIf { it } }
    }

    private fun <T : Any> await(read: () -> T?): T = runBlocking {
        withTimeout(10_000) {
            var value = read()
            while (value == null) {
                delay(5)
                value = read()
            }
            value
        }
    }

    /**
     * [intent] done, each question met answered as [answers] say, in order; returns once it ended —
     * finished, refused, or failed (then why, from its task).
     */
    private fun perform(intent: FilesIntent, vararg answers: Pair<ConflictAnswer?, Boolean>): String? {
        val before = follower.refreshes + messages.size
        files.handle(intent)
        answers.forEach { (answer, forAll) ->
            val question = await { stateHolder.current.explorer.question }
            files.handle(FilesIntent.Answer(question.id, answer, forAll))
            waitUntil { stateHolder.current.explorer.question?.id != question.id }
        }
        waitUntil { follower.refreshes + messages.size > before && stateHolder.current.tasks.all { it.failure != null } }
        return stateHolder.current.tasks.singleOrNull()?.failure
    }

    /** [intent] held on the clipboard: nothing moves yet. */
    private fun hold(intent: FilesIntent) {
        files.handle(intent)
        await { stateHolder.current.explorer.clipboard }
    }

    private val undo get() = stateHolder.current.explorer.history.undo
    private val redo get() = stateHolder.current.explorer.history.redo

    @Test fun `many are copied into a folder, then undone to Trash, then redone`() {
        val a = file(app, "a.txt")
        val src = folder(app, "src").also { file(it, "Main.kt") }
        val into = folder(app, "lib")
        hold(FilesIntent.Copy(listOf(a.path, src.path)))
        perform(FilesIntent.Paste(into.path))
        assertEquals("a.txt", File(into, "a.txt").readText())
        assertEquals("Main.kt", File(into, "src/Main.kt").readText())
        assertTrue("a copy stays to paste again", stateHolder.current.explorer.clipboard != null)
        assertEquals("Copied 2 items to lib", undo)

        perform(FilesIntent.Undo)
        assertFalse(File(into, "a.txt").exists())
        assertFalse(File(into, "src").exists())
        assertEquals(2, stateHolder.current.explorer.trash.size)
        assertEquals("Copied 2 items to lib", redo)
        assertEquals("Moved the copies to Trash", stateHolder.current.explorer.history.news!!.line)

        perform(FilesIntent.Redo)
        assertEquals("Main.kt", File(into, "src/Main.kt").readText())
        assertTrue(stateHolder.current.explorer.trash.isEmpty())
        assertEquals("Copied 2 items to lib", undo)
    }

    @Test fun `many are moved in one step, a folder covering what is in it, and back with one undo`() {
        val src = folder(app, "src").also { file(it, "Main.kt") }
        val a = file(app, "a.txt")
        val into = folder(app, "lib")
        perform(FilesIntent.Transfer(listOf(src.path, File(src, "Main.kt").path, a.path), into.path, move = true))
        assertTrue(File(into, "src/Main.kt").isFile)
        assertTrue(File(into, "a.txt").isFile)
        assertFalse(src.exists())
        assertEquals("Moved 2 items to lib", undo)
        assertTrue(follower.calls.containsAll(listOf("moved src src", "moved a.txt a.txt")))

        perform(FilesIntent.Undo)
        assertTrue(File(src, "Main.kt").isFile)
        assertTrue(a.isFile)
        assertTrue(into.list()!!.isEmpty())
    }

    @Test fun `a cut pasted onto phone storage moves across, and leaves the clipboard`() {
        val src = folder(app, "src").also { file(it, "Main.kt", "fun main() {}") }
        val download = folder(phone, "Download")
        hold(FilesIntent.Cut(listOf(src.path)))
        perform(FilesIntent.Paste(download.path))
        assertEquals("fun main() {}", File(download, "src/Main.kt").readText())
        assertFalse(src.exists())
        assertNull(stateHolder.current.explorer.clipboard)

        perform(FilesIntent.Undo)
        assertEquals("fun main() {}", File(src, "Main.kt").readText())
        assertFalse(File(download, "src").exists())
    }

    @Test fun `duplicates go beside each item under a free name, and never ask`() {
        val a = file(app, "a.txt")
        val src = folder(app, "src")
        file(app, "a copy.txt")
        perform(FilesIntent.Duplicate(listOf(a.path, src.path)))
        assertTrue(File(app, "a copy 2.txt").isFile)
        assertTrue(File(app, "src copy").isDirectory)
        assertEquals("Duplicated 2 items", undo)
    }

    @Test fun `Replace puts the one there in Trash, and undo brings it back`() {
        val coming = file(folder(app, "new"), "notes.md", "new")
        val there = file(app, "notes.md", "old")
        perform(FilesIntent.Transfer(listOf(coming.path), app.path, move = true), ConflictAnswer.Replace to false)
        assertEquals("new", there.readText())
        assertEquals("notes.md", stateHolder.current.explorer.trash.single().name)

        perform(FilesIntent.Undo)
        assertEquals("old", there.readText())
        assertEquals("new", coming.readText())
        assertTrue(stateHolder.current.explorer.trash.isEmpty())
    }

    @Test fun `Keep both takes a free name, Skip leaves it, and the rest answered alike`() {
        val from = folder(app, "from")
        val names = listOf("a.txt", "b.txt", "c.txt")
        names.forEach { file(from, it, "new"); file(app, it, "old") }
        perform(FilesIntent.Transfer(names.map { File(from, it).path }, app.path, move = false), ConflictAnswer.KeepBoth to false, ConflictAnswer.Skip to true)
        assertEquals("new", File(app, "a copy.txt").readText())
        assertFalse(File(app, "b copy.txt").exists())
        assertFalse(File(app, "c copy.txt").exists())
        names.forEach { assertEquals("old", File(app, it).readText()) }
    }

    @Test fun `two folders merge, a name inside them asked the same way, and a moved folder left empty goes`() {
        val coming = folder(folder(app, "incoming"), "src").also { file(it, "A.kt", "new A"); file(it, "B.kt", "new B") }
        val there = folder(app, "src").also { file(it, "A.kt", "old A") }
        perform(FilesIntent.Transfer(listOf(coming.path), app.path, move = true), ConflictAnswer.Merge to false, ConflictAnswer.Replace to false)
        assertEquals("new A", File(there, "A.kt").readText())
        assertEquals("new B", File(there, "B.kt").readText())
        assertFalse(coming.exists())

        perform(FilesIntent.Undo)
        assertEquals("new A", File(coming, "A.kt").readText())
        assertEquals("new B", File(coming, "B.kt").readText())
        assertEquals("old A", File(there, "A.kt").readText())
        assertFalse(File(there, "B.kt").exists())
    }

    @Test fun `in Private a merge leaves Git's own folder as it is, and says so`() {
        val coming = folder(folder(app, "incoming"), "tool").also { file(it, ".git/HEAD", "theirs"); file(it, "run.sh") }
        val there = folder(app, "tool").also { file(it, ".git/HEAD", "ours") }
        perform(FilesIntent.Transfer(listOf(coming.path), app.path, move = true), ConflictAnswer.Merge to false)
        assertEquals("ours", File(there, ".git/HEAD").readText())
        assertTrue(File(there, "run.sh").isFile)
        assertEquals("theirs", File(coming, ".git/HEAD").readText())
        assertTrue(messages.any { it.startsWith("tool's .git was left where it was") })
    }

    @Test fun `stopped at the question, what it did stays, one change to undo`() {
        val from = folder(app, "from")
        file(from, "a.txt", "new")
        file(from, "b.txt", "new")
        file(app, "b.txt", "old")
        perform(FilesIntent.Transfer(listOf(File(from, "a.txt").path, File(from, "b.txt").path), app.path, move = true), null to false)
        assertEquals("new", File(app, "a.txt").readText())
        assertEquals("old", File(app, "b.txt").readText())
        assertEquals("Moved 2 items to app", undo)
        perform(FilesIntent.Undo)
        assertTrue(File(from, "a.txt").isFile)
    }

    @Test fun `Delete goes to Trash, and Restore brings it back, its folders made again`() {
        val deep = folder(app, "a/b")
        val note = file(deep, "note.md", "kept")
        perform(FilesIntent.Trash(listOf(note.path)))
        assertFalse(note.exists())
        val item = stateHolder.current.explorer.trash.single()
        assertEquals("Moved note.md to Trash", undo)
        assertTrue("kept on the Notifications page too", "Moved note.md to Trash" in kept)
        File(app, "a").deleteRecursively()
        perform(FilesIntent.Restore(listOf(item.id)))
        assertEquals("kept", note.readText())
        assertTrue(stateHolder.current.explorer.trash.isEmpty())
    }

    @Test fun `a restore whose name is taken asks, and Keep both restores beside it`() {
        val note = file(app, "note.md", "trashed")
        perform(FilesIntent.Trash(listOf(note.path)))
        file(app, "note.md", "new")
        perform(FilesIntent.Restore(listOf(stateHolder.current.explorer.trash.single().id)), ConflictAnswer.KeepBoth to false)
        assertEquals("new", note.readText())
        assertEquals("trashed", File(app, "note copy.md").readText())
    }

    @Test fun `a whole workspace goes to Trash as it is deleted, and back a workspace`() {
        file(app, "README.md")
        perform(FilesIntent.Trash(listOf(app.path)))
        assertEquals(listOf("leaving app", "removed app"), follower.calls)
        assertTrue(stateHolder.current.explorer.trash.single().workspace)
        perform(FilesIntent.Undo)
        assertTrue(File(app, "README.md").isFile)
    }

    @Test fun `names swapped by one rule rename through a passing name, and swap back with one undo`() {
        val one = file(app, "Photo 1.png", "one")
        val two = file(app, "Photo 2.png", "two")
        perform(FilesIntent.RenameMany(listOf(two.path, one.path), RenameRule.Number("Photo", start = 1)))
        assertEquals("two", one.readText())
        assertEquals("one", two.readText())
        assertEquals(setOf("Photo 1.png", "Photo 2.png"), app.list()!!.toSet())
        assertEquals("Renamed 2 items", undo)
        perform(FilesIntent.Undo)
        assertEquals("one", one.readText())
        assertEquals("two", two.readText())
        assertEquals(setOf("Photo 1.png", "Photo 2.png"), app.list()!!.toSet())
    }

    @Test fun `undo is refused once what it touches changed, and the history stays`() {
        val a = file(app, "a.txt")
        val into = folder(app, "lib")
        perform(FilesIntent.Transfer(listOf(a.path), into.path, move = true))
        File(into, "a.txt").renameTo(File(into, "b.txt"))
        perform(FilesIntent.Undo)
        assertEquals("a.txt was moved or deleted since", messages.last())
        assertEquals("Moved a.txt to lib", undo)
        assertTrue(File(into, "b.txt").isFile)
    }

    @Test fun `Extract here gives the one folder an archive holds, or a folder named after it, and undoes to Trash`() {
        val site = folder(app, "site").also { file(it, "index.html") }
        perform(FilesIntent.Compress(listOf(site.path), CompressFormat.Zip))
        assertTrue(File(app, "site.zip").isFile)
        assertEquals("Compressed site into site.zip", undo)
        site.deleteRecursively()
        perform(FilesIntent.Extract(File(app, "site.zip").path))
        assertEquals("index.html", File(app, "site/index.html").readText())
        val loose = folder(app, "loose").also { file(it, "a.txt"); file(it, "b.txt") }
        perform(FilesIntent.Compress(listOf(File(loose, "a.txt").path, File(loose, "b.txt").path), CompressFormat.TarGz))
        perform(FilesIntent.Extract(File(loose, "loose.tar.gz").path, folder(app, "out").path))
        assertEquals(setOf("a.txt", "b.txt"), File(app, "out/loose").list()!!.toSet())
        assertEquals("Extracted loose.tar.gz", undo)
        perform(FilesIntent.Undo)
        assertFalse(File(app, "out/loose").exists())
        assertTrue(File(workspaces.parentFile, "trash/staging").list().orEmpty().isEmpty())
    }

    @Test fun `Extract meeting a name asks, and Compress takes a free one`() {
        val notes = file(app, "notes.txt", "kept")
        perform(FilesIntent.Compress(listOf(notes.path), CompressFormat.SevenZ))
        perform(FilesIntent.Compress(listOf(notes.path), CompressFormat.SevenZ))
        assertTrue(File(app, "notes copy.7z").isFile)
        file(app, "notes/older.txt")
        perform(FilesIntent.Extract(File(app, "notes.7z").path), ConflictAnswer.KeepBoth to false)
        assertEquals("kept", File(app, "notes copy/notes.txt").readText())
        assertEquals(listOf("older.txt"), File(app, "notes").list()!!.toList())
    }

    @Test fun `a workspace is not compressed at Private's top, where only workspaces are`() {
        file(app, "a.txt")
        assertEquals(
            "Only workspaces are at Private's top: copy it to Shared, or into a workspace, and compress it there",
            perform(FilesIntent.Compress(listOf(app.path), CompressFormat.Zip)) ?: messages.last(),
        )
        assertEquals(listOf("app"), workspaces.list()!!.toList())
    }

    @Test fun `a 7z locked by a password waits on it, never keeping it`() {
        val archive = File(app, "secret.7z")
        SevenZOutputFile(archive, "s3cret".toCharArray()).use { seven ->
            val notes = file(temp.root, "notes.md", "hidden")
            seven.putArchiveEntry(seven.createArchiveEntry(notes, "notes.md"))
            seven.write(notes.toPath())
            seven.closeArchiveEntry()
        }
        files.handle(FilesIntent.Extract(archive.path))
        assertEquals(PasswordAsk(archive.path, null), await { stateHolder.current.explorer.password })
        assertEquals("The password for secret.7z is wrong, or the archive is damaged", perform(FilesIntent.Extract(archive.path, password = "nope")))
        assertNull(stateHolder.current.explorer.password)
        tasks.handle(ICodeIntent.DismissTask(stateHolder.current.tasks.single().id))
        perform(FilesIntent.Extract(archive.path, password = "s3cret"))
        assertEquals("hidden", File(app, "secret/notes.md").readText())
    }

    @Test fun `Recent and Favorites follow a move and its undo, and what is in Trash is kept, not shown`() {
        val src = folder(app, "src").also { file(it, "Main.kt") }
        val lib = folder(app, "lib")
        runBlocking {
            shortcuts.opened(File(src, "Main.kt").path)
            shortcuts.handle(FilesIntent.Favorite(src.path, pinned = true))
        }
        fun recent() = stateHolder.current.explorer.recent.map { it.location }
        fun favorites() = stateHolder.current.explorer.favorites.map { it.location }
        perform(FilesIntent.Transfer(listOf(src.path), lib.path, move = true))
        assertEquals(listOf(File(lib, "src/Main.kt").path), recent())
        assertEquals(listOf(File(lib, "src").path), favorites())
        perform(FilesIntent.Undo)
        assertEquals(listOf(File(src, "Main.kt").path), recent())
        assertEquals(listOf(src.path), favorites())
        perform(FilesIntent.Trash(listOf(src.path)))
        assertEquals(emptyList<String>(), recent())
        assertEquals(emptyList<String>(), favorites())
        perform(FilesIntent.Undo)
        assertEquals(listOf(File(src, "Main.kt").path), recent())
        assertEquals(listOf(src.path), favorites())
    }

    @Test fun `nothing starts when one item cannot go`() {
        val a = file(app, "a.txt")
        val src = folder(app, "src")
        assertNull(perform(FilesIntent.Transfer(listOf(a.path, app.path), src.path, move = true)))
        assertEquals("app cannot go inside itself", messages.last())
        assertTrue(a.isFile)
        perform(FilesIntent.Transfer(listOf(a.path), workspaces.path, move = false))
        assertEquals("A workspace is a folder: paste a.txt inside one", messages.last())
        perform(FilesIntent.Trash(listOf(File(app, "gone.txt").path)))
        assertEquals("gone.txt no longer exists", messages.last())
        assertTrue(stateHolder.current.explorer.trash.isEmpty())
        assertNull(undo)
    }
}
