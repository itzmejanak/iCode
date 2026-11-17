package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.koog.KoogFailures
import dev.icode.mobile.domain.AssistantRetryPolicy
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/** Provider-agnostic retry — per-kind: Server 5-10s×10, others 30s×5, with countdown. */
internal object AssistantRetries {
    suspend fun <T> runWithRetry(step: (String) -> Unit, block: suspend (Int) -> T): T {
        var attempt = 0
        var lastKind: dev.icode.mobile.domain.AssistantRetryKind? = null
        while (true) {
            try {
                return block(attempt)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                val kind = KoogFailures.retryKind((failure as? AssistantTurnException)?.cause ?: failure)
                    ?: KoogFailures.retryKind(failure)
                lastKind = kind ?: lastKind
                val cap = lastKind?.let { AssistantRetryPolicy.maxRetries(it) } ?: AssistantRetryPolicy.MAX_RETRIES
                if (kind == null || attempt >= cap) throw failure
                val ms = KoogFailures.retryAfterMillis((failure as? AssistantTurnException)?.cause ?: failure)
                    ?: KoogFailures.retryAfterMillis(failure)
                val wait = AssistantRetryPolicy.delayMillis(kind, attempt, ms)
                countdown(step, kind, attempt + 1, wait)
                attempt++
            }
        }
    }

    suspend fun retryable(initialStep: String, step: (String) -> Unit, block: suspend () -> Unit) {
        var attempt = 0
        var lastKind: dev.icode.mobile.domain.AssistantRetryKind? = null
        while (true) {
            try {
                step(initialStep)
                block()
                return
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                val kind = KoogFailures.retryKind((failure as? AssistantTurnException)?.cause ?: failure)
                    ?: KoogFailures.retryKind(failure)
                lastKind = kind ?: lastKind
                val cap = lastKind?.let { AssistantRetryPolicy.maxRetries(it) } ?: AssistantRetryPolicy.MAX_RETRIES
                if (kind == null || attempt >= cap) throw failure
                val ms = KoogFailures.retryAfterMillis((failure as? AssistantTurnException)?.cause ?: failure)
                    ?: KoogFailures.retryAfterMillis(failure)
                val wait = AssistantRetryPolicy.delayMillis(kind, attempt, ms)
                countdown(step, kind, attempt + 1, wait)
                attempt++
            }
        }
    }

    private suspend fun countdown(step: (String) -> Unit, kind: dev.icode.mobile.domain.AssistantRetryKind, attempt: Int, totalMillis: Long) {
        var remaining = totalMillis
        while (remaining > 0) {
            step(AssistantRetryPolicy.stepTextWithCountdown(kind, attempt, totalMillis, remaining))
            val tick = minOf(1_000L, remaining)
            try {
                delay(tick)
            } catch (c: CancellationException) {
                throw c
            }
            remaining -= tick
        }
    }
}
