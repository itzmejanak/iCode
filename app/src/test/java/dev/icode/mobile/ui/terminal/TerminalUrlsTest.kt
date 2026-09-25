package dev.icode.mobile.ui.terminal

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs Termux's real emulator: the rows, wraps and cells are its own. */
class TerminalUrlsTest {

    private fun screen(columns: Int, text: String): TerminalEmulator {
        val emulator = TerminalEmulator(NoOutput, columns, ROWS, CELL_PX, CELL_PX, TRANSCRIPT_ROWS, null)
        val bytes = text.toByteArray(Charsets.UTF_8)
        emulator.append(bytes, bytes.size)
        return emulator
    }

    private fun TerminalEmulator.urlAt(column: Int, row: Int) =
        TerminalUrls.at(screen, mColumns, mRows, column, row)

    @Test
    fun `finds the URL under the touched cell`() {
        val emulator = screen(40, "ready on http://localhost:3000 now")
        assertEquals("http://localhost:3000", emulator.urlAt(column = 12, row = 0))
    }

    @Test
    fun `a URL wrapped onto the next row is found whole from either row`() {
        val url = "https://example.com/a/very/long/path/that/wraps"
        val emulator = screen(20, "go $url")
        assertEquals(url, emulator.urlAt(column = 5, row = 0))
        assertEquals(url, emulator.urlAt(column = 3, row = 1))
        assertEquals(url, emulator.urlAt(column = 2, row = 2))
    }

    @Test
    fun `wide characters before a URL do not shift it`() {
        // Each of these takes two cells but is one character.
        val emulator = screen(40, "界界 http://wide.test")
        // Cells: 界(0-1) 界(2-3) space(4) h(5)…
        assertEquals("http://wide.test", emulator.urlAt(column = 5, row = 0))
        assertNull(emulator.urlAt(column = 1, row = 0))
    }

    @Test
    fun `a blank cell right after a URL opens nothing`() {
        val emulator = screen(40, "http://a.test  tail")
        assertNull(emulator.urlAt(column = 14, row = 0))
    }

    @Test
    fun `text without a URL finds nothing`() {
        val emulator = screen(40, "no links here")
        assertNull(emulator.urlAt(column = 3, row = 0))
    }

    @Test
    fun `a touch outside the screen finds nothing`() {
        val emulator = screen(40, "http://a.test")
        assertNull(emulator.urlAt(column = 45, row = 0))
        assertNull(emulator.urlAt(column = 0, row = ROWS))
    }

    private object NoOutput : TerminalOutput() {
        override fun write(data: ByteArray?, offset: Int, count: Int) = Unit
        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
        override fun onCopyTextToClipboard(text: String?) = Unit
        override fun onPasteTextFromClipboard() = Unit
        override fun onBell() = Unit
        override fun onColorsChanged() = Unit
    }

    private companion object {
        const val ROWS = 6
        const val CELL_PX = 10
        const val TRANSCRIPT_ROWS = 100
    }
}
