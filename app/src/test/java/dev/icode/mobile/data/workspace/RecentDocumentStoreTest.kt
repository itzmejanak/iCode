package dev.icode.mobile.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentDocumentStoreTest {
    /** A workspace is the folder right under /workspaces; phone storage is a group of its own. */
    private val groupOf = { location: String -> location.takeIf { it.startsWith("/workspaces/") }?.split('/')?.take(3)?.joinToString("/") }

    private fun document(group: String, index: Int) = StoredRecentDocument("$group/$index.kt", index.toLong())

    @Test
    fun `opening a file again moves it to the front without duplication`() {
        val first = document("/workspaces/one", 1)
        val second = document("/workspaces/one", 2)
        val actual = touchRecentDocument(listOf(first, second), first.copy(lastOpenedAt = 4), groupOf)
        assertEquals(listOf(first.location, second.location), actual.map { it.location })
        assertEquals(4L, actual.first().lastOpenedAt)
    }

    @Test
    fun `recent files are bounded independently per workspace and for phone storage`() {
        val groups = listOf("/workspaces/one", "/workspaces/two", "/storage/emulated/0/Download")
        val current = groups.flatMap { group -> (1..25).map { document(group, it) } }
        val actual = touchRecentDocument(current, document("/workspaces/one", 26), groupOf)
        groups.forEach { group -> assertEquals(20, actual.count { it.location.startsWith("$group/") }) }
        assertEquals("/workspaces/one/26.kt", actual.first().location)
    }

    @Test
    fun `a hundred in all`() {
        val current = (1..10).flatMap { workspace -> (1..15).map { document("/workspaces/w$workspace", it) } }
        assertEquals(100, touchRecentDocument(current, document("/workspaces/new", 1), groupOf).size)
    }
}
