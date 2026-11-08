package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.Color
import dev.icode.mobile.domain.AccentColorRules
import dev.icode.mobile.domain.AppEnvironment
import dev.icode.mobile.domain.AppTheme
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IcodePaletteTest {
    @Test fun `a chosen accent replaces only the accent and what reads on it`() {
        val indigo = 0xFF1A237E.toInt()
        val custom = IcodePalette.Light.withAccent(indigo)
        assertEquals(PaletteKind.Light, custom.kind)
        assertEquals(Color(indigo), custom.accent)
        assertEquals(Color.White, custom.onAccent)
        assertEquals(IcodePalette.Light.copy(accent = custom.accent, onAccent = custom.onAccent), custom)
    }

    @Test fun `no choice keeps the theme's own accent`() {
        assertSame(IcodePalette.Dark, IcodePalette.Dark.withAccent(null))
    }

    @Test fun `a translucent choice is drawn opaque`() {
        assertEquals(Color(0xFF4FB3FF), IcodePalette.Dark.withAccent(0x404FB3FF).accent)
    }

    @Test fun `each palette draws its theme's own accent, this build's environment's`() {
        assertEquals(Color(AccentColorRules.themeAccent(AppTheme.Dark, BuildEnvironment)), IcodePalette.Dark.accent)
        assertEquals(Color(AccentColorRules.themeAccent(AppTheme.Light, BuildEnvironment)), IcodePalette.Light.accent)
        assertEquals(Color(AccentColorRules.themeAccent(AppTheme.Amoled, BuildEnvironment)), IcodePalette.Amoled.accent)
    }

    @Test fun `every environment's accent reads on every theme, and so does what is drawn on it`() {
        AppEnvironment.entries.forEach { environment ->
            AppTheme.entries.forEach { theme ->
                val palette = IcodePalette.of(theme, borderLines = true)
                val accent = AccentColorRules.themeAccent(theme, environment)
                assertTrue("$environment on $theme", AccentColorRules.contrastRatio(accent, palette.background.toArgb()) >= AccentColorRules.MIN_CONTRAST)
                assertTrue("on $environment's $theme accent", AccentColorRules.contrastRatio(palette.onAccent.toArgb(), accent) >= 4.5)
            }
        }
    }

    @Test fun `with border lines each theme is its own palette`() {
        assertSame(IcodePalette.Dark, IcodePalette.of(AppTheme.Dark, borderLines = true))
        assertSame(IcodePalette.Light, IcodePalette.of(AppTheme.Light, borderLines = true))
        assertSame(IcodePalette.Amoled, IcodePalette.of(AppTheme.Amoled, borderLines = true))
    }

    @Test fun `without border lines a card stands at least 1_12 to 1 from its page, in every theme`() {
        AppTheme.entries.forEach { theme ->
            val drawn = IcodePalette.of(theme, borderLines = false)
            assertTrue("$theme", contrast(drawn.surface, drawn.background) >= 1.12)
        }
    }

    @Test fun `without border lines every other surface keeps its own step from what it sits on`() {
        AppTheme.entries.forEach { theme ->
            val before = IcodePalette.of(theme, borderLines = true)
            val drawn = IcodePalette.of(theme, borderLines = false)
            fun keeps(what: String, step: (IcodePalette) -> Double) =
                assertTrue("$theme $what", step(drawn) >= step(before) - EPSILON)
            keeps("chip on card") { contrast(it.surfaceElevated, it.surface) }
            keeps("chip on page") { contrast(it.surfaceElevated, it.background) }
            keeps("track on chip") { contrast(it.surfaceTrack, it.surfaceElevated) }
            keeps("track on card") { contrast(it.surfaceTrack, it.surface) }
            keeps("deep page") { contrast(it.backgroundDeep, it.background) }
        }
    }

    @Test fun `without border lines nothing drawn on a surface reads worse, unless it meets WCAG AA`() {
        AppTheme.entries.forEach { theme ->
            val before = IcodePalette.of(theme, borderLines = true)
            val drawn = IcodePalette.of(theme, borderLines = false)
            val surfaces = listOf<(IcodePalette) -> Color>({ it.background }, { it.backgroundDeep }, { it.surface }, { it.surfaceElevated })
            val colours = mapOf<String, Pair<(IcodePalette) -> Color, Double>>(
                "text" to ({ p: IcodePalette -> p.textPrimary } to 4.5), "secondary" to ({ p: IcodePalette -> p.textSecondary } to 4.5),
                "muted" to ({ p: IcodePalette -> p.textMuted } to 4.5), "amber" to ({ p: IcodePalette -> p.amber } to 4.5),
                "green" to ({ p: IcodePalette -> p.green } to 4.5), "blue" to ({ p: IcodePalette -> p.blue } to 4.5),
                "error" to ({ p: IcodePalette -> p.error } to 4.5), "outline" to ({ p: IcodePalette -> p.outline } to 3.0),
            )
            colours.forEach { (name, rule) ->
                val (colour, enough) = rule
                surfaces.forEachIndexed { index, surface ->
                    val wanted = minOf(contrast(colour(before), surface(before)), enough)
                    assertTrue("$theme $name on surface $index", contrast(colour(drawn), surface(drawn)) >= wanted - EPSILON)
                }
            }
            before.terminalAnsi.indices.forEach { index ->
                val wanted = minOf(contrast(before.terminalAnsi[index], before.backgroundDeep), 4.5)
                assertTrue("$theme terminal colour $index", contrast(drawn.terminalAnsi[index], drawn.backgroundDeep) >= wanted - EPSILON)
            }
        }
    }

    @Test fun `without border lines the accent and what is drawn on it are the theme's`() {
        AppTheme.entries.forEach { theme ->
            val before = IcodePalette.of(theme, borderLines = true)
            val drawn = IcodePalette.of(theme, borderLines = false)
            assertEquals(before.accent, drawn.accent)
            assertEquals(before.onAccent, drawn.onAccent)
            assertEquals(before.kind, drawn.kind)
            assertEquals(before.diffAddedSurface, drawn.diffAddedSurface)
        }
    }

    private fun contrast(first: Color, second: Color) = AccentColorRules.contrastRatio(first.toArgb(), second.toArgb())

    private companion object {
        const val EPSILON = 1e-9
    }
}
