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

    /** How long something has lasted: "12s", "4m", "1h 5m", "2d 3h" — two units at most. */
    fun span(elapsedMillis: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis.coerceAtLeast(0))
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "${seconds}s"
            hours < 1 -> "${minutes}m"
            days < 1 -> if (minutes % 60 == 0L) "${hours}h" else "${hours}h ${minutes % 60}m"
            else -> if (hours % 24 == 0L) "${days}d" else "${days}d ${hours % 24}h"
        }
    }

    private const val WEEK = 7L
    private const val MONTH = 30L
    private const val YEAR = 365L
}
