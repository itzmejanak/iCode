package dev.icode.mobile.data.files

import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.ConflictAnswer
import dev.icode.mobile.domain.files.FileConflict
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred

/**
 * The question an operation waits on when a name is already there (docs/explorer-gaps-plan.md §3.6):
 * shown in the snapshot, the oldest first, and answered by id — never behind the mutation lock, which
 * the operation does not hold while it waits. Its task stays, cancellable, saying so.
 */
internal class FileQuestions(private val stateHolder: ICodeStateHolder) {
    /** An answer, and whether it answers the rest alike. */
    class Answered(val answer: ConflictAnswer, val forAll: Boolean)

    private class Waiting(val conflict: FileConflict, val reply: CompletableDeferred<Answered?>)

    private val ids = AtomicLong()
    private val waiting = LinkedHashMap<Long, Waiting>()

    /** [conflict] asked, its id given here; the answer, or null when the operation is to stop. */
    suspend fun ask(conflict: (id: Long) -> FileConflict, progress: TaskRunner.Progress): Answered? {
        val question = Waiting(conflict(ids.incrementAndGet()), CompletableDeferred())
        progress.stage("Waiting for your answer")
        synchronized(waiting) { waiting[question.conflict.id] = question }
        publish()
        try {
            return question.reply.await()
        } finally {
            synchronized(waiting) { waiting.remove(question.conflict.id) }
            publish()
        }
    }

    fun answer(intent: FilesIntent.Answer) {
        val question = synchronized(waiting) { waiting[intent.id] } ?: return
        question.reply.complete(intent.answer?.let { Answered(it, intent.forAll) })
    }

    private fun publish() {
        val first = synchronized(waiting) { waiting.values.firstOrNull()?.conflict }
        stateHolder.update { it.copy(explorer = it.explorer.copy(question = first)) }
    }
}
