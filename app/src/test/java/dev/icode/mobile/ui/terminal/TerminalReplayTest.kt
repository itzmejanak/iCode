package dev.icode.mobile.ui.terminal

import com.termux.terminal.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A replay's frame fits the rows its output fills (docs/assistant-and-mcp.md), on Termux's real
 * emulator: wraps, scrollback and the cursor are its own.
 */
class TerminalReplayTest {
    private fun rows(text: String, columns: Int = 20, rows: Int = 6): Int {
        val emulator = TerminalEmulator(NoOutput, columns, rows, 10, 10, 100, null)
        val bytes = text.toByteArray(Charsets.UTF_8)
        emulator.append(bytes, bytes.size)
        return emulator.contentRows()
    }

    @Test fun `lines that end fill their rows, and one without its end fills its own too`() {
        assertEquals(2, rows("a.txt\r\nb.txt\r\n"))
        assertEquals(2, rows("a.txt\r\nContinue? [y/N] "))
        assertEquals(3, rows("one\r\n\r\nthree\r\n"))
    }

    @Test fun `nothing printed still fills a row`() {
        assertEquals(1, rows(""))
    }

    @Test fun `a line wider than the view fills each row it wraps onto`() {
        assertEquals(3, rows("x".repeat(45) + "\r\n"))
    }

    @Test fun `output past the screen counts its scrollback`() {
        assertEquals(30, rows((1..30).joinToString("") { "line $it\r\n" }))
    }

    @Test fun `colours and a hidden cursor take no rows`() {
        assertEquals(1, rows("\u001B[32mok\u001B[0m\r\n\u001B[?25l"))
    }
}
