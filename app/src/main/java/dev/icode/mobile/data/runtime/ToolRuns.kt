package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.AptProgress
import dev.icode.mobile.domain.RuntimeToolWork
import java.util.ArrayDeque
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The runtime tools' run in hand, one at a time ([RuntimePackageInstaller]): what the page, a button
 * waiting on it and its notification see ([work]), and how Cancel reaches it — only while the step
 * in hand leaves nothing half done.
 */
internal class ToolRuns {
    private val mutableWork = MutableStateFlow<RuntimeToolWork?>(null)
    /** The run in hand, or the page's last failure until it is dismissed. */
    val work: StateFlow<RuntimeToolWork?> = mutableWork.asStateFlow()

    /** What is in hand, while stopping it leaves nothing half done. */
    @Volatile private var stoppable: Job? = null
    @Volatile var stopRequested = false
        private set

    fun begin(work: RuntimeToolWork): ToolRun {
        stopRequested = false
        mutableWork.value = work
        return ToolRun(work)
    }

    /** [run] has ended: gone, or kept with its [failure] for the page to show. */
    fun end(run: ToolRun, failure: String?) {
        stoppable = null
        mutableWork.value = failure?.let { run.work.copy(failure = it) }
    }

    /** Stops the run in hand while its step allows it. */
    fun cancel() {
        if (mutableWork.value?.cancellable != true) return
        stopRequested = true
        stoppable?.cancel()
    }

    /** The page has shown a kept failure: it goes. */
    fun dismiss() {
        if (mutableWork.value?.failure != null) mutableWork.value = null
    }

    /** One published run: its progress and the last lines apt or a setup printed, as they come. */
    inner class ToolRun(var work: RuntimeToolWork) {
        val output = ArrayDeque<String>()
        val names: String get() = work.tools.joinToString { it.name }

        var progress: AptProgress
            get() = work.progress
            set(value) = publish(work.copy(progress = value))

        fun publish(next: RuntimeToolWork) {
            work = next
            mutableWork.value = next
        }

        /** A line printed: kept for Details, with what it says of the progress. */
        fun printed(line: String, progressed: (RuntimeToolWork) -> RuntimeToolWork = { it }) {
            if (line.isBlank()) return
            if (output.size == MAX_OUTPUT_LINES) output.removeFirst()
            output.addLast(line.trim())
            publish(progressed(work).copy(log = output.toList()))
        }

        /** Runs [body]; Cancel stops it when [stoppable] and the step in hand says it may. */
        suspend fun <T> stoppably(stoppable: Boolean, body: suspend () -> T): T = coroutineScope {
            val running = async { body() }
            this@ToolRuns.stoppable = running.takeIf { stoppable }
            if (stoppable && stopRequested) running.cancel()
            running.await()
        }
    }

    private companion object {
        const val MAX_OUTPUT_LINES = 20
    }
}
