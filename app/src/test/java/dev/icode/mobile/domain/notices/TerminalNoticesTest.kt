package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.LostTerminalSession
import dev.icode.mobile.domain.LostTerminalSessions
import dev.icode.mobile.domain.ProcessEndCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalNoticesTest {
    private val sessions = listOf(
        LostTerminalSession("bash", "superpowers"),
        LostTerminalSession("bash 2", "superpowers"),
        LostTerminalSession("bash 3", null),
    )

    @Test fun `sessions are listed in tab order with their workspaces`() {
        assertEquals("bash · superpowers, bash 2 · superpowers, bash 3", TerminalNotices.sessionsLine(sessions))
    }

    @Test fun `every cause has its own sentence`() {
        val sentences = ProcessEndCause.entries.map(TerminalNotices::causeOf)
        assertEquals(sentences.size, sentences.toSet().size)
        assertTrue(sentences.none { it.isBlank() || it.endsWith(".") })
    }

    @Test fun `durations are said the way a person would`() {
        assertEquals("45 s", TerminalNotices.spokenDuration(45_900))
        assertEquals("2 min", TerminalNotices.spokenDuration(130_000))
        assertEquals("1 h", TerminalNotices.spokenDuration(3_600_000))
        assertEquals("1 h 5 min", TerminalNotices.spokenDuration(3_900_000))
    }

    @Test fun `a long command's end says how long and where, opens its tab, and is posted away only when asked`() {
        val place = NoticePlace("superpowers", "/w/superpowers", subject = "bash")
        val notice = TerminalNotices.finished("npm", 130_000, place, "session-3", alert = true)
        assertEquals("npm finished after 2 min", notice.title)
        assertEquals("superpowers · bash", notice.place?.line)
        assertEquals(NoticeKeep.Done, notice.keep)
        assertEquals(listOf(NoticeAction.OpenSession("session-3", "Open Terminal")), notice.actions)
        assertTrue(notice.tellAway)
        assertFalse(TerminalNotices.finished("npm", 130_000, place, "session-3", alert = false).tellAway)
    }

    @Test fun `ended sessions say how many, why, and where`() {
        val notice = TerminalNotices.sessionsEnded(LostTerminalSessions(sessions, ProcessEndCause.LowMemory, endedAt = null))
        assertEquals("3 terminal sessions ended", notice.title)
        assertEquals("superpowers", notice.place?.line)
        assertEquals(
            "Android closed iCode to free memory. Start them again from Terminal.\nbash · superpowers, bash 2 · superpowers, bash 3",
            notice.detail,
        )
        assertEquals(listOf(NoticeAction.Show(NoticeTarget.Terminal)), notice.actions)
    }
}
