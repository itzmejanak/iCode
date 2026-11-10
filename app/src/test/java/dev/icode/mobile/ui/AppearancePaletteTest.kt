package dev.icode.mobile.ui

import androidx.compose.ui.graphics.Color
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The one place a palette is chosen (docs/theme-variants-plan.md D2). */
class AppearancePaletteTest {
    @Test fun `before the settings are read the app is drawn in iCode's dark`() {
        assertSame(IcodePalette.Dark, appearancePalette(null))
    }

    @Test fun `a theme is drawn in its own variant, not another theme's`() {
        // Dark's variant does not reach Light; Light has none, so it is iCode's own.
        val appearance = AppearanceSettings(theme = AppTheme.Light, variants = mapOf(AppTheme.Dark to "nord"))
        assertSame(IcodePalette.Light, appearancePalette(appearance))
    }

    @Test fun `an id no variant has draws today's look`() {
        val appearance = AppearanceSettings(theme = AppTheme.Amoled, variants = mapOf(AppTheme.Amoled to "removed_variant"))
        assertSame(IcodePalette.Amoled, appearancePalette(appearance))
    }

    @Test fun `the user's accent is drawn over the variant`() {
        val appearance = AppearanceSettings(theme = AppTheme.Dark, accentColor = 0xFF4FB3FF.toInt())
        assertEquals(Color(0xFF4FB3FF), appearancePalette(appearance).accent)
    }
}
