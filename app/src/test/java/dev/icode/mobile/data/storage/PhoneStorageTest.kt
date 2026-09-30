package dev.icode.mobile.data.storage

import dev.icode.mobile.domain.FolderCrumb
import dev.icode.mobile.domain.PhoneFolder
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

    private var access = true
    private val root by lazy { temp.newFolder("emulated", "0").canonicalFile }
    private val storage by lazy { PhoneStorage(root) { access } }

    private fun folder(path: String) = File(root, path).apply { mkdirs() }

    @Test fun `with access it lists the folders here, their counts, and not hidden ones`() {
        folder("Projects/RevPlayer/src")
        File(root, "Projects/RevPlayer/README.md").writeText("# app")
        folder("Download")
        folder(".thumbnails")
        File(root, "notes.txt").writeText("a file, not a folder")
        val top = storage.browse(root.path)
        assertTrue(top.access && top.atRoot)
        assertEquals(listOf("Download", "Projects"), top.folders.map { it.name })
        assertEquals(listOf("sdcard"), top.crumbs.map { it.name })
        val projects = storage.browse(File(root, "Projects").path)
        assertEquals(listOf(PhoneFolder("RevPlayer", File(root, "Projects/RevPlayer").path, 1, 1)), projects.folders)
        assertEquals(listOf(FolderCrumb("sdcard", root.path), FolderCrumb("Projects", File(root, "Projects").path)), projects.crumbs)
        assertEquals("Projects", projects.name)
    }

    @Test fun `without access it lists nothing and waits where it was asked to open`() {
        access = false
        val waiting = storage.browse(File(root, "Projects/RevPlayer").path)
        assertFalse(waiting.access)
        assertEquals(File(root, "Projects/RevPlayer").path, waiting.currentPath)
        assertEquals("sdcard / Projects / RevPlayer", storage.displayPath(File(waiting.currentPath)))
        assertTrue(waiting.folders.isEmpty())
        assertEquals(root.path, storage.browse("/data/data/other.app").currentPath)
    }

    @Test fun `nothing outside internal storage is a folder here, even through a link`() {
        val outside = temp.newFolder("outside")
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())
        assertNull(storage.folder(outside.path))
        assertNull(storage.folder(File(root, "escape").path))
        assertNull(storage.folder(File(root, "../outside").path))
        assertEquals(root, storage.folder(root.path))
        assertTrue(storage.browse(root.path).folders.none { it.name == "escape" })
    }

    @Test fun `a folder gone since is said, not listed`() {
        assertTrue(runCatching { storage.browse(File(root, "gone").path) }.isFailure)
    }
}
