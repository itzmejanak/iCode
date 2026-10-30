package dev.icode.mobile.data.web

import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.NoticeLink
import dev.icode.mobile.domain.NoticeTone
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebNoticesTest {
    private fun read(vararg items: String) = WebJson.notices(JSONObject("""{"notices":[${items.joinToString()}]}"""), now = 42L)

    @Test fun `each level reads as the app's own tone`() {
        val tones = read(
            """{"id":"a","title":"A","body":"x","level":"critical"}""",
            """{"id":"b","title":"B","body":"x","level":"warning"}""",
            """{"id":"c","title":"C","body":"x","level":"info"}""",
        ).map { it.tone }
        assertEquals(listOf(NoticeTone.Problem, NoticeTone.HeadsUp, NoticeTone.Info), tones)
    }

    @Test fun `a website notice is kept under web and its id, with its time, body and link`() {
        val notice = read("""{"id":"65f0","title":"Known issue","body":"Builds fail","level":"warning","at":"2026-10-30T08:00:00.000Z","linkUrl":"https://icodze.vercel.app/notices","linkLabel":"Read more"}""").single()
        assertEquals("web:65f0", notice.id)
        assertTrue(notice.fromWebsite)
        assertEquals(1_793_347_200_000L, notice.at)
        assertEquals("Builds fail", notice.detail)
        assertEquals(NoticeLink("https://icodze.vercel.app/notices", "Read more"), notice.link)
    }

    @Test fun `no link, or one that is not https, means no button`() {
        assertNull(read("""{"id":"a","title":"A","body":"x","level":"info","linkUrl":null}""").single().link)
        assertNull(read("""{"id":"a","title":"A","body":"x","level":"info","linkUrl":"http://x.dev"}""").single().link)
    }

    @Test fun `a phone's own message is not from the website`() {
        assertTrue(!AppNotice.Message("m1", 1, read = false, tone = NoticeTone.Info, title = "Saved").fromWebsite)
    }
}
