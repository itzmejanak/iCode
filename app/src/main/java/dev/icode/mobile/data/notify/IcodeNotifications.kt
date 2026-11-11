package dev.icode.mobile.data.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.icode.mobile.MainActivity
import dev.icode.mobile.R
import dev.icode.mobile.domain.BackgroundPlace

/**
 * What iCode posts, each under one id of its own and, where there are many at once, a tag of the
 * thing's own id (docs/notices-redesign-plan.md D7): no ranges to keep apart, nothing to collide.
 */
internal enum class NotificationKey(val id: Int) {
    /** The runtime service's own, its group's summary. */
    Running(4711),

    /** A terminal session's row; tagged with the session's id. */
    Session(5000),

    /** A terminal program rang the bell; tagged with the session's id. */
    ProgramAsks(6000),

    /** Work's progress; tagged with the work's key. */
    Progress(7000),

    /** What work waits on the user for; tagged with the work's key. */
    Question(9000),
    PhoneControl(10_000),

    /** A notice; tagged with its id, so a later process finds what an earlier one posted. */
    Notice(10_500),
    NoticeSummary(10_501),
}

/** Where a notification's tap opens iCode: one encoding, written here and read back by [read]. */
internal sealed interface NotificationLink {
    /** iCode as it was left. */
    data object App : NotificationLink

    /** Its workspace, the Terminal panel, that tab. */
    data class Session(val id: String) : NotificationLink

    /** Where iCode shows that work (docs/long-operations-plan.md §6). */
    data class Work(val place: BackgroundPlace) : NotificationLink

    /** The notice's sheet — or, with no [id], the Notifications page; [act]: its main action done instead, an Open. */
    data class Notice(val id: String?, val act: Boolean = false) : NotificationLink

    companion object {
        private const val ACTION_SESSION = "dev.icode.mobile.action.SHOW_TERMINAL_SESSION"
        private const val ACTION_WORK = "dev.icode.mobile.action.SHOW_WORK"
        private const val ACTION_NOTICE = "dev.icode.mobile.action.SHOW_NOTICE"
        private const val EXTRA_SESSION = "terminal_session"
        private const val EXTRA_WORK = "work_place"
        private const val EXTRA_NOTICE = "notice"
        private const val EXTRA_ACT = "act"

        /** MainActivity on [link], the screen that is open reused. Each link's own data keeps one pending intent per link. */
        fun intent(context: Context, link: NotificationLink): Intent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return when (link) {
                App -> intent.setData("icode://app".toUri())
                is Session -> intent.setAction(ACTION_SESSION).putExtra(EXTRA_SESSION, link.id).setData("icode://terminal/${link.id}".toUri())
                is Work -> intent.setAction(ACTION_WORK).putExtra(EXTRA_WORK, link.place.encoded()).setData("icode://work/${link.place.encoded()}".toUri())
                is Notice -> intent.setAction(ACTION_NOTICE)
                    .apply { link.id?.let { putExtra(EXTRA_NOTICE, it) } }
                    .putExtra(EXTRA_ACT, link.act)
                    .setData("icode://notice/${link.id.orEmpty()}${if (link.act) "/act" else ""}".toUri())
            }
        }

        /** What [intent] asks to show; null for anything not written by [intent]. */
        fun read(intent: Intent?): NotificationLink? = when (intent?.action) {
            ACTION_SESSION -> intent.getStringExtra(EXTRA_SESSION)?.let(::Session)
            ACTION_WORK -> BackgroundPlace.decode(intent.getStringExtra(EXTRA_WORK))?.let(::Work)
            ACTION_NOTICE -> Notice(intent.getStringExtra(EXTRA_NOTICE), intent.getBooleanExtra(EXTRA_ACT, false))
            else -> null
        }
    }
}

/**
 * The one way iCode builds and posts its notifications (D7): its icon and colour, a registered
 * channel ([IcodeChannels]), the place as the header's sub-text, a key ([NotificationKey]) and a tap
 * that opens a [NotificationLink].
 */
internal class IcodeNotifications(private val context: Context) {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    /** A notification on [channel]; [place] — "phone · src" — beside iCode's name in its header. */
    fun builder(channel: IcodeChannel, place: String? = null): Notification.Builder =
        Notification.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .apply { place?.takeIf { it.isNotEmpty() }?.let(::setSubText) }

    fun post(key: NotificationKey, notification: Notification, tag: String? = null) = manager.notify(tag, key.id, notification)

    fun cancel(key: NotificationKey, tag: String? = null) = manager.cancel(tag, key.id)

    /** The tags of [key]'s notifications in the shade now: posted by this process or an earlier one. */
    fun shown(key: NotificationKey): Set<String> = manager.activeNotifications.filter { it.id == key.id }.mapNotNullTo(hashSetOf()) { it.tag }

    /** A tap opening iCode on [link]. */
    fun open(link: NotificationLink): PendingIntent =
        PendingIntent.getActivity(context, REQUEST, NotificationLink.intent(context, link), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** A tap sending [action] to [service]; [data] keeps one pending intent per thing and action. */
    fun toService(service: Class<out Service>, action: String, data: String, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, service).setAction(action).setData(data.toUri()).apply(extras)
        return PendingIntent.getService(context, REQUEST, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun action(label: String, intent: PendingIntent): Notification.Action = Notification.Action.Builder(null, label, intent).build()

    private companion object {
        /** Every pending intent's: each is told apart by its data. */
        const val REQUEST = 0
    }
}
