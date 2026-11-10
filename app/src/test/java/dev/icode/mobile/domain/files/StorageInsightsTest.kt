package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.FileTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Manage storage's sums, and how its findings follow iCode's own changes (docs/explorer-gaps-plan.md §3.10). */
class StorageInsightsTest {
    private val photo = ScannedFile("/s/DCIM/a.jpg", 10, changedAt = 1, FileTypes.Image)
    private val copy = ScannedFile("/s/Download/a.jpg", 10, changedAt = 2, FileTypes.Image)
    private val song = ScannedFile("/s/Music/b.mp3", 30, changedAt = 3, FileTypes.Audio)

    @Test fun `every category is counted, Git's history added to the total, and the extra copies' space said`() {
        val group = DuplicateGroup(10, listOf(photo.location, copy.location))
        val overview = StorageInsights.overview(ScanScope.All, listOf(photo, copy, song), gitBytes = 5, listOf(group), scannedAt = 7, sharedUnread = false)
        assertEquals(FileCategory.entries.size, overview.categories.size)
        assertEquals(CategoryShare(FileCategory.Images, 2, 20), overview.categories.first())
        assertEquals(3, overview.files)
        assertEquals(55L, overview.bytes)
        assertEquals(50L, overview.largestBytes)
        assertEquals(10L, overview.duplicateBytes)
        assertEquals(listOf(copy.location), StorageInsights.extraCopies(listOf(group)))
    }

    @Test fun `only files of one size, none empty, may be copies`() {
        assertEquals(listOf(listOf(photo, copy)), StorageInsights.sameSize(listOf(photo, copy, song, photo.copy(location = "/e", bytes = 0), song.copy(location = "/x", bytes = 0))))
    }

    @Test fun `a file follows a move, takes its new name's category, and goes with what went`() {
        val steps = listOf(FileStep.Moved("/s/Download", "/s/Old"), FileStep.Moved("/s/Old/a.jpg", "/s/Old/a.txt"))
        // A new extension is a new kind, by the name; a move that keeps it keeps what was found (a `.ts` read as a video).
        val renamed = StorageInsights.after(copy, steps)!!
        assertEquals(copy.copy(location = "/s/Old/a.txt", type = FileTypes.PlainText), renamed)
        assertEquals(FileCategory.Documents, renamed.category)
        val recording = ScannedFile("/s/TV/news.ts", 900, 4, FileTypes.Video)
        assertEquals(FileCategory.Videos, StorageInsights.after(recording, listOf(FileStep.Moved("/s/TV", "/s/Old")))!!.category)
        assertEquals(FileCategory.Code, recording.copy(type = FileTypes.of("news.ts")).category)
        assertEquals(photo, StorageInsights.after(photo, steps))
        assertNull(StorageInsights.after(song, listOf(FileStep.Trashed("t1", "/s/Music"))))
        assertNull(StorageInsights.after("/s/Music/b.mp3", listOf(FileStep.Removed("/s/Music/b.mp3"))))
        assertNull(StorageInsights.after(DuplicateGroup(10, listOf(photo.location, copy.location)), listOf(FileStep.Trashed("t2", photo.location))))
    }

    @Test fun `each of the scan's views lists its own files, the others none of them`() {
        val picture = ScannedFile("/w/a/photo.png", 300, 1, FileTypes.Image)
        val main = ScannedFile("/w/a/Main.kt", 100, 2, FileTypes.of("Main.kt"))
        val twin = ScannedFile("/w/b/Main.kt", 100, 3, FileTypes.of("Main.kt"))
        val files = listOf(main, picture, twin)
        val groups = listOf(DuplicateGroup(100, listOf(main.location, twin.location)))
        assertEquals(listOf(main, twin), StorageInsights.listed(ExplorerView.Category(FileCategory.Code, ScanScope.All), files, groups))
        assertEquals(listOf(picture, main, twin), StorageInsights.listed(ExplorerView.Largest(ScanScope.All), files, groups))
        assertEquals(listOf(main, twin), StorageInsights.listed(ExplorerView.Duplicates(ScanScope.All), files, groups))
        assertEquals(null, StorageInsights.listed(ExplorerView.Trash, files, groups))
    }
}
