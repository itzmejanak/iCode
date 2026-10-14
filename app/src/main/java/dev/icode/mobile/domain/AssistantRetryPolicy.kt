package dev.icode.mobile.domain

/**
 * Retries for transient provider / connection failures — provider-agnostic.
 *
 * Architecture: KoogFailures classifies (429, timeouts, UnknownHost, etc.),
 * this pure policy decides delays and user-facing step text. No provider branches.
 */
enum class AssistantRetryKind { RateLimited, Connection, Server, Overloaded }

object AssistantRetryPolicy {
    /** Legacy Server kind removed; use Overloaded (5-10s ×10) or RateLimited/Connection (30s×5). */
    const val MAX_RETRIES = 5
    const val MAX_ATTEMPTS_OVERLOADED = 10
    private const val FIXED_DELAY_MILLIS = 30_000L
    private const val OVERLOADED_MIN_MILLIS = 5_000L
    private const val OVERLOADED_MAX_MILLIS = 10_000L
    private const val MAX_RETRY_AFTER_MILLIS = 180_000L

    fun maxRetries(kind: AssistantRetryKind): Int = if (kind == AssistantRetryKind.Overloaded) MAX_ATTEMPTS_OVERLOADED else MAX_RETRIES

    fun delayMillis(kind: AssistantRetryKind, attempt: Int, retryAfterMillis: Long?): Long {
        retryAfterMillis?.let {
            val cap = if (kind == AssistantRetryKind.Overloaded) OVERLOADED_MAX_MILLIS else MAX_RETRY_AFTER_MILLIS
            return it.coerceIn(1_000L, cap)
        }
        // Legacy Server kind (500/502/504 not overloaded) keeps same 5–10s window as Overloaded — short.
        return when (kind) {
            AssistantRetryKind.Overloaded, AssistantRetryKind.Server -> OVERLOADED_MIN_MILLIS + (Math.random() * (OVERLOADED_MAX_MILLIS - OVERLOADED_MIN_MILLIS)).toLong()
            else -> FIXED_DELAY_MILLIS
        }
    }

    /** Back-compat: fixed 30s (used only where kind unknown). */
    fun delayMillis(attempt: Int, retryAfterMillis: Long?): Long = delayMillis(AssistantRetryKind.RateLimited, attempt, retryAfterMillis)

    fun stepText(kind: AssistantRetryKind, attempt: Int, delayMillis: Long, remainingSec: Long? = null): String {
        val cap = maxRetries(kind)
        val seconds = remainingSec ?: (delayMillis / 1000).coerceAtLeast(1)
        val label = when (kind) {
            AssistantRetryKind.RateLimited -> "Rate limited"
            AssistantRetryKind.Connection -> "Connection lost"
            // A 500–504 that does not say it is overloaded (KoogFailures.retryKind).
            AssistantRetryKind.Server -> "Server error"
            AssistantRetryKind.Overloaded -> "Server overloaded — retrying"
        }
        return if (kind == AssistantRetryKind.Overloaded) "$label in ${seconds}s ($attempt/$cap)…"
        else "$label — retrying in ${seconds}s ($attempt/$cap)…"
    }

    fun stepTextWithCountdown(kind: AssistantRetryKind, attempt: Int, totalDelayMillis: Long, remainingMillis: Long): String =
        stepText(kind, attempt, totalDelayMillis, (remainingMillis / 1000).coerceAtLeast(1))
}
