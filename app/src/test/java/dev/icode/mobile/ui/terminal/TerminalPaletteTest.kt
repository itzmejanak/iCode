package dev.icode.mobile.ui.terminal

import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.withAccent
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalPaletteTest {
    @Test fun `the cursor slot, which the prompt draws with, is the accent in effect`() {
        assertEquals("#FF6B2C", TerminalPalette.schemeOf(IcodePalette.Dark).getProperty("cursor"))
        assertEquals("#B4470C", TerminalPalette.schemeOf(IcodePalette.Light).getProperty("cursor"))
        assertEquals("#B67CFF", TerminalPalette.schemeOf(IcodePalette.Dark.withAccent(0xFFB67CFF.toInt())).getProperty("cursor"))
    }
}
