package dev.icode.mobile.domain

import java.util.concurrent.TimeUnit

/**
 * How long ago something happened, in the largest whole unit: "just now", "5m ago", "3h ago",
 * "2d ago", "3w ago", "2mo ago", "1y ago" — a month taken as 30 days, a year as 365.
 */
object RelativeTime {
    fun since(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val elapsed = (nowMillis - epochMillis).coerceAtLeast(0)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        val days = TimeUnit.MILLISECONDS.toDays(elapsed)
        return when {
            minutes < 1 -> "just now"
            hours < 1 -> "${minutes}m ago"
            days < 1 -> "${hours}h ago"
            days < WEEK -> "${days}d ago"
            days < MONTH -> "${days / WEEK}w ago"
            days < YEAR -> "${days / MONTH}mo ago"
            else -> "${days / YEAR}y ago"
        }
    }

    private const val WEEK = 7L
    private const val MONTH = 30L
    private const val YEAR = 365L
}
