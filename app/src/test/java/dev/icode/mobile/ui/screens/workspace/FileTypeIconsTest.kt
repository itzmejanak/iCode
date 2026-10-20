package dev.icode.mobile.ui.screens.workspace

import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.Oklch
import dev.icode.mobile.designsystem.everyPalette
import dev.icode.mobile.domain.AccentColorRules
import dev.icode.mobile.domain.FileTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypeIconsTest {
    private val types = FileTypes.rules.map { it.type } + FileTypes.PlainText

    @Test fun `every file type has its colour, and nothing else has one`() {
        assertEquals(types.map { it.id }.toSet(), fileTypeColours.keys)
    }

    @Test fun `every icon is at least 3 to 1 on everything it is drawn on, on every theme`() {
        everyPalette.forEach { palette ->
            types.forEach { type ->
                val colour = palette.fileTypeColor(type).toArgb()
                listOf(palette.background, palette.surface, palette.surfaceElevated).forEach { behind ->
                    val ratio = AccentColorRules.contrastRatio(colour, behind.toArgb())
                    assertTrue("${palette.kind} ${type.id} %.2f".format(ratio), ratio >= 3.0)
                }
            }
        }
    }

    @Test fun `each type takes Seti's colour for its language`() {
        mapOf("json" to SetiColour.Yellow, "typescript" to SetiColour.Blue, "kotlin" to SetiColour.Orange,
            "shell" to SetiColour.Green, "java" to SetiColour.Red, "yaml" to SetiColour.Purple, "sql" to SetiColour.Pink,
            "rust" to SetiColour.Grey, "plaintext" to SetiColour.Grey, "image" to SetiColour.Purple,
        ).forEach { (id, colour) -> assertEquals(id, colour, fileTypeColours[id]) }
    }

    @Test fun `Seti's own colour where it reads, and only its lightness moved where it does not`() {
        val chromatic = SetiColour.entries - SetiColour.Grey
        listOf(IcodePalette.Dark, IcodePalette.Amoled).forEach { palette ->
            chromatic.forEach { seti -> assertEquals("${palette.kind} $seti", seti.dark, colourOf(palette, seti)) }
        }
        val moved = setOf(SetiColour.Yellow, SetiColour.Green)
        (chromatic - moved).forEach { seti -> assertEquals("Light $seti", seti.light, colourOf(IcodePalette.Light, seti)) }
        moved.forEach { seti ->
            val drawn = colourOf(IcodePalette.Light, seti)
            assertTrue("Light $seti moved", drawn != seti.light)
            assertEquals("Light $seti hue", Oklch.hue(seti.light!!), Oklch.hue(drawn), 2.0)
        }
    }

    private fun colourOf(palette: IcodePalette, seti: SetiColour): Int {
        val id = fileTypeColours.entries.first { it.value == seti }.key
        return palette.fileTypeColor(types.first { it.id == id }).toArgb() and 0xFFFFFF
    }
}
