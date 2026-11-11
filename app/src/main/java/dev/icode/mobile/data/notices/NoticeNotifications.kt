package dev.icode.mobile.data.notices

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.icode.mobile.MainActivity
import dev.icode.mobile.R
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeRules
import dev.icode.mobile.domain.notices.NoticeTone

/**
 * Notices in Android's shade (docs/notices-plan.md §2.5): every unread message, the newest
 * [NoticeRules.MAX_POSTED], silent under one summary. A Problem that comes while iCode is out of view
 * pops up. Read, deleted or cleared in iCode, it leaves the shade. Each is posted under its notice's
 * id as its tag, so what an earlier process posted is found, and withdrawn, by a later one.
 */
internal class NoticeNotifications(private val context: Context, private val notifications: NotificationManager) {
    init {
        notifications.createNotificationChannel(
            NotificationChannel(MESSAGES_CHANNEL, "Messages", NotificationManager.IMPORTANCE_LOW).apply {
                description = "What iCode tells you, as its Notifications page keeps it: no sound, no pop-up"
            },
        )
        notifications.createNotificationChannel(
            NotificationChannel(PROBLEMS_CHANNEL, "Problems", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A problem iCode meets while you are elsewhere"
            },
        )
    }

    /** The shade as [notices] are now; a Problem new to it pops up when iCode is not in view ([screenVisible]). */
    fun show(notices: List<Notice>, screenVisible: Boolean) {
        val posted = NoticeRules.posted(notices)
        val shown = notifications.activeNotifications.filter { it.id == NOTICE_ID && it.tag != null }.mapTo(hashSetOf()) { it.tag }
        (shown - posted.mapTo(hashSetOf()) { it.id }).forEach { notifications.cancel(it, NOTICE_ID) }
        posted.filterNot { it.id in shown }.forEach { notice ->
            notifications.notify(notice.id, NOTICE_ID, message(notice, popUp = notice.tone == NoticeTone.Problem && !screenVisible))
        }
        if (posted.isEmpty()) notifications.cancel(SUMMARY_ID) else notifications.notify(SUMMARY_ID, summary(posted.size))
    }

    private fun message(notice: Notice, popUp: Boolean): Notification =
        Notification.Builder(context, if (popUp) PROBLEMS_CHANNEL else MESSAGES_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(notice.title)
            .apply { notice.detail?.let { setContentText(it).setStyle(Notification.BigTextStyle().bigText(it)) } }
            .setCategory(if (notice.tone == NoticeTone.Problem) Notification.CATEGORY_ERROR else Notification.CATEGORY_STATUS)
            .setWhen(notice.at)
            .setShowWhen(true)
            .setGroup(GROUP)
            .setAutoCancel(true)
            .setContentIntent(open(notice.id))
            .build()

    private fun summary(count: Int): Notification =
        Notification.Builder(context, MESSAGES_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle("Notifications")
            .setContentText("$count unread")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open(null))
            .build()

    /** iCode on notice [id]'s sheet; without one, on the Notifications page. */
    private fun open(id: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_SHOW_NOTICE)
            .apply { id?.let { putExtra(MainActivity.EXTRA_NOTICE_ID, it) } }
            // Distinct data keeps one pending intent per notice.
            .setData("icode://notice/${id.orEmpty()}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, NOTICE_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val MESSAGES_CHANNEL = "icode.messages"
        const val PROBLEMS_CHANNEL = "icode.problems"
        const val GROUP = "icode.notices"
        const val NOTICE_ID = 10_500
        const val SUMMARY_ID = 10_501
    }
}
