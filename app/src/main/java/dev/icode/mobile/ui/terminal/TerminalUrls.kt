package dev.icode.mobile.ui.terminal

import com.termux.terminal.TerminalBuffer
import dev.icode.mobile.domain.UrlDetector

/**
 * Finds the URL under a touched cell of the terminal screen.
 *
 * A URL longer than the screen is wide continues on the next row, so the
 * logical line is rebuilt from the rows the terminal marked as wrapped. Cells
 * and characters are mapped through the buffer's own text extraction, which
 * knows about wide characters: a cell index is not a string index.
 */
internal object TerminalUrls {

    /** [row] is an external row: negative in the scrollback, 0 at the top of the screen. */
    fun at(screen: TerminalBuffer, columns: Int, rows: Int, column: Int, row: Int): String? {
        val top = -screen.activeTranscriptRows
        if (row !in top until rows || column !in 0 until columns) return null
        // A blank cell is never part of a URL, even right after one.
        if (screen.getSelectedText(column, row, column, row).isBlank()) return null

        var start = row
        while (start > top && screen.getLineWrap(start - 1)) start--
        var end = row
        while (end < rows - 1 && screen.getLineWrap(end)) end++

        val line = screen.getSelectedText(0, start, columns - 1, end, true, false)
        val before = if (row > start) screen.getSelectedText(0, start, columns - 1, row - 1, true, false).length else 0
        val throughTouch = screen.getSelectedText(0, row, column, row, true, false)
        return UrlDetector.at(line, before + throughTouch.length - 1)
    }
}
