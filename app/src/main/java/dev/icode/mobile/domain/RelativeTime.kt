package dev.icode.mobile.domain

import java.util.concurrent.TimeUnit

/** How long ago something happened, in the largest whole unit: "just now", "5m ago", "3h ago", "2d ago". */
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
            else -> "${days}d ago"
        }
    }
}
