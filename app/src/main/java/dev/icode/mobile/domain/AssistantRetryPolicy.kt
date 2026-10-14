package dev.icode.mobile.domain

/**
 * Retries for transient provider / connection failures — provider-agnostic.
 *
 * Architecture: KoogFailures classifies (429, timeouts, UnknownHost, etc.),
 * this pure policy decides delays and user-facing step text. No provider branches.
 */
enum class AssistantRetryKind { RateLimited, Connection, Server }

object AssistantRetryPolicy {
    const val MAX_RETRIES = 5
    private const val FIXED_DELAY_MILLIS = 30_000L
    private const val MAX_RETRY_AFTER_MILLIS = 180_000L

    /** Fixed 30s × 5 — simple and best per design. Respects Retry-After (1–180s) when present. */
    fun delayMillis(attempt: Int, retryAfterMillis: Long?): Long {
        retryAfterMillis?.let { return it.coerceIn(1_000L, MAX_RETRY_AFTER_MILLIS) }
        return FIXED_DELAY_MILLIS
    }

    fun stepText(kind: AssistantRetryKind, attempt: Int, delayMillis: Long, remainingSec: Long? = null): String {
        val seconds = remainingSec ?: (delayMillis / 1000).coerceAtLeast(1)
        val label = when (kind) {
            AssistantRetryKind.RateLimited -> "Rate limited"
            AssistantRetryKind.Connection -> "Connection lost"
            AssistantRetryKind.Server -> "Server busy"
        }
        return "$label — retrying in ${seconds}s ($attempt/$MAX_RETRIES)…"
    }

    fun stepTextWithCountdown(kind: AssistantRetryKind, attempt: Int, totalDelayMillis: Long, remainingMillis: Long): String =
        stepText(kind, attempt, totalDelayMillis, (remainingMillis / 1000).coerceAtLeast(1))
}
