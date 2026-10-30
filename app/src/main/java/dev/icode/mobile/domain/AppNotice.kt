package dev.icode.mobile.domain

/**
 * Something iCode tells the user, kept on the Notifications page until they
 * delete it. The page shows [read] = false with a dot on the home screen's bell.
 */
sealed interface AppNotice {
    /** Stable: the same event is never stored twice. */
    val id: String

    /** When it happened (epoch millis). */
    val at: Long
    val read: Boolean

    /** Terminal sessions that ended when Android ended iCode's previous process. */
    data class TerminalSessionsEnded(
        override val id: String,
        override val at: Long,
        override val read: Boolean,
        val lost: LostTerminalSessions,
    ) : AppNotice

    /** A clone or sync still running when iCode's process ended: Git never finished it. */
    data class GitTransferInterrupted(
        override val id: String,
        /** When it started; the process's end left no time of its own. */
        override val at: Long,
        override val read: Boolean,
        /** A [GitTransferLabel]. */
        val label: String,
        /** The workspace it was for, or the folder a clone was writing. */
        val target: String,
    ) : AppNotice

    /**
     * What iCode said where a toast once did (docs/notices-plan.md): [title] in a line, and
     * [detail] when there is more to it — a failure's own words.
     */
    data class Message(
        override val id: String,
        override val at: Long,
        override val read: Boolean,
        val tone: NoticeTone,
        val title: String,
        val detail: String? = null,
        /** A notice the website's admin wrote: its button, when it has one. */
        val link: NoticeLink? = null,
    ) : AppNotice {
        /** Said by the iCode website (Admin › Notices), not by this phone. */
        val fromWebsite: Boolean get() = id.startsWith(AppNotice.WEBSITE_PREFIX)
    }

    companion object {
        /** A website notice's id here: `web:` and the website's id, so it is stored once and never twice. */
        const val WEBSITE_PREFIX = "web:"
    }
}

/** A website notice's button: [label] opens [url] in the user's browser. */
data class NoticeLink(val url: String, val label: String)

/**
 * How a message reads: its colour and icon on the bell, the page and the sheet (docs/notices-plan.md
 * §2.1). In order, the most serious first.
 */
enum class NoticeTone {
    /** Something failed. */
    Problem,

    /** What to do first, or what was refused. */
    HeadsUp,

    /** Something happened. */
    Info,

    /** The user's own action, done: shown on the bell only, never kept. */
    Done,
}

fun AppNotice.markedRead(): AppNotice = when (this) {
    is AppNotice.TerminalSessionsEnded -> copy(read = true)
    is AppNotice.GitTransferInterrupted -> copy(read = true)
    is AppNotice.Message -> copy(read = true)
}

/** A notice's tone: the two kinds the previous process left behind ask the user to look again. */
val AppNotice.tone: NoticeTone
    get() = when (this) {
        is AppNotice.Message -> tone
        is AppNotice.TerminalSessionsEnded, is AppNotice.GitTransferInterrupted -> NoticeTone.HeadsUp
    }

/** The Notifications page, the bell and Android's shade (docs/notices-plan.md). */
sealed interface NoticeIntent : ICodeIntent {
    /** The Notifications page was opened: everything on it has been seen. */
    data object MarkAllRead : NoticeIntent

    /** A notice's sheet was opened: that one has been seen. */
    data class Read(val id: String) : NoticeIntent

    data class Delete(val id: String) : NoticeIntent

    data object Clear : NoticeIntent

    /** What a screen has to say, told as the backend's messages are. */
    data class Notify(val tone: NoticeTone, val title: String, val detail: String? = null) : NoticeIntent
}

/** What the bell, the Notifications page and Android's shade do with notices (docs/notices-plan.md §2). */
object NoticeRules {
    /** The page keeps this many, newest first. */
    const val MAX_KEPT = 100

    /** Android's shade holds at most this many messages, the newest unread. */
    const val MAX_POSTED = 20

    /** Kept on the page and posted: everything but what the user just did. */
    fun kept(message: AppNotice.Message): Boolean = message.tone != NoticeTone.Done

    /** Once its line has folded away, the bell stays this long, unless × puts it away first. */
    const val BELL_MILLIS = 10_000L

    /** How long the bell shows a notice's line before it folds away. */
    fun lineMillis(tone: NoticeTone): Long = when (tone) {
        NoticeTone.Done -> 3_000
        NoticeTone.HeadsUp, NoticeTone.Info -> 4_000
        NoticeTone.Problem -> 6_000
    }

    /** The folded bell's colour: the most serious unread tone; null with nothing unread. */
    fun bellTone(notices: List<AppNotice>): NoticeTone? = notices.filterNot { it.read }.minOfOrNull { it.tone }

    /** The messages Android's shade holds: the newest unread, at most [MAX_POSTED]. */
    fun posted(notices: List<AppNotice>): List<AppNotice.Message> =
        notices.filterIsInstance<AppNotice.Message>().filter { !it.read && kept(it) }.sortedByDescending { it.at }.take(MAX_POSTED)
}
