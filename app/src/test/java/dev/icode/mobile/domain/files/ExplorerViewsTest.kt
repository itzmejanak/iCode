package dev.icode.mobile.domain.files

import org.junit.Assert.assertEquals
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

    @Test fun `Manage storage's views list what the scan shows them, while it is theirs`() {
        val largest = ExplorerView.Largest(ScanScope.All)
        val explorer = ExplorerState(scanned = ScannedView(largest, listOf(notes)))
        assertEquals(listOf(notes), ExplorerViews.rows(largest, explorer))
        assertEquals(emptyList<BrowsedItem>(), ExplorerViews.rows(ExplorerView.Largest(ScanScope.Private), explorer))
    }

    @Test fun `Duplicates lists set by set, under how many copies and their size, its order its own`() {
        val older = notes.copy(location = "/a/notes.txt")
        val newer = notes.copy(location = "/b/notes.txt")
        val lone = notes.copy(location = "/c/notes.txt")
        val duplicates = ExplorerView.Duplicates(ScanScope.All)
        val groups = listOf(DuplicateGroup(1024, listOf(older.location, newer.location)), DuplicateGroup(1024, listOf(lone.location, "/gone")))
        assertEquals(
            listOf(RowGroup("2 copies · 1.0 KB each", listOf(older, newer)), RowGroup("2 copies · 1.0 KB each", listOf(lone))),
            ExplorerViews.grouped(duplicates, listOf(newer, lone, older), FilesSort.Name, groups),
        )
        assertEquals(listOf(RowGroup(null, listOf(notes))), ExplorerViews.grouped(ExplorerView.Recent, listOf(notes), FilesSort.Name, groups))
        assertEquals(false, ExplorerViews.sortable(duplicates))
        assertEquals(true, ExplorerViews.sortable(ExplorerView.Trash))
    }
}
