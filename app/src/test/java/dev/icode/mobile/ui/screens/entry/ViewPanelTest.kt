package dev.icode.mobile.ui.screens.entry

import dev.icode.mobile.designsystem.columns
import dev.icode.mobile.domain.files.FilesLook
import dev.icode.mobile.domain.files.FilesSort
import dev.icode.mobile.domain.files.FilesView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ☰ panel offers View and Sort as an option panel, the choices in use marked (docs/explorer-gaps-plan.md §3.12). */
class ViewPanelTest {
    private val looks = mutableListOf<FilesLook>()
    private val hidden = mutableListOf<Boolean>()

    private fun panel(look: FilesLook = FilesLook(), showHidden: Boolean = false, withHidden: Boolean = true) =
        viewPanel(look, showHidden, { looks += it }, if (withHidden) ({ hidden += it }) else null)

    @Test
    fun `View then Sort, as ZArchiver's, Hidden set apart at the end of View`() {
        val groups = panel()
        assertEquals(listOf("View", "Sort"), groups.map { it.title })
        assertEquals(listOf("Detailed", "Compact", "Grid"), groups[0].options.map { it.label })
        assertEquals(listOf("Hidden"), groups[0].aside.map { it.label })
        assertEquals(listOf("Name", "Size", "Date", "Type", "Desc"), groups[1].options.map { it.label })
    }

    @Test
    fun `the choices in use are the ones marked`() {
        val groups = panel(FilesLook(FilesView.Grid, FilesSort.Size, descending = true), showHidden = true)
        assertEquals(listOf("Grid"), groups[0].options.filter { it.chosen }.map { it.label })
        assertTrue(groups[0].aside.single().chosen)
        assertEquals(listOf("Size", "Desc"), groups[1].options.filter { it.chosen }.map { it.label })
    }

    @Test
    fun `a tap changes one thing, a new sort reads its own way first, and Descending flips it`() {
        val groups = panel(FilesLook(FilesView.Detailed, FilesSort.Date, descending = true))
        groups[0].options.single { it.label == "Compact" }.onClick()
        groups[1].options.single { it.label == "Name" }.onClick()
        groups[1].options.single { it.label == "Desc" }.onClick()
        assertEquals(
            listOf(
                FilesLook(FilesView.Compact, FilesSort.Date, descending = true),
                FilesLook(FilesView.Detailed, FilesSort.Name, descending = false),
                FilesLook(FilesView.Detailed, FilesSort.Date, descending = false),
            ),
            looks,
        )
    }

    @Test
    fun `Hidden files flips, and is not offered where it does not apply`() {
        panel(showHidden = false)[0].aside.single().onClick()
        assertEquals(listOf(true), hidden)
        assertTrue(panel(withHidden = false)[0].aside.isEmpty())
    }

    @Test
    fun `a short label is spoken in full, and the panel is as wide as its widest row`() {
        val groups = panel()
        assertEquals("Descending", groups[1].options.last().description)
        assertEquals("Hidden files", groups[0].aside.single().description)
        assertEquals(5, groups.columns())
    }
}
