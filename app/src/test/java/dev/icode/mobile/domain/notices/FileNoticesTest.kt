package dev.icode.mobile.domain.notices

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.files.FileStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
