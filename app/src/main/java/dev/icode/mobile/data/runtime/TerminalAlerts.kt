package dev.icode.mobile.data.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import dev.icode.mobile.R
import dev.icode.mobile.domain.TerminalSettingRules

/** A command that held a session's foreground and has let go of it. */
internal data class FinishedCommand(val program: String, val durationMillis: Long) {
    /** It ran long enough that its end is worth telling: by the finish alert, and on the dock. */
    val long: Boolean get() = durationMillis >= TerminalSettingRules.FINISH_ALERT_MILLIS
}

/**
 * What each session has had in its foreground, sample by sample. A command's
 * end is when the shell has the terminal again, or another program takes it;
 * the exit status is not observable from here, so nothing claims success.
 * Samples are seconds apart, so a duration is right to one sampling interval.
 */
internal class CommandRuns {
    private class Run(val program: String, val since: Long)

    private val runs = mutableMapOf<String, Run>()

    /** Records [foreground] for [sessionId] at [now]; returns the command that just finished, if any. */
    fun sample(sessionId: String, foreground: TerminalForeground, now: Long): FinishedCommand? = when (foreground) {
        is TerminalForeground.Running -> {
            val current = runs[sessionId]
            if (current?.program == foreground.program) {
                null
            } else {
                runs[sessionId] = Run(foreground.program, now)
                current?.let { FinishedCommand(it.program, now - it.since) }
            }
        }
        TerminalForeground.Idle -> runs.remove(sessionId)?.let { FinishedCommand(it.program, now - it.since) }
        TerminalForeground.Unknown -> null
    }

    fun forget(sessionId: String) {
        runs.remove(sessionId)
    }
}

/** "45 s", "2 min", "1 h 5 min". */
internal fun spokenDuration(millis: Long): String {
    val seconds = millis / 1_000
    return when {
        seconds < 60 -> "$seconds s"
        seconds < 3_600 -> "${seconds / 60} min"
        else -> listOfNotNull("${seconds / 3_600} h", (seconds % 3_600 / 60).takeIf { it > 0 }?.let { "$it min" })
            .joinToString(" ")
    }
}

/**
 * Tells the user about a session they are not looking at: its program rang
 * the bell (as Claude Code does, set to `terminal_bell`, when it finishes or
 * waits for input), or a command that ran a while has finished. A channel of
 * its own, apart from the silent session rows, so that it can sound; one alert
 * per session, replaced by the next.
 */
internal class TerminalAlerts(private val context: Context, private val notifications: NotificationManager) {

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Terminal alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A program asks for you, or a long command finishes, while you are elsewhere"
            },
        )
    }

    /** "claude needs your attention — bash · superpowers". */
    fun bell(sessionId: String, program: String, where: String, open: PendingIntent) {
        // A program ringing again before the user comes back updates, silently.
        post(sessionId, "$program needs your attention", where, open, alertAgain = false)
    }

    /** "npm finished — bash · superpowers · after 2 min". */
    fun finished(sessionId: String, command: FinishedCommand, where: String, open: PendingIntent) {
        post(sessionId, "${command.program} finished", "$where · after ${spokenDuration(command.durationMillis)}", open, alertAgain = true)
    }

    /** The user is looking at the session, or it has ended: its alert has nothing left to say. */
    fun clear(sessionId: String) {
        notifications.cancel(alertId(sessionId))
    }

    private fun post(sessionId: String, title: String, text: String, open: PendingIntent, alertAgain: Boolean) {
        val alert = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alertAgain)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        notifications.notify(alertId(sessionId), alert)
    }

    private companion object {
        const val CHANNEL_ID = "icode.terminal-alerts"
        const val ALERT_BASE = 6000

        /** Stable per session: "session-3" is always alert 6003. */
        fun alertId(sessionId: String): Int =
            ALERT_BASE + (sessionId.substringAfterLast('-').toIntOrNull() ?: (sessionId.hashCode() and 0xFFF))
    }
}
