package dev.icode.mobile.designsystem.palettes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.designsystem.ThemeVariants
import dev.icode.mobile.designsystem.VariantGroup
import dev.icode.mobile.domain.AccentColorRules
import dev.icode.mobile.domain.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/theme-variants-plan.md D3, D4 and D5: how a ported theme becomes a palette. */
class ThemeSeedTest {
    private val seeded = listOf(
        PaletteKind.Dark to DarkSeeds, PaletteKind.Light to LightSeeds, PaletteKind.Amoled to AmoledSeeds,
    ).flatMap { (kind, seeds) -> seeds.map { seed -> Triple(kind, seed, seed.palette(kind)) } }

    @Test fun `each theme offers what the owner chose, 16 Dark, 13 Light, and every Dark variant on black too`() {
        assertEquals(16, ThemeVariants.of(AppTheme.Dark).size)
        assertEquals(13, ThemeVariants.of(AppTheme.Light).size)
        assertEquals(18, ThemeVariants.of(AppTheme.Amoled).size)
        val amoled = ThemeVariants.of(AppTheme.Amoled).map { it.id }.toSet()
        ThemeVariants.of(AppTheme.Dark).forEach { assertTrue(it.id, it.id in amoled) }
        listOf("catppuccin_latte", "gruvbox_light", "rose_pine_dawn", "tokyo_night_day", "solarized_light").forEach { id ->
            assertEquals(id, VariantGroup.Classic, ThemeVariants.find(AppTheme.Light, id).group)
        }
        listOf("catppuccin_mocha", "tokyo_night", "rose_pine", "gruvbox_dark", "nord", "dracula", "solarized_dark").forEach { id ->
            assertEquals(id, VariantGroup.Classic, ThemeVariants.find(AppTheme.Dark, id).group)
            assertEquals(id, VariantGroup.Classic, ThemeVariants.find(AppTheme.Amoled, id).group)
        }
    }

    @Test fun `a seed's colour is kept, unless it is under the floor iCode's own palettes meet`() {
        seeded.forEach { (kind, seed, palette) ->
            val deep = palette.backgroundDeep
            fun kept(name: String, given: Color, drawn: Color, behind: List<Color>, floor: Double) {
                if (drawn == given) return
                val under = behind.any { contrast(given, it) < floor }
                assertTrue("$kind ${seed.id} $name moved though it read", under)
                assertTrue("$kind ${seed.id} $name still under its floor", behind.all { contrast(drawn, it) >= floor - EPSILON })
            }
            val surfaces = listOf(palette.background, palette.surface)
            val all = surfaces + listOf(palette.surfaceElevated, deep, palette.editorLineHighlight)
            assertEquals(seed.page, palette.background)
            assertEquals(seed.card, palette.surface)
            assertEquals(seed.raised, palette.surfaceElevated)
            seed.editor?.let { assertEquals(it, deep) }
            kept("text", seed.text, palette.textPrimary, all, 4.5)
            kept("secondary", seed.secondary, palette.textSecondary, all, 4.5)
            kept("green", seed.green, palette.green, surfaces, 4.5)
            kept("amber", seed.amber, palette.amber, surfaces, 4.5)
            kept("blue", seed.blue, palette.blue, surfaces, 4.5)
            kept("error", seed.error, palette.error, all - palette.surfaceElevated, 4.5)
        }
    }

    @Test fun `what a seed does not give keeps its family's own step`() {
        seeded.forEach { (kind, seed, palette) ->
            val family = family(kind)
            fun step(name: String, drawn: Double, wanted: Double) = assertTrue("$kind ${seed.id} $name %.3f < %.3f".format(drawn, wanted), drawn >= wanted - EPSILON)
            step("track", contrast(palette.surfaceTrack, palette.surfaceElevated), contrast(family.surfaceTrack, family.surfaceElevated))
            step("outline", contrast(palette.outline, palette.background), contrast(family.outline, family.background))
            step("muted", contrast(palette.textMuted, palette.background), minOf(contrast(family.textMuted, family.background), contrast(palette.textSecondary, palette.background)))
            assertTrue("$kind ${seed.id} muted is quieter than secondary", contrast(palette.textMuted, palette.background) <= contrast(palette.textSecondary, palette.background) + EPSILON)
            if (seed.editor == null) step("editor", contrast(palette.backgroundDeep, palette.background), contrast(family.backgroundDeep, family.background))
            assertEquals(Color(AccentColorRules.onColor(palette.accent.toArgb())), palette.onAccent)
            assertEquals(16, palette.terminalAnsi.size)
        }
    }

    @Test fun `a Light variant draws a light editor, and a dark one a dark editor`() {
        seeded.forEach { (kind, seed, palette) ->
            val light = palette.backgroundDeep.luminance() > 0.5f
            assertEquals("$kind ${seed.id}", kind == PaletteKind.Light, light)
        }
    }

    @Test fun `on black, a Dark variant keeps its own colours and takes AMOLED's steps`() {
        val amoled = IcodePalette.Amoled
        DarkSeeds.forEach { seed ->
            val dark = seed.palette(PaletteKind.Dark)
            val black = seed.onBlack()
            assertEquals(seed.id, PaletteKind.Amoled, black.kind)
            assertEquals(seed.id, Color.Black, black.background)
            assertEquals(seed.id, Color.Black, black.backgroundDeep)
            listOf(
                "card" to (black.surface to amoled.surface),
                "raised" to (black.surfaceElevated to amoled.surfaceElevated),
            ).forEach { (name, pair) ->
                val (drawn, own) = pair
                val wanted = contrast(own, Color.Black)
                val ratio = contrast(drawn, Color.Black)
                assertTrue("${seed.id} $name %.3f".format(ratio), ratio >= wanted - EPSILON && ratio <= wanted + STEP_TOLERANCE)
            }
            assertEquals(seed.id, seed.syntax, black.syntax)
            // Light colours read better on black than on the Dark page; each is the Dark variant's own where it read there.
            assertEquals(seed.id, dark.accent, black.accent)
            assertEquals(seed.id, dark.textPrimary, black.textPrimary)
        }
    }

    @Test fun `the classics carry their projects' own terminal colours`() {
        (DarkSeeds + LightSeeds).filter { it.classic }.forEach { seed ->
            assertTrue(seed.id, seed.ansi?.size == 16)
        }
    }

    private fun family(kind: PaletteKind) = when (kind) {
        PaletteKind.Dark -> IcodePalette.Dark
        PaletteKind.Light -> IcodePalette.Light
        PaletteKind.Amoled -> IcodePalette.Amoled
    }

    private fun contrast(first: Color, second: Color) = AccentColorRules.contrastRatio(first.toArgb(), second.toArgb())

    private companion object {
        const val EPSILON = 1e-9
        /** The finest lightness step lands within this of a wanted ratio. */
        const val STEP_TOLERANCE = 0.02
    }
}
