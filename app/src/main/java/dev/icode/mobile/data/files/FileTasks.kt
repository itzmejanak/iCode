package dev.icode.mobile.data.files

import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceUsage
import dev.icode.mobile.domain.files.FileChange
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.FileTask
import dev.icode.mobile.domain.files.FileWords
import dev.icode.mobile.domain.notices.FileNotices
import dev.icode.mobile.domain.notices.NoticePlaces
import dev.icode.mobile.domain.files.TrashedItem
import dev.icode.mobile.domain.files.paths
import java.io.File

/**
 * The explorers' operations run as tasks (docs/workspaces-and-files.md), off the
 * mutation lock, each with its [FileBatch] over the task's progress. Once one ends — done, stopped
 * or failed — what it did is kept, what it noted said, Trash read again when it changed, and then
 * every explorer.
 */
internal class FileTasks(
    private val stateHolder: ICodeStateHolder,
    private val tasks: TaskRunner,
    private val operator: FileOperator,
    private val questions: FileQuestions,
    private val usage: WorkspaceUsage,
    private val follower: FileFollower,
    private val history: FileHistory,
) {
    /**
     * [work] as a [task] over [paths], [counted] first when it copies; what it did, once it ends, to
     * [keep]. Where it is — the folder of its first path — is read as it starts, for what it
     * stopped short of, told into the folder [into] for a move or a copy.
     */
    fun run(
        task: FileTask,
        subject: String,
        paths: List<String>,
        into: String? = null,
        counted: List<String>? = null,
        keep: suspend (List<FileStep>) -> Unit,
        work: suspend FileBatch.() -> Unit,
    ) {
        var batch: FileBatch? = null
        val place = placeOf(paths.firstOrNull())
        tasks.start(
            title = task.title,
            subject = subject,
            paths = paths,
            cancellable = true,
            failed = FileNotices.stopped(task, subject, into, place),
            work = { progress ->
                counted?.let { locations -> progress.counted(locations.sumOf { operator.places.of(it).count(it) }) }
                FileBatch(operator, questions, progress, usage::edited).also { batch = it }.work()
            },
            finished = {
                val steps = batch?.steps?.toList().orEmpty()
                keep(steps)
                batch?.notes?.distinct()?.forEach { stateHolder.tell(FileNotices.headsUp(it, place)) }
                if (steps.any { it is FileStep.Trashed || it is FileStep.Restored }) publishTrash()
                follower.refresh(steps.flatMap { it.paths } + paths)
            },
        )
    }

    /** What an operation did, kept to undo as [words] say it. */
    fun kept(words: FileChange): suspend (List<FileStep>) -> Unit = { steps -> history.record(words.copy(steps = steps)) }

    /**
     * Asked first: Trash's [items] gone for good, without Cancel — stopped midway, it would leave half
     * a folder — and not kept to undo, so told: where they were.
     */
    fun deleteForGood(items: List<TrashedItem>) {
        if (items.isEmpty()) return
        val held = items.map { it.heldAt }
        val names = items.map { it.name }
        val subject = FileWords.subject(names)
        val place = placeOf(items.first().from)
        tasks.start(
            title = FileTask.DeleteForGood.title,
            subject = subject,
            paths = held,
            cancellable = false,
            failed = FileNotices.stopped(FileTask.DeleteForGood, subject, into = null, place),
            work = { progress ->
                progress.counted(held.sumOf { countEntries(File(it).toPath()) })
                items.forEach { operator.trash.delete(it, progress::item) }
            },
            finished = { outcome ->
                if (outcome == null) stateHolder.tell(FileNotices.deletedForGood(names, place))
                publishTrash()
            },
        )
    }

    /** What Trash holds now, into the snapshot. */
    suspend fun publishTrash() {
        val items = runCatching { operator.trash.list() }.getOrDefault(emptyList())
        stateHolder.update { it.copy(explorer = it.explorer.copy(trash = items)) }
    }

    /** What to do first, or what was refused, in the explorer's [words]: where [near] is, when it is about something. */
    suspend fun said(words: String, near: String? = null) = stateHolder.tell(FileNotices.headsUp(words, placeOf(near)))

    private fun placeOf(location: String?) = location?.let { NoticePlaces.of(stateHolder.current, it) }
}
