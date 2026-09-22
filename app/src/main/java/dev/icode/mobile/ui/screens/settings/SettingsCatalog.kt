package dev.icode.mobile.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.navigation.DemoScreen

/** One entry on the settings root page. */
internal data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: DemoScreen,
)

/** Static settings navigation catalog; Phase 2 replaces it with the settings contract. */
internal val settingsCategories = listOf(
    SettingsCategory("Appearance", "Theme, fonts, and display", Icons.Outlined.Palette, DemoScreen.AppearanceSettings),
    SettingsCategory("Editor", "Editing behavior, formatting, autosave", Icons.Outlined.Code, DemoScreen.SettingsEditor),
    SettingsCategory("Terminal", "Shell, extra keys, scrollback", Icons.Outlined.Terminal, DemoScreen.TerminalSettings),
    SettingsCategory("Source Control", "Git preferences and diff display", Icons.Outlined.AccountTree, DemoScreen.GitAuth),
    SettingsCategory("Languages", "Syntax, highlighting, language servers", Icons.Outlined.Public, DemoScreen.SettingsLanguages),
    SettingsCategory("Runtime", "Ubuntu environment, processes, health", Icons.Outlined.Storage, DemoScreen.SettingsRuntime),
    SettingsCategory("Storage", "Workspace paths and cleanup", Icons.Outlined.Folder, DemoScreen.StorageSettings),
    SettingsCategory("Application", "Updates, about, diagnostics", Icons.Outlined.Info, DemoScreen.About),
)
