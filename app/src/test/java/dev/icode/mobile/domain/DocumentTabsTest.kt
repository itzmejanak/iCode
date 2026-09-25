package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentTabsTest {
    private val tabs = listOf("a", "b", "c").map { name ->
        OpenDocument(name, name, name, TextEncoding.Utf8, LineEnding.Lf, dirty = false, readOnly = false)
    }

    @Test fun `reordering keeps document identity and text`() {
        assertEquals(listOf("b", "c", "a"), reorderDocuments(tabs, "a", "c").map { it.location })
        assertEquals(tabs, reorderDocuments(tabs, "missing", "b"))
    }

    @Test fun `closing active chooses adjacent surviving tab`() {
        assertEquals("c", selectedAfterClose(tabs, "b", "b"))
        assertEquals("b", selectedAfterClose(tabs, "c", "c"))
        assertEquals("a", selectedAfterClose(tabs, "a", "c"))
        assertEquals(null, selectedAfterClose(tabs.take(1), "a", "a"))
    }
}
