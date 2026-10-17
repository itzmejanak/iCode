package dev.icode.mobile.ui.screens.entry

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route

/**
 * Entry, setup, permission and launcher frames.
 *
 * `LauncherHome` and `LauncherEmpty` share [LauncherScreen]; `else`: the routes of the other
 * groups, which their own hosts draw.
 */
@Composable
fun EntryScreenHost(screen: Route, actions: NavigationActions) {
    when (screen) {
        Route.Splash -> SplashScreen(actions)
        Route.RuntimeInstalling -> RuntimeInstallingScreen(actions)
        Route.RuntimeTools -> RuntimeToolsScreen(actions)
        Route.PermissionExplanation -> PermissionScreen(actions)
        Route.LauncherHome -> LauncherScreen(actions, empty = false)
        Route.LauncherEmpty -> LauncherScreen(actions, empty = true)
        Route.Workspaces -> FilesScreen(actions)
        Route.ChooseDestination -> FilesScreen(actions, choosing = true)
        Route.Notifications -> NotificationsScreen(actions)
        Route.CloneRepository -> CloneRepositoryScreen(actions)
        Route.CloneProgress -> CloneProgressScreen(actions)
        else -> Unit
    }
}
