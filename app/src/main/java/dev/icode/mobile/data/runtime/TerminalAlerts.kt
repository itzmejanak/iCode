package dev.icode.mobile.data.runtime

import android.app.Notification
import android.app.PendingIntent
import dev.icode.mobile.data.notify.IcodeChannel
import dev.icode.mobile.data.notify.IcodeNotifications
import dev.icode.mobile.data.notify.NotificationKey
import dev.icode.mobile.domain.TerminalSettingRules

/** A command that held a session's foreground and has let go of it. */
internal data class FinishedCommand(val program: String, val durationMillis: Long) {
    /** It ran long enough that its end is worth telling: as a notice ([LongCommandEnded]), and on the dock. */
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

/**
 * Tells the user a program rang the bell in a session they are not looking at (as Claude Code does,
 * set to `terminal_bell`, when it finishes or waits for input). A channel of its own, apart from the
 * silent session rows, so that it can sound; one alert per session, replaced by the next. A long
 * command's end is a notice ([TerminalCoordinator]), posted to Finished while iCode is out of view
 * (docs/notices-redesign-plan.md D7).
 */
internal class TerminalAlerts(private val shade: IcodeNotifications) {
    /** "claude needs your attention — bash · superpowers". A program ringing again before the user comes back updates, silently. */
    fun bell(sessionId: String, program: String, where: String, open: PendingIntent) {
        val alert = shade.builder(IcodeChannel.ProgramAsks)
            .setContentTitle("$program needs your attention")
            .setContentText(where)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        shade.post(NotificationKey.ProgramAsks, alert, tag = sessionId)
    }

    /** The user is looking at the session, or it has ended: its alert has nothing left to say. */
    fun clear(sessionId: String) = shade.cancel(NotificationKey.ProgramAsks, sessionId)
}
