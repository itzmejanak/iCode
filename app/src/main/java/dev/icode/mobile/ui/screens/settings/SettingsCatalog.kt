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
import dev.icode.mobile.navigation.Route

/** One entry on the settings root page. */
internal data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val destination: Route,
    /** Control names make root search useful without creating duplicate setting screens. */
    val searchTerms: String = "",
)

/** One navigation/search catalog; every hit opens its existing settings page. */
internal val settingsCategories = listOf(
    SettingsCategory("Appearance", "Theme, fonts, and display", Icons.Outlined.Palette, Route.AppearanceSettings,
        "dark light amoled color ui text scale compact layout accent ember"),
    SettingsCategory("Editor", "Editing behavior, formatting, autosave", Icons.Outlined.Code, Route.SettingsEditor,
        "font family size tab indent word wrap auto save format on save line numbers minimap bracket matching"),
    SettingsCategory("Terminal", "Shell, extra keys, scrollback, background", Icons.Outlined.Terminal, Route.TerminalSettings,
        "keyboard font size scrollback background notifications battery optimization"),
    SettingsCategory("Source Control", "Git preferences and diff display", Icons.Outlined.AccountTree, Route.GitAuth,
        "git credentials authentication ssh token"),
    SettingsCategory("Languages", "Syntax, highlighting, language servers", Icons.Outlined.Public, Route.SettingsLanguages,
        "grammar syntax highlighting"),
    SettingsCategory("Runtime", "Ubuntu environment, processes, health", Icons.Outlined.Storage, Route.SettingsRuntime,
        "linux repair reset"),
    SettingsCategory("Storage", "Workspace paths and cleanup", Icons.Outlined.Folder, Route.StorageSettings,
        "files paths cleanup"),
    SettingsCategory("Application", "Updates, about, diagnostics", Icons.Outlined.Info, Route.About,
        "version"),
)

internal fun searchSettingsCategories(query: String): List<SettingsCategory> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return settingsCategories
    return settingsCategories.filter { category ->
        val searchable = "${category.title} ${category.subtitle} ${category.searchTerms}".lowercase()
        terms.all(searchable::contains)
    }
}
