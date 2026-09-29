package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.Color
import dev.icode.mobile.domain.AccentColorRules
import dev.icode.mobile.domain.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    @Test fun `each palette draws its theme's own accent`() {
        assertEquals(Color(AccentColorRules.themeAccent(AppTheme.Dark)), IcodePalette.Dark.accent)
        assertEquals(Color(AccentColorRules.themeAccent(AppTheme.Light)), IcodePalette.Light.accent)
        assertEquals(Color(AccentColorRules.themeAccent(AppTheme.Amoled)), IcodePalette.Amoled.accent)
    }
}
