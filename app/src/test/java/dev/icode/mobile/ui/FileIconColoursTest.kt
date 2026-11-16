package dev.icode.mobile.ui

import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.Oklch
import dev.icode.mobile.designsystem.everyPalette
import dev.icode.mobile.domain.AccentColorRules
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every file and folder icon reads on every theme, and keeps its own colours where it does (docs/workspaces-and-files.md). */
class FileIconColoursTest {
    private val icons = File("src/main/assets/file-icons/icons").listFiles()!!.associate { it.name.removeSuffix(".svg") to it.readText() }

    private fun reads(rgb: Int, palette: IcodePalette) = listOf(palette.background, palette.surface, palette.surfaceElevated)
        .all { AccentColorRules.contrastRatio(rgb or 0xFF000000.toInt(), it.toArgb()) >= FileIconColours.MIN_CONTRAST }

    @Test fun `every icon's strongest colour is at least 3 to 1 on everything it is drawn on, on every theme`() {
        everyPalette.forEach { palette ->
            icons.forEach { (icon, svg) ->
                val colours = FileIconColours.colours(FileIconColours.drawn(svg, palette))
                if (colours.isNotEmpty()) assertTrue("${palette.kind} $icon", colours.any { reads(it, palette) })
            }
        }
    }

    @Test fun `an icon that reads is drawn in its own colours, and one that does not keeps their relations`() {
        val svg = """<svg viewBox="0 0 16 16"><path fill="#90a4ae" d="M0 0h8v8z"/><path fill="#FFCA28" d="M8 8h8v8z"/></svg>"""
        assertEquals(svg, FileIconColours.drawn(svg, IcodePalette.Dark))
        val light = FileIconColours.colours(FileIconColours.drawn(svg, IcodePalette.Light))
        assertEquals(2, light.size)
        // Both moved darker by one step: each keeps its hue.
        assertEquals(Oklch.hue(0x90a4ae), Oklch.hue(light[0]), 3.0)
        assertEquals(Oklch.hue(0xFFCA28), Oklch.hue(light[1]), 3.0)
        assertTrue(light.any { reads(it, IcodePalette.Light) })
    }

    @Test fun `ids and references are never taken for colours, and the default folder takes the accent`() {
        val svg = """<svg><linearGradient id="abc"><stop stop-color="#fff"/></linearGradient><path fill="url(#abc)" d=""/><use href="#bad"/></svg>"""
        val drawn = FileIconColours.drawn(svg, IcodePalette.Dark)
        assertTrue(drawn.contains("url(#abc)") && drawn.contains("href=\"#bad\""))
        val accent = IcodePalette.Dark.accent.toArgb() and 0xFFFFFF
        val folder = FileIconColours.colours(FileIconColours.drawn(icons.getValue("folder"), IcodePalette.Dark, accent = true))
        assertEquals(1, folder.size)
        assertEquals(Oklch.hue(accent), Oklch.hue(folder.single()), 3.0)
    }
}
