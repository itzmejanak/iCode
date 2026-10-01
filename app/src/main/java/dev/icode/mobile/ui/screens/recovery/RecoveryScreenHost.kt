package dev.icode.mobile.ui.screens.recovery

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route

/**
 * Recovery and dialog frames.
 *
 * Each branch renders one screen file; `else`: the routes of the other groups, which their own
 * hosts draw.
 */
@Composable
fun RecoveryScreenHost(screen: Route, actions: NavigationActions) {
    when (screen) {
        Route.ExternalFileChange -> ExternalChangeScreen(actions)
        Route.DocumentRecovery -> DocumentRecoveryScreen(actions)
        else -> Unit
    }
}
