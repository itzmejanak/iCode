package dev.icode.mobile.ui.screens.entry

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route

/**
 * Entry, setup, permission and launcher frames.
 *
 * `LauncherHome` and `LauncherEmpty` share [LauncherScreen]; the `else` branch
 * stays until every frame in the group has migrated.
 */
@Composable
fun EntryScreenHost(screen: Route, actions: NavigationActions) {
    when (screen) {
        Route.Splash -> SplashScreen(actions)
        Route.RuntimeInstalling -> RuntimeInstallingScreen(actions)
        Route.RuntimeComponents -> RuntimeComponentsScreen(actions)
        Route.PermissionExplanation -> PermissionScreen(actions)
        Route.LauncherHome -> LauncherScreen(actions, empty = false)
        Route.LauncherEmpty -> LauncherScreen(actions, empty = true)
        Route.Workspaces -> WorkspacesScreen(actions)
        Route.Notifications -> NotificationsScreen(actions)
        Route.ImportBrowser -> ImportBrowserScreen(actions)
        Route.ImportWorkspace -> ImportScreen(actions)
        Route.CloneRepository -> CloneRepositoryScreen(actions)
        Route.CloneProgress -> CloneProgressScreen(actions)
        else -> Unit
    }
}
