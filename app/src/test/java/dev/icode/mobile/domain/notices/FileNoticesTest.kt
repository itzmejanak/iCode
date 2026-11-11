package dev.icode.mobile.domain.notices

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.FileTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNoticesTest {
    private val open = ICodeStateHolder.emptySnapshot().copy(activeWorkspace = "phone", activeWorkspaceLocation = "/w/phone")

    @Test fun `a change is where its first step was, by the folder it was in`() {
        assertEquals("phone · src", FileNotices.placeOf(open, listOf(FileStep.Trashed("t1", "/w/phone/src/notes.md")))?.line)
        assertEquals("phone · app/src", FileNotices.placeOf(open, listOf(FileStep.Moved("/w/phone/app/src/a.kt", "/w/phone/lib/a.kt")))?.line)
        assertEquals(NoticePlace("phone", "/w/phone"), FileNotices.placeOf(open, listOf(FileStep.Made("/w/phone/a.md"))))
    }

    @Test fun `a change of nothing is nowhere`() {
        assertNull(FileNotices.placeOf(open, emptyList()))
    }

    @Test fun `a change is kept as done, with Undo, and rings no bell`() {
        val moved = FileNotices.changed("Moved notes.md to Trash", NoticePlace("phone", "/w/phone", subject = "src"))
        assertEquals(NoticeKeep.Done, moved.keep)
        assertEquals(listOf(NoticeAction.Undo("Moved notes.md to Trash")), moved.actions)
        assertFalse(NoticeRules.rings(moved))
    }

    @Test fun `what stopped short says what it could not do, and where to`() {
        assertEquals("Couldn't move notes.md to Trash", FileNotices.stopped(FileTask.Trash, "notes.md", null, null).title)
        assertEquals("Couldn't copy 2 items to assets", FileNotices.stopped(FileTask.Copy, "2 items", "assets", null).title)
        val undo = FileNotices.stopped(FileTask.Undo, "Moved notes.md to Trash", null, null)
        assertEquals("Couldn't undo the last change", undo.title)
        assertTrue(undo.detail!!.startsWith("Moved notes.md to Trash."))
        assertEquals(NoticeKeep.NeedsYou, undo.keep)
        assertEquals("Deleted 3 items for good", FileNotices.deletedForGood(listOf("a", "b", "c"), null).title)
    }
}
