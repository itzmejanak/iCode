package dev.icode.mobile.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The explorer's views (docs/explorer-gaps-plan.md §3.10): what each lists, what it says it found, and how a screen keeps it. */
class ExplorerViewsTest {
    private val notes = BrowsedItem("notes.txt", "/phone/Download/notes.txt", "/storage/emulated/0/Download/notes.txt", StoragePlace.Shared, folder = false, bytes = 1024, changedAt = 2)
    private val app = TrashedItem("t1", "app", "/w/app", "/workspace/app", "/trash/files/t1", StoragePlace.Private, deletedAt = 5, folder = true, bytes = 3072, workspace = true)

    @Test fun `Recent lists its files, Trash each item where it is held, dated when deleted`() {
        val explorer = ExplorerState(recent = listOf(notes), trash = listOf(app))
        assertEquals(listOf(notes), ExplorerViews.rows(ExplorerView.Recent, explorer))
        assertEquals(
            listOf(BrowsedItem("app", "/trash/files/t1", "/workspace/app", StoragePlace.Private, folder = true, bytes = 3072, changedAt = 5)),
            ExplorerViews.rows(ExplorerView.Trash, explorer),
        )
    }

    @Test fun `a view says how much it found, Trash how long it keeps it, Manage storage's where it looked`() {
        assertEquals("1 file · 1.0 KB", ExplorerViews.found(ExplorerView.Recent, listOf(notes)))
        assertEquals("1 item · 3.0 KB · removed after 30 days", ExplorerViews.found(ExplorerView.Trash, listOf(app.row)))
        assertEquals("All · 2 files · 2.0 KB", ExplorerViews.found(ExplorerView.Largest(ScanScope.All), listOf(notes, notes)))
    }

    @Test fun `each view is kept as it is and read back, anything else not`() {
        val views = listOf(ExplorerView.Recent, ExplorerView.Trash, ExplorerView.Largest(ScanScope.Shared), ExplorerView.Duplicates(ScanScope.All)) +
            FileCategory.entries.map { ExplorerView.Category(it, ScanScope.Private) }
        views.forEach { assertEquals(it, ExplorerView.decode(it.encoded)) }
        assertEquals("category-Images:Private", ExplorerView.Category(FileCategory.Images, ScanScope.Private).encoded)
        listOf("", "recent:All", "largest", "largest:Nowhere", "category-Nothing:All").forEach { assertNull(it, ExplorerView.decode(it)) }
    }
}
