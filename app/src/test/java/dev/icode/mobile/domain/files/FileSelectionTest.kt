package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.StorageMeasurement
import org.junit.Assert.assertEquals
import org.junit.Test

/** What is ticked, and what a selection acts on (docs/explorer-gaps-plan.md §3.2). */
class FileSelectionTest {
    private val shown = listOf("/w/a", "/w/b", "/w/c")

    @Test fun `a tap ticks and unticks`() {
        assertEquals(setOf("/w/a"), FileSelection.toggled(emptySet(), "/w/a"))
        assertEquals(emptySet<String>(), FileSelection.toggled(setOf("/w/a"), "/w/a"))
    }

    @Test fun `select all and invert work on what is shown`() {
        assertEquals(shown.toSet(), FileSelection.all(shown))
        assertEquals(setOf("/w/b", "/w/c"), FileSelection.inverted(setOf("/w/a", "/w/hidden-by-search"), shown))
    }

    @Test fun `a ticked folder covers what is in it, so nothing is acted on twice`() {
        assertEquals(
            listOf("/w/src", "/w/README.md", "/w/srcs"),
            FileSelection.covering(listOf("/w/src", "/w/src/App.kt", "/w/README.md", "/w/src/ui/Row.kt", "/w/srcs", "/w/src")),
        )
    }

    @Test fun `a selection says what it holds`() {
        assertEquals("2 folders, 1 file", FileSelection.counted(2, 1))
        assertEquals("1 folder", FileSelection.counted(1, 0))
        assertEquals("3 files", FileSelection.counted(0, 3))
        assertEquals("0 files", FileSelection.counted(0, 0))
    }

    @Test fun `a selection weighs its files, and the folders measured`() {
        val items = listOf(
            BrowsedItem("app", "/w/app", folder = true, changedAt = 0),
            BrowsedItem("lib", "/w/lib", folder = true, changedAt = 0),
            BrowsedItem("a.png", "/w/a.png", folder = false, bytes = 2048, changedAt = 0),
        )
        assertEquals("2 folders, 1 file · 2.0 KB", FileSelection.summary(items, emptyMap()))
        assertEquals("2 folders, 1 file · 3.0 KB", FileSelection.summary(items, mapOf("/w/app" to StorageMeasurement(1024))))
    }
}
