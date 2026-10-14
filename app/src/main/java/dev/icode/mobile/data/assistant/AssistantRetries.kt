package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.koog.KoogFailures
import dev.icode.mobile.domain.AssistantRetryPolicy
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/** Provider-agnostic retry for turns — retries transient 429 / connection / 5xx with backoff. */
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
                step(AssistantRetryPolicy.stepText(kind, attempt + 1, wait))
                try {
                    delay(wait)
                } catch (c: CancellationException) {
                    throw c
                }
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
                step(AssistantRetryPolicy.stepText(kind, attempt + 1, wait))
                try {
                    delay(wait)
                } catch (c: CancellationException) {
                    throw c
                }
                attempt++
            }
        }
    }
}
