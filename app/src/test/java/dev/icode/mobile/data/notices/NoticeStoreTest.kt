package dev.icode.mobile.data.notices

import dev.icode.mobile.data.assistant.InMemoryPreferences
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.NoticeRules
import dev.icode.mobile.domain.NoticeTone
import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeStoreTest {
    private val preferences = InMemoryPreferences()

    private fun message(at: Long, detail: String? = null) =
        AppNotice.Message("m$at", at, read = false, tone = NoticeTone.Problem, title = "Unable to save document", detail = detail)

    @Test fun `a message comes back as it was kept, its detail or none`() {
        val store = NoticeStore(preferences)
        store.add(message(1, detail = "EACCES: permission denied"))
        store.add(message(2))
        assertEquals(listOf(message(2), message(1, detail = "EACCES: permission denied")), NoticeStore(preferences).notices.value)
    }

    @Test fun `one is read, and only that one`() {
        val store = NoticeStore(preferences)
        store.add(message(1))
        store.add(message(2))
        store.markRead("m1")
        assertEquals(listOf(false, true), store.notices.value.map { it.read })
        assertEquals(listOf(false, true), NoticeStore(preferences).notices.value.map { it.read })
    }

    @Test fun `the newest are kept, up to the page's limit`() {
        val store = NoticeStore(preferences)
        (1..NoticeRules.MAX_KEPT + 5L).forEach { store.add(message(it)) }
        assertEquals(NoticeRules.MAX_KEPT, store.notices.value.size)
        assertEquals("m${NoticeRules.MAX_KEPT + 5}", store.notices.value.first().id)
        assertEquals("m6", store.notices.value.last().id)
    }
}
