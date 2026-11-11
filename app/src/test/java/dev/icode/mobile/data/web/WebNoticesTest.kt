package dev.icode.mobile.data.web

import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeAction
import dev.icode.mobile.domain.notices.NoticeKeep
import dev.icode.mobile.domain.notices.NoticeSource
import dev.icode.mobile.domain.notices.NoticeTone
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebNoticesTest {
    private fun read(vararg items: String) = WebJson.notices(JSONObject("""{"notices":[${items.joinToString()}]}"""), now = 42L)

    @Test fun `each level reads as the app's own tone, and every one needs the user`() {
        val notices = read(
            """{"id":"a","title":"A","body":"x","level":"critical"}""",
            """{"id":"b","title":"B","body":"x","level":"warning"}""",
            """{"id":"c","title":"C","body":"x","level":"info"}""",
        )
        assertEquals(listOf(NoticeTone.Problem, NoticeTone.HeadsUp, NoticeTone.Info), notices.map { it.tone })
        assertTrue(notices.all { it.keep == NoticeKeep.NeedsYou && it.source == NoticeSource.Website })
    }

    @Test fun `a website notice is kept under web and its id, with its time, body and link`() {
        val notice = read("""{"id":"65f0","title":"Known issue","body":"Builds fail","level":"warning","at":"2026-10-30T08:00:00.000Z","linkUrl":"https://icodze.vercel.app/notices","linkLabel":"Read more"}""").single()
        assertEquals("web:65f0", notice.id)
        assertTrue(notice.fromWebsite)
        assertEquals(1_793_347_200_000L, notice.at)
        assertEquals("Builds fail", notice.detail)
        assertEquals(listOf(NoticeAction.OpenUrl("https://icodze.vercel.app/notices", "Read more")), notice.actions)
    }

    @Test fun `no link, or one that is not https, means no button`() {
        assertTrue(read("""{"id":"a","title":"A","body":"x","level":"info","linkUrl":null}""").single().actions.isEmpty())
        assertTrue(read("""{"id":"a","title":"A","body":"x","level":"info","linkUrl":"http://x.dev"}""").single().actions.isEmpty())
    }

    @Test fun `a phone's own notice is not from the website`() {
        assertFalse(Notice(NoticeTone.Info, NoticeSource.Editor, "Saved notes.md").fromWebsite)
    }
}
