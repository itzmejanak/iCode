package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeRulesTest {
    private fun message(at: Long, tone: NoticeTone, read: Boolean = false) = AppNotice.Message("m$at", at, read, tone, "said at $at")

    @Test fun `what the user just did is shown on the bell only`() {
        assertFalse(NoticeRules.kept(message(1, NoticeTone.Done)))
        listOf(NoticeTone.Problem, NoticeTone.HeadsUp, NoticeTone.Info).forEach { assertTrue(NoticeRules.kept(message(1, it))) }
    }

    @Test fun `a problem stays longest on the bell, a done shortest`() {
        assertEquals(listOf(6_000L, 4_000L, 4_000L, 3_000L), NoticeTone.entries.map(NoticeRules::lineMillis))
    }

    @Test fun `the folded bell takes the most serious unread tone`() {
        assertNull(NoticeRules.bellTone(listOf(message(1, NoticeTone.Problem, read = true))))
        assertEquals(NoticeTone.Info, NoticeRules.bellTone(listOf(message(1, NoticeTone.Info), message(2, NoticeTone.Problem, read = true))))
        assertEquals(NoticeTone.Problem, NoticeRules.bellTone(listOf(message(1, NoticeTone.Info), message(2, NoticeTone.Problem))))
        val ended = AppNotice.GitTransferInterrupted("g", 3, read = false, label = GitTransferLabel.CLONING, target = "/w/app")
        assertEquals(NoticeTone.HeadsUp, NoticeRules.bellTone(listOf(message(1, NoticeTone.Info), ended)))
    }

    @Test fun `the shade holds the newest unread messages, at most its limit`() {
        val many = (1..NoticeRules.MAX_POSTED + 3L).map { message(it, NoticeTone.Info) } + message(100, NoticeTone.Problem, read = true)
        val posted = NoticeRules.posted(many)
        assertEquals(NoticeRules.MAX_POSTED, posted.size)
        assertEquals("m${NoticeRules.MAX_POSTED + 3}", posted.first().id)
        assertEquals("m4", posted.last().id)
    }
}
