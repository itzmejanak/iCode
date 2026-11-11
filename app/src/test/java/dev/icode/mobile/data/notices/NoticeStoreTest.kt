package dev.icode.mobile.data.notices

import dev.icode.mobile.data.assistant.InMemoryPreferences
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticeAction
import dev.icode.mobile.domain.notices.NoticeCommand
import dev.icode.mobile.domain.notices.NoticeFilter
import dev.icode.mobile.domain.notices.NoticeKeep
import dev.icode.mobile.domain.notices.NoticePlace
import dev.icode.mobile.domain.notices.NoticeRules
import dev.icode.mobile.domain.notices.NoticeSource
import dev.icode.mobile.domain.notices.NoticeTarget
import dev.icode.mobile.domain.notices.NoticeTone
import dev.icode.mobile.domain.notices.WebNotices
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeStoreTest {
    private val preferences = InMemoryPreferences()

    private fun problem(at: Long, detail: String? = null) =
        Notice(NoticeTone.Problem, NoticeSource.Editor, "Couldn't save notes.md", detail = detail, id = "n$at", at = at)

    @Test fun `a notice comes back as it was kept, all it says and offers`() {
        val pulled = Notice(
            NoticeTone.Problem, NoticeSource.Git, "Couldn't pull main",
            place = NoticePlace("phone", "/w/phone", repository = "app", branch = "main", remote = "origin/main", subject = "src"),
            detail = "The remote has changes you don't have.",
            output = "! [rejected] main -> main (fetch first)",
            actions = listOf(
                NoticeAction.Run(NoticeCommand.Pull, "Try Again"), NoticeAction.Show(NoticeTarget.SourceControl),
                NoticeAction.OpenFile("/w/phone/a.md"), NoticeAction.OpenSession("s1"), NoticeAction.OpenUrl("https://x.dev", "Read"),
                NoticeAction.Undo("Moved a.md to Trash"),
            ),
            tellAway = true, repeatKey = "pull:/w/phone", id = "n1", at = 1, repeats = 2, away = true, dismissed = true,
        )
        NoticeStore(preferences).add(pulled)
        NoticeStore(preferences).add(problem(2))
        assertEquals(listOf(problem(2), pulled), NoticeStore(preferences).notices.value)
    }

    @Test fun `one is read, and only that one`() {
        val store = NoticeStore(preferences)
        store.add(problem(1))
        store.add(problem(2))
        store.markRead("n1")
        assertEquals(listOf(false, true), store.notices.value.map { it.read })
        assertEquals(listOf(false, true), NoticeStore(preferences).notices.value.map { it.read })
    }

    @Test fun `one swiped from the shade is still unread here`() {
        val store = NoticeStore(preferences)
        store.add(problem(1))
        store.dismiss("n1")
        assertEquals(listOf(true to false), NoticeStore(preferences).notices.value.map { it.dismissed to it.read })
    }

    @Test fun `the newest that need the user are kept, up to the page's most`() {
        val store = NoticeStore(preferences)
        (1..NoticeRules.MAX_NEEDS_YOU + 5L).forEach { store.add(problem(it)) }
        assertEquals(NoticeRules.MAX_NEEDS_YOU, store.notices.value.size)
        assertEquals("n${NoticeRules.MAX_NEEDS_YOU + 5}", store.notices.value.first().id)
        assertEquals("n6", store.notices.value.last().id)
    }

    private fun website(id: String) = WebNotices.fromWebsite(id, 5, "warning", "Known issue", "Builds fail", "https://icodze.vercel.app/notices", "Read more")

    @Test fun `a website notice comes back with its link`() {
        NoticeStore(preferences).add(website("a"))
        assertEquals(listOf(website("a")), NoticeStore(preferences).notices.value)
    }

    @Test fun `a deleted website notice is never added again`() {
        val store = NoticeStore(preferences)
        store.add(website("a"))
        store.delete("web:a")
        store.add(website("a"))
        assertTrue(store.notices.value.isEmpty())
        assertTrue(NoticeStore(preferences).knows("web:a"))
    }

    @Test fun `clear takes what the filter shows, and forgets website notices for good`() {
        val store = NoticeStore(preferences)
        val done = Notice(NoticeTone.Done, NoticeSource.Files, "Moved a.md to Trash", id = "d1", at = 3)
        store.add(website("b"))
        store.add(problem(1))
        store.add(done)
        store.clear(NoticeFilter.Done)
        assertEquals(listOf("web:b", "n1"), store.notices.value.map { it.id })
        store.clear(NoticeFilter.All)
        store.add(website("b"))
        store.add(problem(1))
        assertEquals(listOf("n1"), store.notices.value.map { it.id })
        assertFalse(store.knows("web:c"))
    }

    @Test fun `what an earlier build kept is read as notices`() {
        val v1 = JSONArray()
            .put(JSONObject("""{"kind":"message","id":"m1","at":4,"read":false,"tone":"Problem","title":"Unable to save","detail":null}"""))
            .put(JSONObject("""{"kind":"message","id":"web:x","at":3,"read":true,"tone":"Info","title":"News","detail":"Hi","linkUrl":"https://x.dev","linkLabel":"Read"}"""))
            .put(JSONObject("""{"kind":"git-transfer-interrupted","id":"g1","at":2,"read":false,"label":"Cloning","target":"/w/app"}"""))
            .put(JSONObject("""{"kind":"terminal-sessions-ended","id":"t1","at":1,"read":false,"cause":"LowMemory","endedAt":null,"sessions":[{"title":"bash","workspace":"phone"}]}"""))
            .put(JSONObject("""{"kind":"what-a-later-build-keeps","id":"z","at":0,"read":false}"""))
        preferences.edit().putString("notices", v1.toString()).apply()
        val notices = NoticeStore(preferences).notices.value
        assertEquals(listOf("m1", "web:x", "g1", "t1"), notices.map { it.id })
        assertEquals(NoticeKeep.NeedsYou, notices[0].keep)
        assertEquals(null, notices[0].detail)
        assertEquals(NoticeSource.Website to NoticeKeep.NeedsYou, notices[1].source to notices[1].keep)
        assertEquals(listOf(NoticeAction.OpenUrl("https://x.dev", "Read")), notices[1].actions)
        assertTrue(notices[1].read)
        assertEquals(NoticeSource.Git, notices[2].source)
        assertEquals(NoticeSource.Terminal, notices[3].source)
    }
}
