package dev.icode.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeScreen
import dev.icode.mobile.designsystem.IcodeTheme
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LocalOpenDemoCatalog
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.navigation.DemoGroup
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.demo.DemoWorkbenchGateway
import dev.icode.mobile.ui.screens.EntryScreenHost
import dev.icode.mobile.ui.screens.RecoveryScreenHost
import dev.icode.mobile.ui.screens.SettingsScreenHost
import dev.icode.mobile.ui.screens.ToolScreenHost
import dev.icode.mobile.ui.screens.WorkspaceScreenHost

data class DemoActions(
    val navigate: (DemoScreen) -> Unit,
    val back: () -> Unit,
    val reset: () -> Unit,
    val staticDemo: Boolean,
)

@Composable
fun IcodeDemoApp(
    initialScreen: DemoScreen = DemoScreen.Splash,
    staticDemo: Boolean = false,
) {
    IcodeTheme {
        val gateway = remember { DemoWorkbenchGateway() }
        val snapshot by gateway.snapshot.collectAsStateWithLifecycle()
        var historyNames by rememberSaveable(initialScreen) { mutableStateOf(listOf(initialScreen.name)) }
        var catalogOpen by rememberSaveable { mutableStateOf(false) }
        val current = DemoScreen.valueOf(historyNames.last())

        val actions = remember(historyNames) {
            DemoActions(
                navigate = { destination ->
                    if (destination != current) historyNames = historyNames + destination.name
                    catalogOpen = false
                },
                back = {
                    if (historyNames.size > 1) historyNames = historyNames.dropLast(1)
                },
                reset = { historyNames = listOf(DemoScreen.LauncherHome.name) },
                staticDemo = staticDemo,
            )
        }

        BackHandler(enabled = catalogOpen || historyNames.size > 1) {
            if (catalogOpen) catalogOpen = false else actions.back()
        }

        CompositionLocalProvider(
            LocalOpenDemoCatalog provides { catalogOpen = true },
            LocalWorkbenchSnapshot provides snapshot,
            LocalWorkbenchGateway provides gateway,
        ) {
            IcodeScreen {
                ScreenHost(screen = current, actions = actions)
            }
        }

        if (catalogOpen) {
            ScreenCatalog(
                current = current,
                onDismiss = { catalogOpen = false },
                onSelect = actions.navigate,
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

@Composable
private fun ScreenCatalog(
    current: DemoScreen,
    onDismiss: () -> Unit,
    onSelect: (DemoScreen) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            SecondaryButton(text = "Close", onClick = onDismiss)
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Figma screen catalog", style = IcodeType.Title)
                Text("${DemoScreen.entries.size} implemented frames · current ${current.figmaNode}", style = IcodeType.Caption)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight(0.72f)
                    .widthIn(max = 520.dp),
            ) {
                DemoGroup.entries.forEach { group ->
                    item {
                        SectionLabel(group.label, Modifier.padding(top = 12.dp, bottom = 6.dp))
                    }
                    items(DemoScreen.entries.filter { it.group == group }) { screen ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (screen == current) IcodeColors.SurfaceElevated else Color.Transparent)
                                .clickable { onSelect(screen) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(screen.title, style = IcodeType.Body)
                            Text(screen.figmaNode, style = IcodeType.MonoSmall, color = IcodeColors.Accent)
                        }
                        Divider()
                    }
                }
            }
        },
        containerColor = IcodeColors.Surface,
        tonalElevation = 0.dp,
    )
}
