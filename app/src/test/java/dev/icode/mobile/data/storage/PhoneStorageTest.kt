package dev.icode.mobile.data.storage

import dev.icode.mobile.domain.files.BrowsedItem
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhoneStorageTest {
    @get:Rule val temp = TemporaryFolder()

    private val root by lazy { temp.newFolder("emulated", "0").canonicalFile }
    private val storage by lazy { PhoneStorage(root) { true } }

    private fun folder(path: String) = File(root, path).apply { mkdirs() }

    @Test fun `it lists every entry here — folders with their counts, files with their sizes, hidden ones too`() {
        folder("Projects/RevPlayer/src")
        File(root, "Projects/RevPlayer/README.md").writeText("# app")
        folder("Download")
        folder(".thumbnails")
        File(root, "notes.txt").writeText("a file")
        File(root, "notes.txt").setLastModified(1_600_000_000_000L)
        val top = storage.list(root.path, showHidden = true).associateBy { it.name }
        assertEquals(setOf("Projects", "Download", ".thumbnails", "notes.txt"), top.keys)
        assertEquals(BrowsedItem("notes.txt", File(root, "notes.txt").path, folder = false, bytes = 6, changedAt = 1_600_000_000_000L), top["notes.txt"])
        assertEquals(0, top.getValue("Download").entries)
        assertEquals(listOf(2), storage.list(File(root, "Projects").path, showHidden = true).map { it.entries })
        assertEquals(setOf("Projects", "Download", "notes.txt"), storage.list(root.path, showHidden = false).map { it.name }.toSet())
    }

    @Test fun `a link is listed as what it is, never followed`() {
        val outside = temp.newFolder("outside").apply { File(this, "secret").writeText("x") }
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())
        val link = storage.list(root.path, showHidden = true).single()
        assertEquals("escape", link.name)
        assertFalse(link.folder)
        assertNull(link.entries)
    }

    @Test fun `only phone storage is phone storage, as written and as a folder`() {
        val outside = temp.newFolder("outside")
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())
        assertTrue(storage.contains(root.path))
        assertTrue(storage.contains(File(root, "Download/a.txt").path))
        assertFalse(storage.contains(root.path + "-other"))
        assertFalse(storage.contains(outside.path))
        assertNull(storage.folder(outside.path))
        assertNull(storage.folder(File(root, "escape").path))
        assertNull(storage.folder(File(root, "../outside").path))
        assertEquals(root, storage.folder(root.path))
    }

    @Test fun `a folder gone since is said, not listed`() {
        assertTrue(runCatching { storage.list(File(root, "gone").path, showHidden = true) }.isFailure)
    }
}
