package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.koog.KoogFailures
import dev.icode.mobile.domain.AssistantRetryPolicy
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/** Provider-agnostic retry — 5× with 15s→30s→45s→90s→180s countdown, per design. */
internal object AssistantRetries {
    suspend fun runWithRetry(step: (String) -> Unit, block: suspend (Int) -> Unit) {
        var attempt = 0
        while (attempt <= AssistantRetryPolicy.MAX_RETRIES) {
            try {
                block(attempt)
                return
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                val kind = KoogFailures.retryKind((failure as? AssistantTurnException)?.cause ?: failure)
                    ?: KoogFailures.retryKind(failure)
                if (kind == null || attempt == AssistantRetryPolicy.MAX_RETRIES) throw failure
                val ms = KoogFailures.retryAfterMillis((failure as? AssistantTurnException)?.cause ?: failure)
                    ?: KoogFailures.retryAfterMillis(failure)
                val wait = AssistantRetryPolicy.delayMillis(attempt, ms)
                countdown(step, kind, attempt + 1, wait)
                attempt++
            }
        }
    }

    suspend fun retryable(initialStep: String, step: (String) -> Unit, block: suspend () -> Unit) {
        var attempt = 0
        while (attempt <= AssistantRetryPolicy.MAX_RETRIES) {
            try {
                step(initialStep)
                block()
                return
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                val kind = KoogFailures.retryKind((failure as? AssistantTurnException)?.cause ?: failure)
                    ?: KoogFailures.retryKind(failure)
                if (kind == null || attempt == AssistantRetryPolicy.MAX_RETRIES) throw failure
                val ms = KoogFailures.retryAfterMillis((failure as? AssistantTurnException)?.cause ?: failure)
                    ?: KoogFailures.retryAfterMillis(failure)
                val wait = AssistantRetryPolicy.delayMillis(attempt, ms)
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
