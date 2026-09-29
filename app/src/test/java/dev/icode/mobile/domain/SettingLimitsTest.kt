package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingLimitsTest {
    @Test fun `the UI text slider runs from 100 to 150 percent in steps of 5`() {
        assertEquals(100, SettingLimits.uiTextScaleAt(0f))
        assertEquals(125, SettingLimits.uiTextScaleAt(0.5f))
        assertEquals(150, SettingLimits.uiTextScaleAt(1f))
        assertEquals(105, SettingLimits.uiTextScaleAt(0.07f))
        assertEquals(150, SettingLimits.uiTextScaleAt(1.4f))
    }

    @Test fun `a saved scale puts the slider where it reads the same scale back`() {
        SettingLimits.uiTextScales.forEach { percent ->
            assertEquals(percent, SettingLimits.uiTextScaleAt(SettingLimits.uiTextScalePosition(percent)))
        }
    }

    @Test fun `only a step is a UI text scale`() {
        assertTrue(125 in SettingLimits.uiTextScales)
        assertFalse(123 in SettingLimits.uiTextScales)
        assertFalse(155 in SettingLimits.uiTextScales)
    }

    @Test fun `pinch zoom stays within the font sizes`() {
        assertEquals(30f / 13, SettingLimits.zoomScale(4f, baseSp = 13), 1e-6f)
        assertEquals(9f / 13, SettingLimits.zoomScale(0.1f, baseSp = 13), 1e-6f)
        assertEquals(1.5f, SettingLimits.zoomScale(1.5f, baseSp = 13), 1e-6f)
    }

    @Test fun `the defaults are allowed values`() {
        assertTrue(EditorSettings().fontSizeSp in SettingLimits.fontSizesSp)
        assertTrue(TerminalSettings().fontSizeSp in SettingLimits.fontSizesSp)
        assertTrue(WorkspacePreferences().globalIndentSize in SettingLimits.indentSizes)
        assertTrue(AppearanceSettings().uiTextScalePercent in SettingLimits.uiTextScales)
    }
}
