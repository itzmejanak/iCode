package dev.icode.mobile.data.runtime

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import dev.icode.mobile.R
import dev.icode.mobile.data.notify.IcodeChannel
import dev.icode.mobile.data.notify.IcodeNotifications
import dev.icode.mobile.data.notify.NotificationLink
import dev.icode.mobile.domain.TerminalSessionState

/**
 * What [RuntimeForegroundService] shows: a notification group whose parent is
 * the service's own notification, one row per running session with Exit and a
 * wake-lock toggle, and the intents behind them. Tapping a row opens that
 * session; the parent opens the selected one.
 */
internal class TerminalNotifications(private val context: Context, private val shade: IcodeNotifications) {
    private val createdAt = System.currentTimeMillis() - ORDER_PARENT * ORDER_STEP_MILLIS

    /**
     * The group's parent, and the service's foreground notification. Its
     * children are the session rows and the work's progress rows, so iCode is
     * one card; Android shows the parent as the group's header and no buttons
     * on it (measured on this XOS phone, as on stock Android), so it has none.
     * Collapsed, it says what runs — "1 session · 2 processes running".
     */
    fun parent(sessionCount: Int, runningWork: List<String>, openSessionId: String?): Notification {
        // The work by its own words — "2 processes running", "Cloning…" — not all called tasks.
        val text = (
            listOfNotNull(
                when (sessionCount) {
                    0 -> null
                    1 -> "1 session"
                    else -> "$sessionCount sessions"
                },
            ) + runningWork
            ).joinToString(" · ")
        return baseBuilder(ORDER_PARENT)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setGroupSummary(true)
            .setContentIntent(openSession(openSessionId))
            .build()
    }

    fun sessionRow(session: TerminalSessionState, index: Int, text: String, awake: Boolean): Notification =
        baseBuilder(ORDER_PARENT - 1 - index)
            .setContentTitle(session.title)
            .setContentText(text)
            .setContentIntent(openSession(session.id))
            .addAction(shade.action("Exit", serviceIntent(RuntimeForegroundService.ACTION_EXIT_SESSION, session.id)))
            .addAction(
                shade.action(
                    if (awake) "Release wakelock" else "Acquire wakelock",
                    serviceIntent(RuntimeForegroundService.ACTION_TOGGLE_WAKE_LOCK, session.id),
                ),
            )
            .build()

    /** Opens iCode on [sessionId]'s terminal, or as it was without one, reusing the screen that is already open. */
    fun openSession(sessionId: String?): PendingIntent = shade.open(sessionId?.let(NotificationLink::Session) ?: NotificationLink.App)

    /**
     * [order] places the row: Android lists a group newest first, using a
     * notification's own time when it is in the past. Fixed times keep the
     * sessions in tab order however often a row is updated; the times
     * themselves are never shown.
     */
    private fun baseBuilder(order: Int): Notification.Builder =
        shade.builder(IcodeChannel.Running)
            .setWhen(createdAt + order * ORDER_STEP_MILLIS)
            .setShowWhen(false)
            .setGroup(GROUP)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

    private fun serviceIntent(action: String, sessionId: String): PendingIntent =
        shade.toService(RuntimeForegroundService::class.java, action, "icode://terminal/$sessionId/$action") {
            putExtra(RuntimeForegroundService.EXTRA_SESSION_ID, sessionId)
        }

    companion object {
        /** The service's group: its summary, the session rows and the work's progress rows. */
        const val GROUP = "icode.terminal"
        private const val ORDER_PARENT = 1000
        private const val ORDER_STEP_MILLIS = 1000L

        /** "superpowers · running opencode · wake lock held". */
        fun rowText(workspace: String?, foreground: TerminalForeground, awake: Boolean): String {
            val activity = when (foreground) {
                TerminalForeground.Idle -> "idle"
                is TerminalForeground.Running -> "running ${foreground.program}"
                TerminalForeground.Unknown -> null
            }
            return listOfNotNull(workspace, activity, "wake lock held".takeIf { awake }).joinToString(" · ")
        }

        /** "bash · superpowers": which tab, in which workspace. */
        fun whereIs(title: String, workspace: String?): String = listOfNotNull(title, workspace).joinToString(" · ")
    }
}
