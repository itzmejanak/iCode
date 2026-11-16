package dev.icode.mobile.domain.files

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Properties' rows and the question's words (docs/workspaces-and-files.md). */
class FilePropertiesTest {
    private val changed = LocalDateTime.of(2026, 10, 28, 14, 5).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val now = changed + 2 * 60 * 60 * 1000L

    private fun rows(facts: FileFacts, name: String = "Main.kt", branch: String? = null) =
        FileProperties.rows(facts, name, "/workspace/app/$name", branch, now, ZoneOffset.UTC).associate { it.label to it.value }

    @Test fun `a file says its kind by extension, its exact size, when it changed, whether it runs`() {
        val facts = FileFacts(listOf("/w/app/Main.kt"), FactKind.File, folders = 0, modifiedAt = changed, executable = false, measured = TreeFacts(1_234_567, 0, 0))
        val rows = rows(facts)
        assertEquals(listOf("Name", "Kind", "Location", "Size", "Modified", "Executable"), rows.keys.toList())
        assertEquals("Code · .kt", rows["Kind"])
        assertEquals("1.2 MB (1,234,567 bytes)", rows["Size"])
        assertTrue(rows.getValue("Modified"), rows.getValue("Modified").endsWith("2026, 14:05 · 2h ago"))
        assertEquals("No", rows["Executable"])
        assertTrue(FileProperties.rows(facts, "Main.kt", "/workspace/app/Main.kt", null, now).single { it.label == "Location" }.path)
    }

    @Test fun `a folder is measured as it is open, and a workspace says its branch`() {
        val folder = FileFacts(listOf("/w/app"), FactKind.Workspace, folders = 1, modifiedAt = changed)
        assertEquals("Measuring…", rows(folder, "app", branch = "main")["Size"])
        assertEquals("Workspace · main", rows(folder, "app", branch = "main")["Kind"])
        assertEquals("340 MB · 1234 files, 56 folders", rows(folder.copy(measured = TreeFacts(340L * 1024 * 1024, 1234, 56)), "app")["Size"])
        assertEquals("Unknown, a folder could not be read · 3 files, 1 folder", rows(folder.copy(measured = TreeFacts(null, 3, 1)), "app")["Size"])
        assertEquals("Link to ../lib", rows(FileFacts(listOf("/w/a"), FactKind.Link, 0, linkTarget = "../lib"), "a")["Kind"])
        assertEquals("File", rows(FileFacts(listOf("/w/LICENSE"), FactKind.File, 0), "LICENSE")["Kind"])
    }

    @Test fun `many say how many, their size, and the folder they are in`() {
        val facts = FileFacts(listOf("/w/a", "/w/b", "/w/c.txt"), kind = null, folders = 2, measured = TreeFacts(2048, 10, 3))
        assertEquals(mapOf("Items" to "3 items · 2 folders, 1 file", "Size" to "2.0 KB · 10 files, 3 folders", "In" to "/workspace/app/x"), rows(facts, "x"))
    }

    @Test fun `many a view gathered from more than one folder are in none`() {
        val facts = FileFacts(listOf("/w/a.txt", "/phone/b.txt"), kind = null, folders = 0, measured = TreeFacts(10, 2, 0))
        assertEquals(listOf("Items", "Size"), FileProperties.rows(facts, "2 selected", null, null, now).map { it.label })
    }

    @Test fun `what Trash holds says where it was, when it went, and until when it is kept`() {
        val item = TrashedItem("t1", "app", "/w/app", "/workspace/app", "/trash/files/t1", StoragePlace.Private, deletedAt = changed, folder = true, bytes = 3 * 1024 * 1024, workspace = true)
        val rows = FileProperties.trashed(item, now, ZoneOffset.UTC)
        assertEquals(listOf("Name", "Kind", "Deleted from", "Size", "Deleted", "Kept until"), rows.map { it.label })
        assertEquals("Workspace", rows[1].value)
        assertTrue(rows[2].path)
        assertEquals("3.0 MB", rows[3].value)
        assertTrue(rows[4].value, rows[4].value.endsWith("2026, 14:05 · 2h ago"))
        assertEquals("27 Nov 2026, 14:05", rows[5].value)
        assertEquals("Unknown, a folder could not be read", FileProperties.trashed(item.copy(bytes = null), now, ZoneOffset.UTC)[3].value)
        assertEquals("12 B (12 bytes)", FileProperties.trashed(item.copy(folder = false, workspace = false, name = "a.txt", bytes = 12), now, ZoneOffset.UTC)[3].value)
    }

    @Test fun `the question says each side, and offers the rest while there are others`() {
        assertEquals("1.2 MB · 2h ago", ConflictSide(folder = false, bytes = 1_234_567, changedAt = changed).said(now))
        assertEquals("Folder · 2h ago", ConflictSide(folder = true, bytes = null, changedAt = changed).said(now))
        val conflict = FileConflict(1, "a.txt", "/workspace/app", ConflictSide(false, 1, changed), ConflictSide(false, 2, changed), others = 0)
        assertNull(conflict.forOthers)
        assertEquals("Do this for the next one too", conflict.copy(others = 1).forOthers)
        assertEquals("Do this for the other 4", conflict.copy(others = 4).forOthers)
    }
}
