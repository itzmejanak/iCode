package dev.icode.mobile.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.withAccent
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalPaletteTest {
    /** Each build's accent is its icon's (docs/build-and-release.md): read it, never a fixed literal. */
    @Test fun `the cursor slot, which the prompt draws with, is the accent in effect`() {
        assertEquals(hex(IcodePalette.Dark.accent), TerminalPalette.schemeOf(IcodePalette.Dark).getProperty("cursor"))
        assertEquals(hex(IcodePalette.Light.accent), TerminalPalette.schemeOf(IcodePalette.Light).getProperty("cursor"))
        assertEquals("#B67CFF", TerminalPalette.schemeOf(IcodePalette.Dark.withAccent(0xFFB67CFF.toInt())).getProperty("cursor"))
    }

    /** As the palette writes it: lowercase-free six digits, the alpha dropped. */
    private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)
}
