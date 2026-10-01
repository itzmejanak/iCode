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
}

fun AppNotice.markedRead(): AppNotice = when (this) {
    is AppNotice.TerminalSessionsEnded -> copy(read = true)
    is AppNotice.GitTransferInterrupted -> copy(read = true)
}
