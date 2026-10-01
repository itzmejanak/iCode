package dev.icode.mobile.ui.screens.settings

import dev.icode.mobile.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
    @Test fun `control searches open the existing settings route`() {
        assertEquals(Route.SettingsEditor, searchSettingsCategories("minimap").single().destination)
        assertEquals(Route.SettingsEditor, searchSettingsCategories("auto save").single().destination)
        assertEquals(Route.AppearanceSettings, searchSettingsCategories("amoled").single().destination)
    }

    @Test fun `controls the old terms missed are found on their page`() {
        assertEquals(Route.TerminalSettings, searchSettingsCategories("cursor").single().destination)
        assertEquals(Route.TerminalSettings, searchSettingsCategories("bell").single().destination)
        assertEquals(Route.GitAuth, searchSettingsCategories("token").single().destination)
        assertEquals(Route.WorkspaceSettings, searchSettingsCategories("auto fetch").single().destination)
        assertEquals(Route.AssistantSettings, searchSettingsCategories("api key").single().destination)
        assertEquals(Route.AssistantSettings, searchSettingsCategories("ollama").single().destination)
        assertEquals("Provider, model, API key", settingsSubtitle(Route.AssistantSettings))
        assertEquals(
            listOf(Route.SettingsEditor, Route.WorkspaceSettings),
            searchSettingsCategories("indent").map { it.destination },
        )
    }

    @Test fun `workspace settings are found, not listed`() {
        assertFalse(workspaceSettingsCategory in searchSettingsCategories(""))
        assertEquals("This workspace's indentation and Git fetch", settingsSubtitle(Route.WorkspaceSettings))
    }

    @Test fun `empty query returns the full category order`() {
        assertEquals(settingsCategories, searchSettingsCategories("   "))
        assertTrue(searchSettingsCategories("not a real setting").isEmpty())
    }
}
