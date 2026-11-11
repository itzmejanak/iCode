package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.LostTerminalSession
import dev.icode.mobile.domain.LostTerminalSessions
import dev.icode.mobile.domain.ProcessEndCause
import dev.icode.mobile.domain.countOf

/** What the terminal tells (docs/notices-redesign-plan.md D8, D9). */
object TerminalNotices {
    /** The sessions iCode's previous process took with it. */
    fun sessionsEnded(lost: LostTerminalSessions): Notice {
        val count = lost.sessions.size
        return Notice(
            NoticeTone.HeadsUp,
            NoticeSource.Terminal,
            if (count == 1) "A terminal session ended" else "${countOf(count, "terminal session")} ended",
            place = lost.sessions.mapNotNull { it.workspace }.distinct().takeIf { it.isNotEmpty() }?.let { NoticePlace(it.joinToString(", ")) },
            detail = "${causeOf(lost.cause)}. Start them again from Terminal.\n${sessionsLine(lost.sessions)}",
            actions = listOf(NoticeAction.Show(NoticeTarget.Terminal)),
        )
    }

    /** A shell that exited with an error: kept, quietly, as what happened. */
    fun exited(tab: String, code: Int, place: NoticePlace?) =
        Notice(NoticeTone.Info, NoticeSource.Terminal, "$tab ended with exit code $code", place)

    fun outputNotShown(process: String, place: NoticePlace?) =
        Notice(NoticeTone.Problem, NoticeSource.Terminal, "Couldn't show $process's output", place)

    /**
     * A command that ran [millis] ended, "npm finished after 2 min" — its exit status is not known, so
     * nothing claims success. [alert]: worth the shade while away, as the user's setting says.
     */
    fun finished(program: String, millis: Long, place: NoticePlace?, session: String, alert: Boolean) = Notice(
        NoticeTone.Done,
        NoticeSource.Terminal,
        "$program finished after ${spokenDuration(millis)}",
        place,
        actions = listOf(NoticeAction.OpenSession(session, "Open Terminal")),
        tellAway = alert,
    )

    /** "45 s", "2 min", "1 h 5 min". */
    fun spokenDuration(millis: Long): String {
        val seconds = millis / 1_000
        return when {
            seconds < 60 -> "$seconds s"
            seconds < 3_600 -> "${seconds / 60} min"
            else -> listOfNotNull("${seconds / 3_600} h", (seconds % 3_600 / 60).takeIf { it > 0 }?.let { "$it min" })
                .joinToString(" ")
        }
    }

    fun needsRuntime() = Notice(NoticeTone.HeadsUp, NoticeSource.Terminal, "Install the Linux runtime first", detail = "Terminal sessions run in it.")

    fun notStarted(place: NoticePlace?) = Notice(NoticeTone.Problem, NoticeSource.Terminal, "Couldn't start a terminal session", place)

    fun ended() = Notice(NoticeTone.HeadsUp, NoticeSource.Terminal, "That terminal session has ended")

    /** What ended iCode's previous process, in the user's terms. */
    fun causeOf(cause: ProcessEndCause): String = when (cause) {
        ProcessEndCause.StoppedByUser -> "iCode was stopped from Android (Force stop, Task Manager or Recents)"
        ProcessEndCause.StoppedByUserOrUpdated -> "iCode was stopped from Android or updated"
        ProcessEndCause.Updated -> "iCode was updated"
        ProcessEndCause.PhoneRestarted -> "The phone restarted"
        ProcessEndCause.LowMemory -> "Android closed iCode to free memory"
        ProcessEndCause.Crashed -> "iCode crashed"
        ProcessEndCause.NotResponding -> "iCode stopped responding and Android closed it"
        ProcessEndCause.PermissionChanged -> "Android closed iCode after one of its permissions changed"
        ProcessEndCause.Killed -> "iCode's process was killed"
        ProcessEndCause.Other -> "iCode's process ended"
        ProcessEndCause.Unrecorded -> "iCode's process ended; Android kept no record of why"
    }

    /** "bash · superpowers, bash 2 · superpowers": tab names as they were, with their workspaces. */
    fun sessionsLine(sessions: List<LostTerminalSession>): String =
        sessions.joinToString(", ") { session -> listOfNotNull(session.title, session.workspace).joinToString(" · ") }
}
