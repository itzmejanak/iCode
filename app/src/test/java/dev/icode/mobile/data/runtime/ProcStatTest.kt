package dev.icode.mobile.data.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcStatTest {

    @Test
    fun `reads the command, parent and the terminal's foreground group`() {
        // Captured on the device: the shell of a session, while `sleep` ran.
        val stat = "12205 (bash) S 12199 12205 12199 34854 15641 4194304 36813 109700 0 14 57 363 1"
        assertEquals(ProcStat(command = "bash", parent = 12199, foregroundGroup = 15641), ProcStat.parse(stat))
    }

    @Test
    fun `a command with spaces and parentheses does not shift the fields`() {
        val stat = "4242 (my (odd) tool) R 100 4242 100 34854 4242 0 0 0"
        assertEquals(ProcStat(command = "my (odd) tool", parent = 100, foregroundGroup = 4242), ProcStat.parse(stat))
    }

    @Test
    fun `a process without a terminal has no foreground group`() {
        val stat = "8579 (de.mobile.debug) S 900 900 0 0 -1 1077936448 99213"
        assertEquals(-1, ProcStat.parse(stat)?.foregroundGroup)
    }

    @Test
    fun `malformed text is refused, not guessed`() {
        assertNull(ProcStat.parse(""))
        assertNull(ProcStat.parse("12 bash S 1 2"))
        assertNull(ProcStat.parse("12 (bash) S 1"))
        assertNull(ProcStat.parse("12 (bash) S x 2 3 4 5"))
    }
}
