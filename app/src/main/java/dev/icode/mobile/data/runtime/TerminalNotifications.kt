package dev.icode.mobile.data.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.icode.mobile.MainActivity
import dev.icode.mobile.R
import dev.icode.mobile.domain.TerminalSessionState

/**
 * What [RuntimeForegroundService] shows: a notification group whose parent is
 * the service's own notification, one row per running session with Exit and a
 * wake-lock toggle, and the intents behind them. Tapping a row opens that
 * session; the parent opens the selected one.
 */
internal class TerminalNotifications(private val context: Context) {
    private val createdAt = System.currentTimeMillis() - ORDER_PARENT * ORDER_STEP_MILLIS

    fun createChannel(notifications: NotificationManager) {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Development runtime", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while terminal sessions or long operations run"
                setShowBadge(false)
            },
        )
    }

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
            .addAction(action("Exit", serviceIntent(RuntimeForegroundService.ACTION_EXIT_SESSION, session.id)))
            .addAction(
                action(
                    if (awake) "Release wakelock" else "Acquire wakelock",
                    serviceIntent(RuntimeForegroundService.ACTION_TOGGLE_WAKE_LOCK, session.id),
                ),
            )
            .build()

    /** Opens iCode on [sessionId]'s terminal, reusing the screen that is already open. */
    fun openSession(sessionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (sessionId != null) {
            intent.setAction(MainActivity.ACTION_SHOW_TERMINAL_SESSION)
                .putExtra(MainActivity.EXTRA_TERMINAL_SESSION_ID, sessionId)
                // Distinct data keeps one pending intent per session.
                .setData("icode://terminal/$sessionId".toUri())
        }
        return PendingIntent.getActivity(
            context,
            sessionId?.let(::rowId) ?: REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * [order] places the row: Android lists a group newest first, using a
     * notification's own time when it is in the past. Fixed times keep the
     * sessions in tab order however often a row is updated; the times
     * themselves are never shown.
     */
    private fun baseBuilder(order: Int): Notification.Builder =
        Notification.Builder(context, CHANNEL_ID)
            .setWhen(createdAt + order * ORDER_STEP_MILLIS)
            .setShowWhen(false)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setGroup(GROUP)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

    private fun action(title: String, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(null, title, intent).build()

    private fun serviceIntent(action: String, sessionId: String): PendingIntent {
        val intent = Intent(context, RuntimeForegroundService::class.java)
            .setAction(action)
            .putExtra(RuntimeForegroundService.EXTRA_SESSION_ID, sessionId)
            // Distinct data keeps one pending intent per session and action.
            .setData("icode://terminal/$sessionId/$action".toUri())
        return PendingIntent.getService(
            context,
            rowId(sessionId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val PARENT_ID = 4711
        private const val CHANNEL_ID = "icode.runtime"
        /** The service's group: its summary, the session rows and the work's progress rows. */
        const val GROUP = "icode.terminal"
        private const val SESSION_ROW_BASE = 5000
        private const val ORDER_PARENT = 1000
        private const val ORDER_STEP_MILLIS = 1000L
        private const val REQUEST_OPEN_APP = 1

        /** Stable per session: "session-3" is always row 5003. */
        fun rowId(sessionId: String): Int =
            SESSION_ROW_BASE + (sessionId.substringAfterLast('-').toIntOrNull() ?: (sessionId.hashCode() and 0xFFF))

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
