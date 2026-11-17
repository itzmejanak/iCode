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
        assertEquals(Route.AppearanceSettings, searchSettingsCategories("border lines").single().destination)
        assertEquals(Route.AppearanceSettings, searchSettingsCategories("outline").single().destination)
        // Every theme variant by its name, its accents typed or not (docs/design-and-navigation.md).
        listOf("dracula", "tokyo night day", "rosé pine", "rose pine dawn", "catppuccin latte", "high contrast", "variant", "match phone").forEach { name ->
            assertEquals(name, Route.AppearanceSettings, searchSettingsCategories(name).single().destination)
        }
    }

    @Test fun `the editor's terms are its page's controls`() {
        assertEquals(Route.SettingsEditor, searchSettingsCategories("keys row").single().destination)
        assertEquals(Route.SettingsEditor, searchSettingsCategories("keyboard suggestions").single().destination)
        assertEquals(Route.SettingsEditor, searchSettingsCategories("git change markers").single().destination)
        assertEquals(Route.SettingsEditor, searchSettingsCategories("auto close brackets").single().destination)
        assertEquals(Route.SettingsEditor, searchSettingsCategories("semantic highlighting").single().destination)
        // Removed from the page (docs/editor-and-language-services.md): no longer found there.
        assertTrue(searchSettingsCategories("format on save").isEmpty())
    }

    @Test fun `controls the old terms missed are found on their page`() {
        assertEquals(Route.TerminalSettings, searchSettingsCategories("cursor").single().destination)
        assertEquals(Route.TerminalSettings, searchSettingsCategories("bell").single().destination)
        assertEquals(Route.SourceControlSettings, searchSettingsCategories("token").single().destination)
        assertEquals(Route.SourceControlSettings, searchSettingsCategories("commit message").single().destination)
        assertEquals("Git credentials, commit message model", settingsSubtitle(Route.SourceControlSettings))
        assertEquals(Route.WorkspaceSettings, searchSettingsCategories("auto fetch").single().destination)
        assertEquals(Route.AssistantSettings, searchSettingsCategories("api key").single().destination)
        assertEquals(Route.AssistantSettings, searchSettingsCategories("ollama").single().destination)
        assertEquals("Providers, voice, phone control", settingsSubtitle(Route.AssistantSettings))
        assertEquals(Route.AssistantSettings, searchSettingsCategories("microphone").single().destination)
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
