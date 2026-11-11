package dev.icode.mobile.data.tasks

import android.app.Notification
import android.app.PendingIntent
import dev.icode.mobile.data.notify.IcodeChannel
import dev.icode.mobile.data.notify.IcodeNotifications
import dev.icode.mobile.data.notify.NotificationKey
import dev.icode.mobile.data.notify.NotificationLink
import dev.icode.mobile.data.runtime.RuntimeForegroundService
import dev.icode.mobile.data.runtime.TerminalNotifications
import dev.icode.mobile.domain.BackgroundItem

/**
 * Background work in the notification drawer (docs/long-operations-plan.md §6), as the reference
 * shows it: per piece of work a silent progress notification — "Deleting… (1207 / 11813)", the item
 * in hand, a bar, Cancel where the work allows it. Work that waits on the user (the assistant asking
 * before a change) pops its question up while iCode is out of view. A tap opens iCode on the work.
 * [RuntimeForegroundService] posts them; progress rows sit in its group, under its summary, with the
 * terminal sessions. Work that stops short is told as a Problem notice, posted once
 * (docs/notices-redesign-plan.md D7). Each is keyed by its work's own key.
 */
internal class WorkNotifications(private val shade: IcodeNotifications) {
    /** What each progress notification shows now, so an unchanged one is not posted again. */
    private val posted = mutableMapOf<String, BackgroundItem>()

    /** The question each piece of work has popped up, so one is posted once and withdrawn once answered. */
    private val asked = mutableMapOf<String, String>()

    /** Shows [items]: a progress notification for each that runs, and the questions while iCode is out of view ([screenVisible]). */
    fun show(items: List<BackgroundItem>, screenVisible: Boolean) {
        val running = items.associateBy { it.key }
        (posted.keys - running.keys).forEach { key ->
            posted.remove(key)
            shade.cancel(NotificationKey.Progress, key)
        }
        running.values.forEach { item ->
            if (posted[item.key] != item) {
                shade.post(NotificationKey.Progress, progress(item), tag = item.key)
                posted[item.key] = item
            }
        }
        ask(running.values, screenVisible)
    }

    /** A question pops up while iCode is out of view; in view, iCode shows it, and answered, it goes. */
    private fun ask(running: Collection<BackgroundItem>, screenVisible: Boolean) {
        val now = running.mapNotNull { item -> item.question?.takeIf { !screenVisible }?.let { item.key to it } }.toMap()
        (asked.keys - now.keys).toList().forEach { key ->
            asked.remove(key)
            shade.cancel(NotificationKey.Question, key)
        }
        now.forEach { (key, question) ->
            if (asked[key] == question) return@forEach
            shade.post(NotificationKey.Question, question(running.first { it.key == key }, question), tag = key)
            asked[key] = question
        }
    }

    /** The service stops: nothing runs any more, and a question goes with its work. */
    fun clear() {
        posted.keys.forEach { shade.cancel(NotificationKey.Progress, it) }
        posted.clear()
        asked.keys.forEach { shade.cancel(NotificationKey.Question, it) }
        asked.clear()
    }

    private fun progress(item: BackgroundItem): Notification {
        val fraction = item.fraction
        val builder = shade.builder(IcodeChannel.Progress)
            .setContentTitle(item.heading)
            .setContentText(item.detail)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            // In the service's group, under its summary, with the sessions: one iCode card.
            .setGroup(TerminalNotifications.GROUP)
            .setContentIntent(shade.open(NotificationLink.Work(item.place)))
        // Counting, or a step with no measure: the bar sweeps, as the sheet's does. What runs until stopped has none.
        if (item.progresses) builder.setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
        if (item.cancel != null) builder.addAction(shade.action(item.cancelLabel, cancel(item)))
        return builder.build()
    }

    private fun question(item: BackgroundItem, question: String): Notification =
        shade.builder(IcodeChannel.Questions)
            .setContentTitle("Waiting for your answer")
            .setContentText(question)
            .setStyle(Notification.BigTextStyle().bigText("$question\n${item.detail}"))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(shade.open(NotificationLink.Work(item.place)))
            .build()

    private fun cancel(item: BackgroundItem): PendingIntent =
        shade.toService(RuntimeForegroundService::class.java, RuntimeForegroundService.ACTION_CANCEL_WORK, "icode://work/${item.key}/cancel") {
            putExtra(RuntimeForegroundService.EXTRA_WORK_KEY, item.key)
        }

    private companion object {
        const val PROGRESS_MAX = 1000
    }
}
