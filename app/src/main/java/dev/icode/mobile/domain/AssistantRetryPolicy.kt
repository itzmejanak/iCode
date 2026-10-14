package dev.icode.mobile.domain

/**
 * Retries for transient provider / connection failures — provider-agnostic.
 *
 * Architecture: KoogFailures classifies (429, timeouts, UnknownHost, etc.),
 * this pure policy decides delays and user-facing step text. No provider branches.
 */
enum class AssistantRetryKind { RateLimited, Connection, Server }

object AssistantRetryPolicy {
    const val MAX_RETRIES = 3
    private const val MAX_RETRY_AFTER_MILLIS = 60_000L

    fun delayMillis(attempt: Int, retryAfterMillis: Long?): Long {
        retryAfterMillis?.let { return it.coerceIn(1_000L, MAX_RETRY_AFTER_MILLIS) }
        val base = 2_000L * (1 shl attempt) // 2s, 4s, 8s
        val jitter = (base * 0.2 * (Math.random() * 2 - 1)).toLong()
        return (base + jitter).coerceAtLeast(1_000L)
    }

    fun stepText(kind: AssistantRetryKind, attempt: Int, delayMillis: Long): String {
        val seconds = (delayMillis / 1000).coerceAtLeast(1)
        return when (kind) {
            AssistantRetryKind.RateLimited -> "Rate limited — retrying in ${seconds}s ($attempt/$MAX_RETRIES)…"
            AssistantRetryKind.Connection -> "Connection lost — retrying in ${seconds}s ($attempt/$MAX_RETRIES)…"
            AssistantRetryKind.Server -> "Server busy — retrying in ${seconds}s ($attempt/$MAX_RETRIES)…"
        }
    }
}
