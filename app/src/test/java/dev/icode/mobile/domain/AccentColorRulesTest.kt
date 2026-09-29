package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentColorRulesTest {
    private val ember = 0xFFFF6B2C.toInt()

    @Test fun `hex is written and read back in the forms people type`() {
        assertEquals("#FF6B2C", AccentColorRules.hex(ember))
        assertEquals(ember, AccentColorRules.parseHex("#FF6B2C"))
        assertEquals(ember, AccentColorRules.parseHex("ff6b2c"))
        assertEquals(ember, AccentColorRules.parseHex("  #ff6B2c "))
        listOf("", "#FFF", "#FF6B2", "#FF6B2CC", "#GG6B2C", "#FF 6B2").forEach { assertNull(it, AccentColorRules.parseHex(it)) }
    }

    @Test fun `a preset is named, anything else is custom`() {
        assertEquals("Ember", AccentColorRules.nameOf(ember))
        assertEquals("Sky", AccentColorRules.nameOf(0xFF4FB3FF.toInt()))
        assertEquals("Custom", AccentColorRules.nameOf(0xFF123456.toInt()))
    }

    @Test fun `contrast follows the WCAG formula`() {
        assertEquals(21.0, AccentColorRules.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, AccentColorRules.contrastRatio(ember, ember), 0.001)
        // Ember reads on the dark theme; a pale yellow does not read on the light one.
        assertTrue(AccentColorRules.contrastRatio(ember, 0xFF0A0D0B.toInt()) >= AccentColorRules.MIN_CONTRAST)
        assertTrue(AccentColorRules.contrastRatio(0xFFFFF59D.toInt(), 0xFFF5F7F3.toInt()) < AccentColorRules.MIN_CONTRAST)
    }

    @Test fun `text on the accent is black or white, whichever reads`() {
        assertEquals(0xFF000000.toInt(), AccentColorRules.onColor(0xFFFFB020.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), AccentColorRules.onColor(0xFF1A237E.toInt()))
    }
}
