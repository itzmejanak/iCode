package dev.icode.mobile.data.notices

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.icode.mobile.data.notify.IcodeChannel
import dev.icode.mobile.data.notify.IcodeNotifications
import dev.icode.mobile.data.notify.NotificationKey
import dev.icode.mobile.data.notify.NotificationLink
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeAction
import dev.icode.mobile.domain.notices.NoticeRules
import dev.icode.mobile.domain.notices.NoticeTone

/**
 * Notices in Android's shade (docs/notices-redesign-plan.md D7): what needs the user, unread, and work
 * that finished while iCode was out of view — the newest [NoticeRules.MAX_POSTED], under one summary.
 * Each says where in its header. A Problem that comes while iCode is out of view pops up; what
 * finished away sounds, on Finished; the rest is silent, on Messages. Read, deleted or cleared in
 * iCode, it leaves the shade; swiped away there, it is not posted again ([NoticeSwipedReceiver]).
 * Each is posted under its notice's id as its tag, so what an earlier process posted is found, and
 * withdrawn, by a later one.
 */
internal class NoticeNotifications(private val context: Context, private val shade: IcodeNotifications) {
    /** The shade as [notices] are now; a Problem new to it pops up when iCode is not in view ([screenVisible]). */
    fun show(notices: List<Notice>, screenVisible: Boolean) {
        val posted = NoticeRules.posted(notices)
        val shown = shade.shown(NotificationKey.Notice)
        (shown - posted.mapTo(hashSetOf()) { it.id }).forEach { shade.cancel(NotificationKey.Notice, it) }
        posted.filterNot { it.id in shown }.forEach { notice -> shade.post(NotificationKey.Notice, message(notice, screenVisible), tag = notice.id) }
        if (posted.isEmpty()) shade.cancel(NotificationKey.NoticeSummary) else shade.post(NotificationKey.NoticeSummary, summary(posted.size))
    }

    private fun channelOf(notice: Notice, screenVisible: Boolean): IcodeChannel = when {
        notice.tone == NoticeTone.Problem && !screenVisible -> IcodeChannel.Problems
        notice.away -> IcodeChannel.Finished
        else -> IcodeChannel.Messages
    }

    private fun message(notice: Notice, screenVisible: Boolean): Notification {
        val problem = notice.tone == NoticeTone.Problem
        return shade.builder(channelOf(notice, screenVisible), place = notice.place?.line)
            .setContentTitle(notice.title)
            // Why, or — with nothing more to say — the error's own words.
            .apply { (notice.detail ?: notice.output)?.let { setContentText(it).setStyle(Notification.BigTextStyle().bigText(it)) } }
            .setCategory(if (problem) Notification.CATEGORY_ERROR else Notification.CATEGORY_STATUS)
            .setWhen(notice.at)
            .setShowWhen(true)
            .setGroup(GROUP)
            .setAutoCancel(true)
            .setContentIntent(shade.open(NotificationLink.Notice(notice.id)))
            .setDeleteIntent(NoticeSwipedReceiver.intent(context, notice.id))
            // Its main action as a button, where it only opens something: what changes things is chosen in iCode, seen first.
            .apply { notice.actions.firstOrNull()?.takeIf(::opens)?.let { addAction(shade.action(it.label, shade.open(NotificationLink.Notice(notice.id, act = true)))) } }
            .build()
    }

    private fun opens(action: NoticeAction): Boolean = when (action) {
        is NoticeAction.OpenFile, is NoticeAction.OpenSession, is NoticeAction.OpenUrl, is NoticeAction.Show -> true
        is NoticeAction.Undo, is NoticeAction.Run -> false
    }

    private fun summary(count: Int): Notification =
        shade.builder(IcodeChannel.Messages)
            .setContentTitle("Notifications")
            .setContentText("$count unread")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(shade.open(NotificationLink.Notice(null)))
            .build()

    private companion object {
        const val GROUP = "icode.notices"
    }
}

/**
 * A notice swiped from Android's shade — on its own, or with its group or Clear all: marked dismissed,
 * so it is not posted again, and still unread in iCode. It reaches the one store directly, as
 * `NoticeBoard` does, without starting the rest of iCode in a process Android may have started for
 * this alone; the board, when it runs, follows the store.
 */
class NoticeSwipedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra(EXTRA_ID)?.let { NoticeStore.get(context).dismiss(it) }
    }

    internal companion object {
        private const val EXTRA_ID = "notice"

        fun intent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            // Distinct data keeps one pending intent per notice.
            Intent(context, NoticeSwipedReceiver::class.java).putExtra(EXTRA_ID, id).setData("icode://notice/$id/swiped".toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
