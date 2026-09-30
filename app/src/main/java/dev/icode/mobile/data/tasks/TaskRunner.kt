package dev.icode.mobile.data.tasks

import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.data.workspace.isAtOrUnder
import dev.icode.mobile.domain.WorkTask
import dev.icode.mobile.domain.WorkbenchIntent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Runs the user's long operations (docs/long-operations-plan.md): each on its
 * own job, outside the mutation lock, so opening, saving and Git do not wait
 * behind a large delete. A task claims the paths it works on until it ends;
 * its result is applied under the lock. Progress reaches the snapshot a few
 * times a second, however fast the work goes.
 */
internal class TaskRunner(
    private val workbench: WorkbenchStateHolder,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val ids = AtomicLong(0)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val claims = ConcurrentHashMap<Long, List<String>>()

    /** What a task's work reports as it goes. */
    inner class Progress internal constructor(private val id: Long) {
        @Volatile private var publishedAt = 0L
        @Volatile private var total: Long? = null
        @Volatile private var done = 0L

        /** Counting is over: [items] is what the work will touch. */
        fun counted(items: Long) {
            total = items
            publish(current = null, force = true)
        }

        /** [relative] was done; stops the work here if the task was cancelled. */
        fun item(relative: String) {
            if (jobs[id]?.isActive == false) throw CancellationException("Cancelled")
            done++
            publish(relative, force = false)
        }

        /** Between items, what the work is doing: "Restoring Git's executables and links". */
        fun stage(text: String) = publish(text, force = true)

        private fun publish(current: String?, force: Boolean) {
            val now = clock()
            if (!force && now - publishedAt < PUBLISH_INTERVAL_MILLIS) return
            publishedAt = now
            val reached = done
            val of = total
            update(id) { it.copy(currentItem = current ?: it.currentItem, done = reached, total = of) }
        }
    }

    /** The running task holding [location], or a folder above or below it. */
    fun claimedBy(location: String): WorkTask? {
        val id = claims.entries.firstOrNull { (_, paths) ->
            paths.any { location.isAtOrUnder(it) || it.isAtOrUnder(location) }
        }?.key ?: return null
        return workbench.current.tasks.firstOrNull { it.id == id }
    }

    /**
     * Why [location] must wait: a running task holds it, or a folder above or below it. The user ran
     * into that task, so a hidden one comes back into view to show what it is doing. Null when
     * nothing holds it.
     */
    fun waitReason(location: String): String? {
        val task = claimedBy(location) ?: return null
        if (task.hidden) update(task.id) { it.copy(hidden = false) }
        return "${task.title.removeSuffix("…")} ${task.subject} — wait for it to finish"
    }

    /**
     * Starts [work] as a task over [paths], and applies [finished] under the
     * mutation lock when it ends — with null, the cancellation, or the failure.
     * A failure stays shown, as [failure] or the error's own words, until dismissed.
     */
    fun start(
        title: String,
        subject: String,
        paths: List<String>,
        cancellable: Boolean,
        failure: String,
        work: suspend (Progress) -> Unit,
        finished: suspend (Throwable?) -> Unit,
    ): Long {
        val id = ids.incrementAndGet()
        claims[id] = paths
        var failed: Throwable? = null
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                work(Progress(id))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                failed = error
            }
        }
        // However it ends — done, failed, or cancelled before its body ever ran — it is settled.
        job.invokeOnCompletion { cause -> settle(id, failed ?: cause, failure, finished) }
        // Registered before the task can be seen, so a Cancel on its first frame reaches it.
        jobs[id] = job
        workbench.update { it.copy(tasks = it.tasks + WorkTask(id, title, subject, cancellable = cancellable)) }
        job.start()
        return id
    }

    /** Frees [id]'s paths and says how it ended; its result is applied under the mutation lock. */
    private fun settle(id: Long, outcome: Throwable?, failure: String, finished: suspend (Throwable?) -> Unit) {
        claims.remove(id)
        jobs.remove(id)
        when (outcome) {
            null, is CancellationException -> workbench.update { state -> state.copy(tasks = state.tasks.filterNot { it.id == id }) }
            else -> {
                val reason = workbench.failureText("tasks", failure, outcome)
                // Said in the dialog: a hidden task comes back to say why it stopped.
                update(id) { it.copy(failure = reason, hidden = false) }
            }
        }
        scope.launch(NonCancellable) { workbench.mutations.withLock { finished(outcome) } }
    }

    fun handle(intent: WorkbenchIntent.TaskIntent) {
        when (intent) {
            // A failure stays shown until dismissed, even when it landed before this Hide did.
            is WorkbenchIntent.HideTask -> update(intent.id) { if (it.failure != null) it else it.copy(hidden = true) }
            is WorkbenchIntent.ShowTask -> update(intent.id) { it.copy(hidden = false) }
            is WorkbenchIntent.CancelTask ->
                if (workbench.current.tasks.any { it.id == intent.id && it.cancellable }) jobs[intent.id]?.cancel()
            is WorkbenchIntent.DismissTask -> workbench.update { state ->
                state.copy(tasks = state.tasks.filterNot { it.id == intent.id && it.failure != null })
            }
        }
    }

    private fun update(id: Long, change: (WorkTask) -> WorkTask) = workbench.update { state ->
        state.copy(tasks = state.tasks.map { if (it.id == id) change(it) else it })
    }

    private companion object {
        /** A few updates a second: fast enough to move, slow enough not to flood the screen. */
        const val PUBLISH_INTERVAL_MILLIS = 150L
    }
}
