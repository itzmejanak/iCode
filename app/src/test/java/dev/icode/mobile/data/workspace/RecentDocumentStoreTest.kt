package dev.icode.mobile.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentDocumentStoreTest {
    private fun document(workspace: String, index: Int) = StoredRecentDocument(
        name = "$index.kt",
        location = "$workspace/$index.kt",
        workspaceLocation = workspace,
        lastOpenedAt = index.toLong(),
    )

    @Test
    fun `opening a file again moves it to the front without duplication`() {
        val first = document("/one", 1)
        val second = document("/one", 2)
        val actual = touchRecentDocument(listOf(first, second), first.copy(lastOpenedAt = 4))
        assertEquals(listOf(1, 2), actual.map { it.name.substringBefore('.').toInt() })
        assertEquals(4L, actual.first().lastOpenedAt)
    }

    @Test
    fun `recent files are bounded independently per workspace`() {
        val current = (1..25).map { document("/one", it) } + (1..25).map { document("/two", it) }
        val actual = touchRecentDocument(current, document("/one", 26))
        assertEquals(20, actual.count { it.workspaceLocation == "/one" })
        assertEquals(20, actual.count { it.workspaceLocation == "/two" })
        assertEquals("26.kt", actual.first().name)
    }
}
