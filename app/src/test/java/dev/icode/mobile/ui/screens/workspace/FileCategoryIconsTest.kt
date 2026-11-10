package dev.icode.mobile.ui.screens.workspace

import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.Oklch
import dev.icode.mobile.designsystem.everyPalette
import dev.icode.mobile.domain.AccentColorRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Manage storage's categories in Seti's colours, each at least 3 : 1 on every theme (docs/explorer-gaps-plan.md §3.10). */
class FileCategoryIconsTest {
    @Test fun `every Seti colour is at least 3 to 1 on everything it is drawn on, on every theme`() {
        everyPalette.forEach { palette ->
            SetiColour.entries.forEach { seti ->
                val colour = palette.setiColor(seti).toArgb()
                listOf(palette.background, palette.surface, palette.surfaceElevated).forEach { behind ->
                    val ratio = AccentColorRules.contrastRatio(colour, behind.toArgb())
                    assertTrue("${palette.kind} $seti %.2f".format(ratio), ratio >= 3.0)
                }
            }
        }
    }

    @Test fun `Seti's own colour where it reads, and only its lightness moved where it does not`() {
        val chromatic = SetiColour.entries - SetiColour.Grey
        listOf(IcodePalette.Dark, IcodePalette.Amoled).forEach { palette ->
            chromatic.forEach { seti -> assertEquals("${palette.kind} $seti", seti.dark, palette.setiColor(seti).toArgb() and 0xFFFFFF) }
        }
        val moved = setOf(SetiColour.Yellow, SetiColour.Green)
        (chromatic - moved).forEach { seti -> assertEquals("Light $seti", seti.light, IcodePalette.Light.setiColor(seti).toArgb() and 0xFFFFFF) }
        moved.forEach { seti ->
            val drawn = IcodePalette.Light.setiColor(seti).toArgb() and 0xFFFFFF
            assertTrue("Light $seti moved", drawn != seti.light)
            assertEquals("Light $seti hue", Oklch.hue(seti.light!!), Oklch.hue(drawn), 2.0)
        }
        assertEquals(IcodePalette.Dark.textSecondary, IcodePalette.Dark.setiColor(SetiColour.Grey))
    }
}
