package dev.icode.mobile.data.files

import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceUsage
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.files.FileChange
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.FileWords
import dev.icode.mobile.domain.files.TrashedItem
import dev.icode.mobile.domain.files.paths
import java.io.File

/**
 * The explorers' operations run as tasks (docs/explorer-gaps-plan.md §3.5–§3.8, §3.11), off the
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
    /** [work] as a task over [paths], [counted] first when it copies; what it did, once it ends, to [keep]. */
    fun run(
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
                counted?.let { locations -> progress.counted(locations.sumOf { operator.places.of(it).count(it) }) }
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

    /** What an operation did, kept to undo as [words] say it. */
    fun kept(words: FileChange): suspend (List<FileStep>) -> Unit = { steps -> history.record(words.copy(steps = steps)) }

    /** Asked first: Trash's [items] gone for good, without Cancel — stopped midway, it would leave half a folder — and not kept to undo. */
    fun deleteForGood(items: List<TrashedItem>) {
        if (items.isEmpty()) return
        val held = items.map(operator.trash::locationOf)
        tasks.start(
            title = "Deleting…",
            subject = FileWords.subject(items.map { it.name }),
            paths = held,
            cancellable = false,
            failure = "Unable to delete",
            work = { progress ->
                progress.counted(held.sumOf { countEntries(File(it).toPath()) })
                items.forEach { operator.trash.delete(it, progress::item) }
            },
            finished = { publishTrash() },
        )
    }

    /** What Trash holds now, into the snapshot. */
    suspend fun publishTrash() {
        val items = runCatching { operator.trash.list() }.getOrDefault(emptyList())
        stateHolder.update { it.copy(explorer = it.explorer.copy(trash = items)) }
    }

    suspend fun said(words: String) = stateHolder.notify(NoticeTone.HeadsUp, words)
}
