package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/theme-variants-plan.md D1. */
class ThemeVariantRulesTest {
    @Test fun `an id is lower-case letters, digits and underscores, up to 40`() {
        listOf("nord", "catppuccin_mocha", "a", "x".repeat(40), "solarized_light2").forEach { assertTrue(it, ThemeVariantRules.isId(it)) }
        listOf("", " ", "Nord", "rosé_pine", "_nord", "nord-dark", "nord ", "x".repeat(41)).forEach { assertFalse(it, ThemeVariantRules.isId(it)) }
    }

    @Test fun `iCode's own, none and what is not an id are all kept as no id`() {
        assertNull(ThemeVariantRules.kept(null))
        assertNull(ThemeVariantRules.kept(ThemeVariantRules.ICODE))
        assertNull(ThemeVariantRules.kept("Nord"))
        assertNull(ThemeVariantRules.kept(""))
        assertEquals("nord", ThemeVariantRules.kept("nord"))
    }

    @Test fun `each theme has its own variant, and one without is iCode's own`() {
        val appearance = AppearanceSettings(theme = AppTheme.Light, variants = mapOf(AppTheme.Dark to "nord"))
        assertNull(appearance.variantOf())
        assertEquals("nord", appearance.variantOf(AppTheme.Dark))
        assertNull(appearance.variantOf(AppTheme.Amoled))
        assertEquals("nord", appearance.copy(theme = AppTheme.Dark).variantOf())
    }
}
