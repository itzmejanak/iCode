package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyRowsTest {
    private val row = listOf(TerminalExtraKey.Ctrl, TerminalExtraKey.Escape, TerminalExtraKey.Up)

    @Test fun `a key taken out leaves the others in their order, and one added goes at the end`() {
        assertEquals(listOf(TerminalExtraKey.Ctrl, TerminalExtraKey.Up), KeyRows.toggled(row, TerminalExtraKey.Escape))
        assertEquals(row + TerminalExtraKey.Tab, KeyRows.toggled(row, TerminalExtraKey.Tab))
    }

    @Test fun `the keys left out are listed in the built-in order`() {
        assertEquals(
            listOf(TerminalExtraKey.Tab, TerminalExtraKey.Alt, TerminalExtraKey.Left, TerminalExtraKey.Down, TerminalExtraKey.Right),
            KeyRows.others(TerminalExtraKey.entries, row),
        )
        assertEquals(emptyList<EditorKey>(), KeyRows.others(EditorKey.entries, EditorKey.entries))
    }
}
