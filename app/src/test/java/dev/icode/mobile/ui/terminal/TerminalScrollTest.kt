package dev.icode.mobile.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** docs/dock-navigation-plan.md D5 and J2: a session left scrolled back comes back to the same lines. */
class TerminalScrollTest {
    @Test fun `at the latest output nothing is kept, so it follows what comes`() {
        assertNull(TerminalScroll.keptPlace(topRow = 0, transcriptRows = 500))
    }

    @Test fun `50 lines back comes back 50 lines back`() {
        val place = TerminalScroll.keptPlace(topRow = -50, transcriptRows = 500)
        assertEquals(450, place)
        assertEquals(-50, TerminalScroll.topRowFor(450, transcriptRows = 500))
    }

    @Test fun `lines that arrived meanwhile do not move it off the lines it showed`() {
        val place = TerminalScroll.keptPlace(topRow = -50, transcriptRows = 500)!!
        // 30 lines printed while another tool was on screen: the same lines are now 80 back.
        assertEquals(-80, TerminalScroll.topRowFor(place, transcriptRows = 530))
    }

    @Test fun `it stays within what the session holds`() {
        assertEquals(-100, TerminalScroll.topRowFor(place = -20, transcriptRows = 100))
        assertEquals(0, TerminalScroll.topRowFor(place = 900, transcriptRows = 100))
    }
}
