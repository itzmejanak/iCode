package dev.icode.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route

/**
 * Settings frames.
 *
 * `else` stays until every frame in the group has migrated.
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
        Route.About -> AboutScreen(actions)
        Route.WorkspaceSettings -> WorkspaceSettingsScreen(actions)
        else -> Unit
    }
}
