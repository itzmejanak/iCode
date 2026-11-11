package dev.icode.mobile.domain.notices

import dev.icode.mobile.data.workspace.ICodeStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EditorNoticesTest {
    private val open = ICodeStateHolder.emptySnapshot().copy(activeWorkspace = "phone", activeWorkspaceLocation = "/w/phone")

    @Test fun `a save names the file, says where, folds when repeated, and rings nothing`() {
        val saved = EditorNotices.saved(open, "/w/phone/src/notes.md")
        assertEquals("Saved notes.md", saved.title)
        assertEquals("phone · src", saved.place?.line)
        assertEquals(saved.repeatKey, EditorNotices.saved(open, "/w/phone/src/notes.md").repeatKey)
        assertFalse(NoticeRules.rings(saved))
    }

    @Test fun `a failed save needs the user, apart from the saves that worked`() {
        val failed = EditorNotices.notSaved(open, "/w/phone/src/notes.md")
        assertEquals("Couldn't save notes.md", failed.title)
        assertEquals(NoticeKeep.NeedsYou, failed.keep)
        assertFalse(failed.repeatKey == EditorNotices.saved(open, "/w/phone/src/notes.md").repeatKey)
    }

    @Test fun `a file deleted outside iCode is said plainly, with what to do and Open`() {
        val gone = EditorNotices.deletedOutside(open, "/w/phone/notes.md")
        assertEquals("notes.md was deleted outside iCode", gone.title)
        assertEquals("Its text stays open in the editor. Save it to keep it.", gone.detail)
        assertEquals(listOf(NoticeAction.OpenFile("/w/phone/notes.md")), gone.actions)
    }
}
