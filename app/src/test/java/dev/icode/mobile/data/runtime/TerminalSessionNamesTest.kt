package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.TerminalSessionNames
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalSessionNamesTest {
    @Test
    fun `closing the last tab reuses its display number`() {
        val names = TerminalSessionNames()

        assertEquals("bash", names.allocate("session-1"))
        assertEquals("bash 2", names.allocate("session-2"))
        assertEquals("bash 3", names.allocate("session-3"))
        assertEquals("bash 4", names.allocate("session-4"))

        names.release("session-4")
        assertEquals("bash 4", names.allocate("session-5"))
    }

    @Test
    fun `closing all tabs returns to the first display number`() {
        val names = TerminalSessionNames()

        assertEquals("bash", names.allocate("session-1"))
        assertEquals("bash 2", names.allocate("session-2"))
        names.release("session-1")
        names.release("session-2")

        assertEquals("bash", names.allocate("session-3"))
    }

    @Test
    fun `closing a middle tab leaves remaining labels stable`() {
        val names = TerminalSessionNames()

        names.allocate("session-1")
        names.allocate("session-2")
        assertEquals("bash 3", names.allocate("session-3"))
        names.release("session-2")

        assertEquals("bash 2", names.allocate("session-4"))
    }
}
