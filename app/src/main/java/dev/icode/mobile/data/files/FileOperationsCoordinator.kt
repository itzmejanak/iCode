package dev.icode.mobile.data.files

import android.content.Context
import dev.icode.mobile.data.git.GitCheckoutOperations
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceUsage
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.files.BatchRename
import dev.icode.mobile.domain.files.FileChange
import dev.icode.mobile.domain.files.FileClipboard
import dev.icode.mobile.domain.files.FileSelection
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.FileWords
import dev.icode.mobile.domain.files.HeldItem
import dev.icode.mobile.domain.files.RenameRule
import dev.icode.mobile.domain.files.RenameSource
import dev.icode.mobile.domain.files.TrashedItem
import dev.icode.mobile.domain.files.paths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * The explorers' operations on their files (docs/explorer-gaps-plan.md §3.3–§3.8): the clipboard,
 * paste, Move to, Copy to, Duplicate, rename of many, Delete to Trash, Trash's own actions, and undo
 * and redo. Each is checked whole first under the mutation lock — nothing starts that one of its
 * items would refuse ([FileRefusals]) — then runs as a task, off the lock, whose steps are kept to
 * undo as one change however it ends. The question a name already there asks is answered without
 * the lock.
 */
internal class FileOperationsCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val tasks: TaskRunner,
    private val usage: WorkspaceUsage,
    private val follower: FileFollower,
    private val history: FileHistory,
    private val operator: FileOperator,
    private val questions: FileQuestions,
    private val refusals: FileRefusals,
) {
    val places = operator.places
    private val trash = operator.trash

    /** An undo or a redo is running: the next waits for it. */
    private var reversing = false

    /** At start: what has been in Trash 30 days goes, and what was left there without its record; then Trash is read. */
    fun start() {
        scope.launch {
            runCatching { trash.purge(orphans = true) }
            publishTrash()
        }
    }

    fun handle(intent: FilesIntent) {
        when (intent) {
            is FilesIntent.Answer -> questions.answer(intent)
            FilesIntent.ReadTrash -> scope.launch {
                runCatching { trash.purge(orphans = false) }
                publishTrash()
            }
            else -> scope.launch { stateHolder.mutations.withLock { act(intent) } }
        }
    }

    private suspend fun act(intent: FilesIntent) {
        when (intent) {
            is FilesIntent.Cut -> hold(intent.locations, cut = true)
            is FilesIntent.Copy -> hold(intent.locations, cut = false)
            FilesIntent.ClearClipboard -> clipboard(null)
            is FilesIntent.Paste -> stateHolder.current.explorer.clipboard?.let { transfer(it.items, intent.folder, it.cut, pasted = true) }
            is FilesIntent.Transfer -> transfer(FileSelection.covering(intent.locations).map(::held), intent.folder, intent.move, pasted = false)
            is FilesIntent.Duplicate -> duplicate(FileSelection.covering(intent.locations))
            is FilesIntent.RenameMany -> renameMany(intent.locations, intent.rule)
            is FilesIntent.Trash -> trash(FileSelection.covering(intent.locations))
            is FilesIntent.Restore -> restore(intent.ids.mapNotNull(trash::item))
            is FilesIntent.DeleteForGood -> deleteForGood(intent.ids.mapNotNull(trash::item))
            FilesIntent.EmptyTrash -> deleteForGood(trash.list())
            FilesIntent.Undo -> reverse(undo = true)
            FilesIntent.Redo -> reverse(undo = false)
            FilesIntent.ReadTrash, is FilesIntent.Answer -> Unit
        }
    }

    /** Held for Paste; nothing moves yet. */
    private suspend fun hold(locations: List<String>, cut: Boolean) {
        val items = FileSelection.covering(locations).map(::held)
        items.firstOrNull { !fileExists(it.location) }?.let { return said("${it.name} no longer exists") }
        clipboard(FileClipboard(items, cut).takeIf { items.isNotEmpty() })
    }

    /** [items] into the folder [into]; a cut [pasted] leaves the clipboard once it moved anything. */
    private suspend fun transfer(items: List<HeldItem>, into: String, move: Boolean, pasted: Boolean) {
        refusals.transfer(items, into, move)?.let { return said(it) }
        val names = items.map { it.name }
        val locations = items.map { it.location }
        val crossing = locations.any { operator.transfers.crosses(it, into) }
        run(
            title = if (move) "Moving…" else "Copying…",
            subject = FileWords.subject(names),
            paths = locations + into,
            failure = "Unable to ${if (move) "move" else "copy"}",
            counted = locations.takeIf { !move || crossing },
            keep = { steps ->
                history.record((if (move) FileWords.moved(names, refusals.nameOf(into)) else FileWords.copied(names, refusals.nameOf(into))).copy(steps = steps))
                if (pasted && move && steps.isNotEmpty()) clipboard(null)
            },
        ) { carry(locations, into, move) }
    }

    private suspend fun duplicate(locations: List<String>) {
        refusals.copies(locations)?.let { return said(it) }
        val names = locations.map { File(it).name }
        run("Duplicating…", FileWords.subject(names), locations, "Unable to duplicate", counted = locations, keep = kept(FileWords.duplicated(names))) {
            duplicate(locations)
        }
    }

    /** One rule renaming [locations], all in one folder, checked again here against what the folder holds now. */
    private suspend fun renameMany(locations: List<String>, rule: RenameRule) {
        val folder = locations.map { File(it).parent }.distinct().singleOrNull() ?: return said("Rename items of one folder at a time")
        refusals.changes(locations)?.let { return said(it) }
        val sources = locations.map { RenameSource(it, File(it).name, Files.isDirectory(File(it).toPath(), LinkOption.NOFOLLOW_LINKS)) }
        val taken = File(folder).list().orEmpty().toSet() - sources.map { it.name }.toSet()
        val previews = BatchRename.preview(sources, rule, taken, ignoreCase = places.storage.contains(folder))
        val problem = previews.firstNotNullOfOrNull { it.problem } ?: refusals.workspaceNames(folder, previews.filter { it.changes }.map { it.to })
        if (problem != null || !BatchRename.ready(previews)) return said(problem ?: "Nothing would change")
        val renames = previews.filter { it.changes }.map { it.location to File(folder, it.to).path }
        val words = renames.singleOrNull()?.let { (from, to) -> FileWords.renamed(File(from).name, File(to).name) } ?: FileWords.renamedMany(renames.size)
        run("Renaming…", FileWords.subject(renames.map { File(it.first).name }), locations, "Unable to rename", keep = kept(words)) { rename(renames) }
    }

    private suspend fun trash(locations: List<String>) {
        refusals.changes(locations)?.let { return said(it) }
        val names = locations.map { File(it).name }
        run("Moving to Trash…", FileWords.subject(names), locations, "Unable to move to Trash", keep = kept(FileWords.trashed(names))) { trash(locations) }
    }

    private suspend fun restore(items: List<TrashedItem>) {
        if (items.isEmpty()) return said("It is no longer in Trash")
        refusals.restore(items)?.let { return said(it) }
        val names = items.map { it.name }
        run("Restoring…", FileWords.subject(names), items.map { it.from }, "Unable to restore", keep = kept(FileWords.restored(names))) { restore(items) }
    }

    /** Asked first: gone for good, without Cancel — stopped midway, it would leave half a folder — and not kept to undo. */
    private fun deleteForGood(items: List<TrashedItem>) {
        if (items.isEmpty()) return
        val held = items.map(trash::locationOf)
        tasks.start(
            title = "Deleting…",
            subject = FileWords.subject(items.map { it.name }),
            paths = held,
            cancellable = false,
            failure = "Unable to delete",
            work = { progress ->
                progress.counted(held.sumOf { countEntries(File(it).toPath()) })
                items.forEach { trash.delete(it, progress::item) }
            },
            finished = { publishTrash() },
        )
    }

    /** The last change undone, or the last undone done again — once nothing it touches changed since. */
    private suspend fun reverse(undo: Boolean) {
        if (reversing) return said("Wait for the ${if (undo) "undo" else "redo"} running to finish")
        val change = (if (undo) history.nextUndo else history.nextRedo) ?: return
        val reversals = change.reversals
        refusals.reversal(change)?.let { return said(it) }
        reversing = true
        run(
            title = if (undo) "Undoing…" else "Redoing…",
            subject = if (undo) change.said else change.undoneSaid,
            paths = reversals.flatMap { it.paths },
            failure = "Unable to ${if (undo) "undo" else "redo"}",
            keep = { steps ->
                reversing = false
                if (undo) history.undone(change, steps) else history.redone(change, steps)
            },
        ) { reverse(reversals) }
    }

    /**
     * [work] as a task over [paths], [counted] first when it copies. Once it ends — done, stopped or
     * failed — what it did is [keep]t, what it noted is said, Trash read again when it changed, and
     * then every explorer.
     */
    private fun run(
        title: String,
        subject: String,
        paths: List<String>,
        failure: String,
        counted: List<String>? = null,
        keep: suspend (List<FileStep>) -> Unit,
        work: suspend FileBatch.() -> Unit,
    ) {
        var batch: FileBatch? = null
        tasks.start(
            title = title,
            subject = subject,
            paths = paths,
            cancellable = true,
            failure = failure,
            work = { progress ->
                counted?.let { locations -> progress.counted(locations.sumOf { places.of(it).count(it) }) }
                FileBatch(operator, questions, progress, usage::edited).also { batch = it }.work()
            },
            finished = {
                val steps = batch?.steps?.toList().orEmpty()
                keep(steps)
                batch?.notes?.distinct()?.forEach { said(it) }
                if (steps.any { it is FileStep.Trashed || it is FileStep.Restored }) publishTrash()
                follower.refresh(steps.flatMap { it.paths } + paths)
            },
        )
    }

    private fun kept(words: FileChange): suspend (List<FileStep>) -> Unit = { steps -> history.record(words.copy(steps = steps)) }

    private fun clipboard(clipboard: FileClipboard?) = stateHolder.update { it.copy(explorer = it.explorer.copy(clipboard = clipboard)) }

    private suspend fun publishTrash() {
        val items = runCatching { trash.list() }.getOrDefault(emptyList())
        stateHolder.update { it.copy(explorer = it.explorer.copy(trash = items)) }
    }

    private fun held(location: String) =
        HeldItem(location, File(location).name, Files.isDirectory(File(location).toPath(), LinkOption.NOFOLLOW_LINKS), follower.isWorkspace(location))

    private suspend fun said(words: String) = stateHolder.notify(NoticeTone.HeadsUp, words)

    companion object {
        fun create(
            context: Context,
            stateHolder: ICodeStateHolder,
            scope: CoroutineScope,
            tasks: TaskRunner,
            layout: RuntimeLayout,
            storage: PhoneStorage,
            git: GitCheckoutOperations,
            usage: WorkspaceUsage,
            follower: FileFollower,
            history: FileHistory,
        ): FileOperationsCoordinator {
            val places = FilePlaces(layout.workspaces.root, storage)
            val transfers = StorageTransfers(stateHolder, layout.workspaces, git, storage, layout::reserveSpace, layout::allocatableBytes)
            // Shared's in the folder Android gives iCode on phone storage, and removes with it.
            val trash = FileTrash(File(context.filesDir, "trash"), File(storage.root, "Android/media/${context.packageName}/Trash"), storage)
            val operator = FileOperator(stateHolder, places, transfers, trash, follower)
            return FileOperationsCoordinator(
                stateHolder, scope, tasks, usage, follower, history, operator, FileQuestions(stateHolder),
                FileRefusals(places, trash, tasks, usage, follower, layout.workspaces.root),
            )
        }
    }
}
