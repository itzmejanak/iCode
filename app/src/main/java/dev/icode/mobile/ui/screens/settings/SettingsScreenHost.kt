package dev.icode.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/**
 * Settings frames.
 *
 * `else` stays until every frame in the group has migrated.
 */
@Composable
fun SettingsScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen) {
        DemoScreen.SettingsRoot -> SettingsRootScreen(actions)
        DemoScreen.SettingsEditor -> EditorSettingsScreen(actions)
        DemoScreen.SettingsLanguages -> LanguageServicesScreen(actions)
        DemoScreen.SettingsLanguageDetail -> LanguageDetailScreen(actions)
        DemoScreen.SettingsRuntime -> RuntimeSettingsScreen(actions)
        DemoScreen.AppearanceSettings -> AppearanceScreen(actions)
        DemoScreen.TerminalSettings -> TerminalSettingsScreen(actions)
        DemoScreen.StorageSettings -> StorageSettingsScreen(actions)
        DemoScreen.About -> AboutScreen(actions)
        DemoScreen.WorkspaceSettings -> WorkspaceSettingsScreen(actions)
        else -> Unit
    }
}
