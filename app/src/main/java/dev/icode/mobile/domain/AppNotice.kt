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
}

fun AppNotice.markedRead(): AppNotice = when (this) {
    is AppNotice.TerminalSessionsEnded -> copy(read = true)
}
