package dev.icode.mobile.data.files

import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.domain.files.FileCategory
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.ScanScope
import dev.icode.mobile.domain.files.StorageInsights
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Manage storage's scan (docs/explorer-gaps-plan.md §3.10): categories, the largest, copies, `.git` and Trash apart, and iCode's own changes followed. */
class StorageScanTest {
    @get:Rule val temp = TemporaryFolder()

    private var access = true
    private lateinit var workspaces: File
    private lateinit var phone: File
    private lateinit var trash: FileTrash
    private lateinit var scan: StorageScan

    private fun file(parent: File, name: String, text: String) = File(parent, name).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    @Before fun places() {
        workspaces = temp.newFolder("files", "workspaces").canonicalFile
        phone = temp.newFolder("emulated", "0").canonicalFile
        val storage = PhoneStorage(phone) { access }
        trash = FileTrash(File(workspaces.parentFile, "trash"), File(phone, "Android/media/dev.icode.mobile/Trash"), storage)
        scan = StorageScan(FilePlaces(workspaces, storage), trash) { 42 }
        file(workspaces, "app/src/Main.kt", "fun main() {}")
        file(workspaces, "app/.git/objects/pack", "x".repeat(700))
        file(workspaces, "app/logo.png", "png bytes")
        file(phone, "DCIM/beach.jpg", "same picture")
        file(phone, "Download/beach copy.jpg", "same picture")
        file(phone, "Download/notes.pdf", "pdf")
        file(phone, "Music/song.mp3", "music".repeat(100))
    }

    @Test fun `every file has its category, Git's history apart and in no list`() {
        val found = scan.scan(ScanScope.All, { true }) {}
        val overview = scan.overview()!!
        assertEquals(700L, overview.gitBytes)
        assertEquals(6, overview.files)
        assertEquals(3, overview.categories.single { it.category == FileCategory.Images }.files)
        assertEquals(1, overview.categories.single { it.category == FileCategory.Code }.files)
        assertEquals(500L, overview.categories.single { it.category == FileCategory.Audio }.bytes)
        assertTrue(found.files.none { "/.git/" in it.location })
        assertEquals(42L, overview.scannedAt)
    }

    @Test fun `the largest come first, and copies are found by their contents`() {
        val found = scan.scan(ScanScope.All, { true }) {}
        assertEquals("song.mp3", StorageInsights.largest(found.files).first().location.substringAfterLast('/'))
        val group = found.duplicates.single()
        assertEquals(12L, group.bytes)
        assertEquals(setOf(File(phone, "DCIM/beach.jpg").path, File(phone, "Download/beach copy.jpg").path), group.locations.toSet())
        assertEquals(12L, scan.overview()!!.duplicateBytes)
    }

    @Test fun `Trash's folders are left out, and Shared waits for access`() {
        val photo = File(phone, "DCIM/beach.jpg")
        trash.put(photo.path, bytes = 12, workspace = false)
        val found = scan.scan(ScanScope.Shared, { true }) {}
        assertTrue(found.files.none { it.location.contains("/Trash/") })
        assertTrue(found.duplicates.isEmpty())
        access = false
        val unread = scan.scan(ScanScope.All, { true }) {}
        assertTrue(unread.sharedUnread)
        assertTrue(unread.files.all { it.location.startsWith(workspaces.path) })
    }

    @Test fun `iCode's own delete and rename change what was found, and what arrives is looked at`() {
        scan.scan(ScanScope.All, { true }) {}
        val copy = File(phone, "Download/beach copy.jpg").path
        val renamed = File(phone, "Download/beach.txt").path
        File(copy).renameTo(File(renamed))
        scan.follow(listOf(FileStep.Moved(copy, renamed)))
        assertEquals(FileCategory.Documents, scan.found!!.files.single { it.location == renamed }.category)
        assertTrue(renamed in scan.found!!.duplicates.single().locations)
        scan.follow(listOf(FileStep.Trashed("t1", File(phone, "DCIM").path)))
        assertTrue(scan.found!!.duplicates.isEmpty())
        assertFalse(scan.found!!.files.any { it.location.startsWith(File(phone, "DCIM").path) })
        val made = file(workspaces, "app/lib/Util.kt", "object Util")
        scan.follow(listOf(FileStep.Made(File(workspaces, "app/lib").path)))
        assertTrue(scan.found!!.files.any { it.location == made.path })
        scan.follow(listOf(FileStep.Trashed("t2", File(workspaces, "app").path)))
        assertEquals(0L, scan.overview()!!.gitBytes)
    }

    @Test fun `a scan told to stop stops`() {
        repeat(3) { file(workspaces, "many/f$it.txt", "x") }
        var asked = 0
        val stopped = runCatching { scan.scan(ScanScope.Private, { ++asked < 2 }) {} }
        assertTrue(stopped.isFailure)
        assertEquals(null, scan.found)
        assertTrue(Files.exists(File(workspaces, "many").toPath()))
    }
}
