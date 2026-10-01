package dev.icode.mobile.data.tasks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.icode.mobile.MainActivity
import dev.icode.mobile.R
import dev.icode.mobile.data.runtime.RuntimeForegroundService
import dev.icode.mobile.domain.BackgroundItem
import dev.icode.mobile.domain.BackgroundPlace

/**
 * Background work in the notification drawer (docs/long-operations-plan.md §6), as the reference
 * shows it: per piece of work a silent progress notification — "Deleting… (1207 / 11813)", the item
 * in hand, a bar, Cancel where the work allows it — and a failure that pops up when the user was
 * not watching. Work that waits on the user (the assistant asking before a change) pops its
 * question up while iCode is out of view. A tap opens iCode on the work. [RuntimeForegroundService]
 * posts them; the running summary is its own notification.
 */
internal class WorkNotifications(private val context: Context, private val notifications: NotificationManager) {
    /** What each progress notification shows now, so an unchanged one is not posted again. */
    private val posted = mutableMapOf<String, BackgroundItem>()

    /** The question each piece of work has popped up, so one is posted once and withdrawn once answered. */
    private val asked = mutableMapOf<String, Pair<Int, String>>()

    init {
        notifications.createNotificationChannel(
            // Minimum importance, as the reference's: no sound, no pop-up, no status-bar icon of its own.
            NotificationChannel(PROGRESS_CHANNEL, "Task progress", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Progress of long operations such as deleting, copying, importing and cloning"
                setShowBadge(false)
            },
        )
        notifications.createNotificationChannel(
            NotificationChannel(FAILURE_CHANNEL, "Task failures", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A long operation stopped short while you were elsewhere"
            },
        )
        notifications.createNotificationChannel(
            NotificationChannel(QUESTION_CHANNEL, "Needs your answer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Work waiting for you, such as the assistant asking before a change or a command"
            },
        )
    }

    /**
     * Shows [items]: a progress notification for each that runs. Work that was running here and
     * has stopped short pops up when the user was not watching it — [screenVisible] false, or its
     * progress was out of view in iCode (a hidden task).
     */
    fun show(items: List<BackgroundItem>, screenVisible: Boolean) {
        val running = items.filter { it.running }.associateBy { it.key }
        (posted.keys - running.keys).forEach { key ->
            val lastSeen = posted.remove(key) ?: return@forEach
            notifications.cancel(PROGRESS_BASE + lastSeen.number)
            val failed = items.firstOrNull { it.key == key && !it.running } ?: return@forEach
            if (!screenVisible || !lastSeen.watched) notifications.notify(FAILURE_BASE + failed.number, failure(failed))
        }
        running.values.forEach { item ->
            if (posted[item.key] != item) {
                notifications.notify(PROGRESS_BASE + item.number, progress(item))
                posted[item.key] = item
            }
        }
        ask(running.values, screenVisible)
    }

    /** A question pops up while iCode is out of view; in view, iCode shows it, and answered, it goes. */
    private fun ask(running: Collection<BackgroundItem>, screenVisible: Boolean) {
        val now = running.mapNotNull { item -> item.question?.takeIf { !screenVisible }?.let { item.key to (item.number to it) } }.toMap()
        (asked.keys - now.keys).toList().forEach { key -> asked.remove(key)?.let { (number, _) -> notifications.cancel(QUESTION_BASE + number) } }
        now.forEach { (key, question) ->
            if (asked[key] == question) return@forEach
            val item = running.first { it.key == key }
            notifications.notify(QUESTION_BASE + item.number, question(item, question.second))
            asked[key] = question
        }
    }

    /** The service stops: nothing runs any more. A failure stays until it is read; a question goes with its work. */
    fun clear() {
        posted.values.forEach { notifications.cancel(PROGRESS_BASE + it.number) }
        posted.clear()
        asked.values.forEach { (number, _) -> notifications.cancel(QUESTION_BASE + number) }
        asked.clear()
    }

    private fun progress(item: BackgroundItem): Notification {
        val fraction = item.fraction
        val builder = Notification.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(item.heading)
            .setContentText(item.detail)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            // Counting, or a step with no measure: the bar sweeps, as the dialog's does.
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
            .setContentIntent(open(context, item.place))
        if (item.cancel != null) builder.addAction(Notification.Action.Builder(null, "Cancel", cancel(item)).build())
        return builder.build()
    }

    private fun failure(item: BackgroundItem): Notification =
        Notification.Builder(context, FAILURE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle("${item.title.removeSuffix("…")} stopped")
            .setContentText(item.failure)
            .setStyle(Notification.BigTextStyle().bigText(item.failure))
            .setCategory(Notification.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(open(context, item.place))
            .build()

    private fun question(item: BackgroundItem, question: String): Notification =
        Notification.Builder(context, QUESTION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle("Waiting for your answer")
            .setContentText(question)
            .setStyle(Notification.BigTextStyle().bigText("$question\n${item.detail}"))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open(context, item.place))
            .build()

    private fun cancel(item: BackgroundItem): PendingIntent {
        val intent = Intent(context, RuntimeForegroundService::class.java)
            .setAction(RuntimeForegroundService.ACTION_CANCEL_WORK)
            .putExtra(RuntimeForegroundService.EXTRA_WORK_KEY, item.key)
            // Distinct data keeps one pending intent per piece of work.
            .setData("icode://work/${item.key}/cancel".toUri())
        return PendingIntent.getService(context, item.number, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private const val PROGRESS_CHANNEL = "icode.work-progress"
        private const val FAILURE_CHANNEL = "icode.work-failures"
        private const val QUESTION_CHANNEL = "icode.work-questions"
        private const val PROGRESS_BASE = 7000
        private const val FAILURE_BASE = 8000
        private const val QUESTION_BASE = 9000
        private const val PROGRESS_MAX = 1000
        private const val REQUEST_OPEN = 20_000

        /** Work that stopped short and has since been read, dismissed or run again: its failure goes. */
        fun dismissFailure(context: Context, item: BackgroundItem) {
            context.getSystemService(NotificationManager::class.java).cancel(FAILURE_BASE + item.number)
        }

        /** Opens iCode on [place]: the task's dialog, or the work's own page. */
        fun open(context: Context, place: BackgroundPlace): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHOW_WORK)
                .putExtra(MainActivity.EXTRA_WORK_PLACE, place.encoded())
                .setData("icode://work/${place.encoded()}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context,
                // Distinct data keeps one pending intent per place.
                REQUEST_OPEN,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

    }
}
