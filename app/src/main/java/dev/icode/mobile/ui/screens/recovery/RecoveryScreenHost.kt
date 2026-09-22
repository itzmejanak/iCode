package dev.icode.mobile.ui.screens.recovery

import androidx.compose.runtime.Composable
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/**
 * Recovery and dialog frames.
 *
 * Each branch renders one screen file; `else` stays until every frame in the
 * group has migrated.
 */
@Composable
fun RecoveryScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen) {
        DemoScreen.DeleteConfirmation -> DeleteConfirmationScreen(actions)
        DemoScreen.ExternalFileChange -> ExternalChangeScreen(actions)
        DemoScreen.DocumentRecovery -> DocumentRecoveryScreen(actions)
        else -> Unit
    }
}
