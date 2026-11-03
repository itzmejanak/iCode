package dev.icode.mobile.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
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

/**
 * One navigation/search catalog; every hit opens its existing settings page. A
 * subtitle says only what its page holds, and the search terms name its controls
 * (checked against each page, S8.4); a page that shows a subtitle takes it from here.
 */
internal val settingsCategories = listOf(
    SettingsCategory("Appearance", "Theme, UI text size, density, borders, accent", Icons.Outlined.Palette, Route.AppearanceSettings,
        "dark light amoled color colour theme ui text scale size compact layout density border lines borders outline accent ember"),
    SettingsCategory("Editor", "Font, indentation, keys, autosave, display", Icons.Outlined.Code, Route.SettingsEditor,
        "font family size tab indent word wrap auto close brackets keyboard suggestions autocorrect keys row home end " +
            "auto save line numbers minimap bracket matching git change markers"),
    SettingsCategory("Terminal", "Font, extra keys, scrollback, background", Icons.Outlined.Terminal, Route.TerminalSettings,
        "font family size scrollback extra keys keyboard cursor block underline bar " +
            "background notifications battery optimization bell alert long command"),
    SettingsCategory("Source Control", "Git credentials, commit message model", Icons.Outlined.AccountTree, Route.SourceControlSettings,
        "git identity name email credentials authentication https token ssh key provider commit message write generate ai model"),
    SettingsCategory("Assistant", "Providers, voice, phone control", Icons.Outlined.AutoAwesome, Route.AssistantSettings,
        "ai agent assistant koog provider model api key openai anthropic claude gemini google openrouter ollama deepseek approval voice microphone speech spoken"),
    SettingsCategory("Languages", "Language servers", Icons.Outlined.Public, Route.SettingsLanguages,
        "language server lsp command start stop restart"),
    SettingsCategory("Runtime", "Ubuntu environment, health, repair", Icons.Outlined.Storage, Route.SettingsRuntime,
        "linux ubuntu proot packages architecture disk repair reset"),
    SettingsCategory("Storage", "Disk usage, workspaces, cleanup", Icons.Outlined.Folder, Route.StorageSettings,
        "disk space usage files paths workspace workspaces folders size cache cleanup manage storage space uninstall keep data trash hidden dot files categories largest duplicates"),
    SettingsCategory("Application", "Version, build, and runtime", Icons.Outlined.Info, Route.About,
        "about version build editor engine android licenses source documentation"),
)

/** The open workspace's own settings, reached from the scope bar; search finds them too. */
internal val workspaceSettingsCategory = SettingsCategory(
    "Workspace", "This workspace's indentation and Git fetch", Icons.Outlined.FolderOpen, Route.WorkspaceSettings,
    "workspace indent size tab type spaces tabs override inherited git auto fetch identity file watcher exclude",
)

/** The subtitle a settings page shows: its catalog entry's. */
internal fun settingsSubtitle(route: Route): String =
    (settingsCategories + workspaceSettingsCategory).first { it.destination == route }.subtitle

internal fun searchSettingsCategories(query: String): List<SettingsCategory> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return settingsCategories
    return (settingsCategories + workspaceSettingsCategory).filter { category ->
        val searchable = "${category.title} ${category.subtitle} ${category.searchTerms}".lowercase()
        terms.all(searchable::contains)
    }
}
