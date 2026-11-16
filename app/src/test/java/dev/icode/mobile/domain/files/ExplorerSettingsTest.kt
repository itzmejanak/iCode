package dev.icode.mobile.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The panel's choices, kept for folders and for each view (docs/workspaces-and-files.md). */
class ExplorerSettingsTest {
    @Test fun `folders open as today, newest first in rows`() {
        assertEquals(FilesLook(FilesView.Detailed, FilesSort.Date, descending = true), ExplorerSettings().look(null))
    }

    @Test fun `a new sort reads its own way first`() {
        val look = FilesLook(sort = FilesSort.Date)
        assertEquals(FilesLook(sort = FilesSort.Name, descending = false), look.sortedBy(FilesSort.Name))
        assertEquals(FilesLook(sort = FilesSort.Size, descending = true), look.sortedBy(FilesSort.Size))
    }

    @Test fun `each view opens its own way, and keeps what is changed apart from folders`() {
        val settings = ExplorerSettings()
        assertEquals(FilesView.Grid, settings.look(ExplorerView.Category(FileCategory.Images, ScanScope.All)).view)
        assertEquals(FilesLook(sort = FilesSort.Size), settings.look(ExplorerView.Category(FileCategory.Code, ScanScope.Shared)))
        assertEquals(FilesSort.Date, settings.look(ExplorerView.Trash).sort)
        val changed = settings.with(ExplorerView.Trash, FilesLook(FilesView.Compact, FilesSort.Name, false))
        assertEquals(FilesView.Compact, changed.look(ExplorerView.Trash).view)
        assertEquals(FilesLook(), changed.look(null))
        assertEquals(FilesView.Grid, changed.with(null, FilesLook(FilesView.Grid)).look(null).view)
        // A category keeps its look wherever Manage storage looked.
        val images = changed.with(ExplorerView.Category(FileCategory.Images, ScanScope.All), FilesLook(FilesView.Compact))
        assertEquals(FilesView.Compact, images.look(ExplorerView.Category(FileCategory.Images, ScanScope.Private)).view)
    }

    @Test fun `a look is kept as text and read back, anything else reading as no choice`() {
        val look = FilesLook(FilesView.Grid, FilesSort.Size, descending = false)
        assertEquals("Grid,Size,asc", look.encoded)
        assertEquals(look, FilesLook.decode(look.encoded))
        FilesView.entries.forEach { view -> FilesSort.entries.forEach { sort -> listOf(true, false).forEach { descending ->
            FilesLook(view, sort, descending).let { assertEquals(it, FilesLook.decode(it.encoded)) }
        } } }
        listOf("", "Grid,Size", "Grid,Size,up", "Tiles,Size,asc", "Grid,Colour,desc", "Grid,Size,asc,more").forEach { assertNull(it, FilesLook.decode(it)) }
    }

    @Test fun `hidden names start with a dot`() {
        assertTrue(ExplorerSettings.hidden(".env"))
        assertFalse(ExplorerSettings.hidden("env"))
    }
}
