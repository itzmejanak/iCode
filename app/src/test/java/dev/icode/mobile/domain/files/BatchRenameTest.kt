package dev.icode.mobile.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One rule renaming many (docs/workspaces-and-files.md). */
class BatchRenameTest {
    private val beach = RenameSource("/p/IMG_001.jpeg", "IMG_001.jpeg", folder = false)
    private val park = RenameSource("/p/IMG_002.JPEG", "IMG_002.JPEG", folder = false)
    private val notes = RenameSource("/p/notes", "notes", folder = true)

    private fun names(rule: RenameRule, vararg items: RenameSource, taken: Set<String> = emptySet(), ignoreCase: Boolean = false) =
        BatchRename.preview(items.toList(), rule, taken, ignoreCase).map { it.to }

    @Test fun `replace works on a file's name before its extension, and on a folder's whole name`() {
        assertEquals(listOf("Photo_001.jpeg", "Photo_002.JPEG", "notes"), names(RenameRule.Replace("img", "Photo"), beach, park, notes))
        assertEquals(listOf("IMG_001.jpeg"), names(RenameRule.Replace("img", "Photo", matchCase = true), beach))
        assertEquals(listOf("IMG_001.jpeg"), names(RenameRule.Replace("", "x"), beach))
    }

    @Test fun `add goes before the name or after it, before the extension`() {
        assertEquals(listOf("2024 IMG_001.jpeg", "2024 notes"), names(RenameRule.Add("2024 ", atStart = true), beach, notes))
        assertEquals(listOf("IMG_001-edit.jpeg", "notes-edit"), names(RenameRule.Add("-edit", atStart = false), beach, notes))
    }

    @Test fun `numbers follow the order given, padded to the widest, each file keeping its extension`() {
        val items = (1..10).map { RenameSource("/p/$it.png", "$it.png", folder = false) }
        val named = BatchRename.preview(items, RenameRule.Number("Beach", start = 1), emptySet(), ignoreCase = false).map { it.to }
        assertEquals("Beach 01.png", named.first())
        assertEquals("Beach 10.png", named.last())
        assertEquals(listOf("7", "8"), names(RenameRule.Number("", start = 7), RenameSource("/p/a", "a", true), RenameSource("/p/b", "b", true)))
    }

    @Test fun `an extension changes or goes, and a folder keeps its name`() {
        assertEquals(listOf("IMG_001.jpg", "notes"), names(RenameRule.Extension(".jpg"), beach, notes))
        assertEquals(listOf("IMG_001"), names(RenameRule.Extension(" "), beach))
    }

    @Test fun `a name refused says why, and nothing is renamed while one is`() {
        val same = BatchRename.preview(
            listOf(RenameSource("/p/a.txt", "a.txt", false), RenameSource("/p/b.txt", "b.txt", false)),
            RenameRule.Replace("a", "b"), emptySet(), ignoreCase = false,
        )
        assertEquals("Two items would be named “b.txt”", same.first().problem)
        assertNull(same.last().problem)
        assertFalse(BatchRename.ready(same))
        val taken = BatchRename.preview(listOf(beach), RenameRule.Replace("IMG", "img", matchCase = true), setOf("img_001.jpeg"), ignoreCase = true)
        assertEquals("“img_001.jpeg” is already here", taken.single().problem)
        val bad = BatchRename.preview(listOf(beach), RenameRule.Add("a/", atStart = true), emptySet(), ignoreCase = false)
        assertEquals("File names cannot contain / or \\", bad.single().problem)
        assertTrue(BatchRename.ready(BatchRename.preview(listOf(beach, park), RenameRule.Replace("IMG_00", ""), emptySet(), false)))
    }

    @Test fun `renaming items over each other's names is allowed, nothing being left with nothing to rename`() {
        val swap = BatchRename.preview(
            listOf(RenameSource("/p/Photo 2.png", "Photo 2.png", false), RenameSource("/p/Photo 1.png", "Photo 1.png", false)),
            RenameRule.Number("Photo", start = 1), emptySet(), ignoreCase = false,
        )
        assertEquals(listOf("Photo 1.png", "Photo 2.png"), swap.map { it.to })
        assertTrue(BatchRename.ready(swap))
        assertFalse(BatchRename.ready(BatchRename.preview(listOf(beach), RenameRule.Add("", atStart = true), emptySet(), false)))
    }
}
