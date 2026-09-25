package dev.icode.mobile.ui.screens.settings

import dev.icode.mobile.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
    @Test fun `control searches open the existing settings route`() {
        assertEquals(Route.SettingsEditor, searchSettingsCategories("minimap").single().destination)
        assertEquals(Route.SettingsEditor, searchSettingsCategories("auto save").single().destination)
        assertEquals(Route.AppearanceSettings, searchSettingsCategories("amoled").single().destination)
    }

    @Test fun `empty query returns the full category order`() {
        assertEquals(settingsCategories, searchSettingsCategories("   "))
        assertTrue(searchSettingsCategories("not a real setting").isEmpty())
    }
}
