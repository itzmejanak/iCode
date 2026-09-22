package dev.icode.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icode.mobile.demo.DemoWorkbenchGateway
import dev.icode.mobile.designsystem.IcodeScreen
import dev.icode.mobile.designsystem.IcodeTheme
import dev.icode.mobile.designsystem.LocalTitleAction
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoGroup
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.catalog.ScreenCatalog
import dev.icode.mobile.ui.screens.entry.EntryScreenHost
import dev.icode.mobile.ui.screens.recovery.RecoveryScreenHost
import dev.icode.mobile.ui.screens.settings.SettingsScreenHost
import dev.icode.mobile.ui.screens.tools.ToolScreenHost
import dev.icode.mobile.ui.screens.workspace.WorkspaceScreenHost

@Composable
fun IcodeDemoApp(
    initialScreen: DemoScreen = DemoScreen.Splash,
    staticDemo: Boolean = false,
) {
    IcodeTheme {
        val gateway = remember { DemoWorkbenchGateway() }
        val snapshot by gateway.snapshot.collectAsStateWithLifecycle()
        val navigation = rememberDemoNavigationState(initialScreen, staticDemo)

        BackHandler(enabled = navigation.catalogOpen || navigation.canGoBack) {
            if (navigation.catalogOpen) navigation.closeCatalog() else navigation.actions.back()
        }

        CompositionLocalProvider(
            LocalTitleAction provides navigation::openCatalog,
            LocalWorkbenchSnapshot provides snapshot,
            LocalWorkbenchGateway provides gateway,
        ) {
            IcodeScreen {
                ScreenHost(screen = navigation.current, actions = navigation.actions)
            }
        }

        if (navigation.catalogOpen) {
            ScreenCatalog(
                current = navigation.current,
                onDismiss = navigation::closeCatalog,
                onSelect = navigation.actions.navigate,
            )
        }
    }
}

@Composable
private fun ScreenHost(screen: DemoScreen, actions: DemoActions) {
    when (screen.group) {
        DemoGroup.ENTRY -> EntryScreenHost(screen, actions)
        DemoGroup.WORKSPACE -> WorkspaceScreenHost(screen, actions)
        DemoGroup.TOOLS -> ToolScreenHost(screen, actions)
        DemoGroup.SETTINGS -> SettingsScreenHost(screen, actions)
        DemoGroup.RECOVERY -> RecoveryScreenHost(screen, actions)
    }
}

