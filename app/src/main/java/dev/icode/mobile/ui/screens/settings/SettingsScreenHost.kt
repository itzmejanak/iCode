package dev.icode.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route

/**
 * Settings frames.
 *
 * `else`: the routes of the other groups, which their own hosts draw.
 */
@Composable
fun SettingsScreenHost(screen: Route, actions: NavigationActions) {
    when (screen) {
        Route.SettingsRoot -> SettingsRootScreen(actions)
        Route.SettingsEditor -> EditorSettingsScreen(actions)
        Route.SettingsLanguages -> LanguageServicesScreen(actions)
        Route.SettingsLanguageDetail -> LanguageDetailScreen(actions)
        Route.SettingsRuntime -> RuntimeSettingsScreen(actions)
        Route.AppearanceSettings -> AppearanceScreen(actions)
        Route.TerminalSettings -> TerminalSettingsScreen(actions)
        Route.StorageSettings -> StorageSettingsScreen(actions)
        Route.ManageStorage -> ManageStorageScreen(actions)
        Route.About -> AboutScreen(actions)
        Route.WorkspaceSettings -> WorkspaceSettingsScreen(actions)
        Route.AssistantSettings -> AssistantSettingsScreen(actions)
        Route.AssistantProvider -> AssistantProviderScreen(actions)
        Route.AssistantProviders -> AssistantProvidersScreen(actions)
        Route.VoiceSettings -> VoiceSettingsScreen(actions)
        Route.PhoneControl -> PhoneControlScreen(actions)
        Route.ShizukuSetup -> ShizukuScreen(actions)
        Route.McpServers -> McpServersScreen(actions)
        Route.McpServer -> McpServerScreen(actions)
        Route.SourceControlSettings -> SourceControlSettingsScreen(actions)
        else -> Unit
    }
}
