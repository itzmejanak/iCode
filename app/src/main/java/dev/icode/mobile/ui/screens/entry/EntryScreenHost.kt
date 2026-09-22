package dev.icode.mobile.ui.screens.entry

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/**
 * Entry, setup, permission and launcher frames.
 *
 * `LauncherHome` and `LauncherEmpty` share [LauncherScreen]; the `else` branch
 * stays until every frame in the group has migrated.
 */
@Composable
fun EntryScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen) {
        DemoScreen.Splash -> SplashScreen(actions)
        DemoScreen.RuntimeInstalling -> RuntimeInstallingScreen(actions)
        DemoScreen.RuntimeError -> RuntimeErrorScreen(actions)
        DemoScreen.PermissionExplanation -> PermissionScreen(actions)
        DemoScreen.LauncherHome -> LauncherScreen(actions, empty = false)
        DemoScreen.LauncherEmpty -> LauncherScreen(actions, empty = true)
        DemoScreen.CloneRepository -> CloneRepositoryScreen(actions)
        DemoScreen.CloneProgress -> CloneProgressScreen(actions)
        DemoScreen.CreateFolder -> CreateFolderScreen(actions)
        DemoScreen.OpenFolderBrowser -> OpenFolderScreen(actions)
        else -> Unit
    }
}
