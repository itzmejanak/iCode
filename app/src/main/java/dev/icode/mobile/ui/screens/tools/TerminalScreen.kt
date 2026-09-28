package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.BottomBarEntry
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.domain.TerminalStatus
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.terminal.TerminalHost
import dev.icode.mobile.ui.terminal.TerminalKeys

/** One tools panel: terminal, problems and output share a tab header and back-stack entry. */
@Composable
fun TerminalScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val terminal = state.terminal
    val session = terminal.selected
    val runtimeReady = state.runtime is RuntimeState.Ready
    val terminalSettings = LocalWorkspacePreferences.current.terminal

    // Starting a panel on Problems/Output must not start a shell. Closing the
    // last shell must not silently reopen one or dismiss the other two tabs.
    var attemptedFirstOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(actions.panelTab, runtimeReady, terminal.sessions.isEmpty()) {
        if (terminal.sessions.isNotEmpty()) attemptedFirstOpen = true
        else if (shouldAutoOpenTerminal(actions.panelTab, runtimeReady, attemptedFirstOpen)) {
            attemptedFirstOpen = true
            // Not "+": a panel drawn again must find the session it asked for, not start another.
            gateway.dispatch(WorkbenchIntent.EnsureTerminalSession)
        }
    }
    // Preserve the terminal's last-tab exit behavior only when the user is
    // looking at Terminal. An ending shell must not dismiss Problems or Output.
    var hadSessions by remember { mutableStateOf(false) }
    LaunchedEffect(terminal.sessions.isEmpty()) {
        if (terminal.sessions.isNotEmpty()) hadSessions = true
        else if (shouldReturnAfterLastTerminal(actions.panelTab, hadSessions)) {
            hadSessions = false
            actions.back()
        }
    }

    var fontScale by rememberSaveable { mutableFloatStateOf(1f) }
    var problemsFilter by rememberSaveable { mutableStateOf(Severity.Error) }
    val keys = remember { TerminalKeys() }
    LaunchedEffect(terminalSettings.extraKeys) { keys.keepOnly(terminalSettings.extraKeys) }

    Column(Modifier.fillMaxSize().background(IcodeColors.BackgroundDeep)) {
        // The panel's own header: its tabs drawn as the bottom bar's entries
        // (owner's design) — accent when active, a count pill on Problems.
        Row(
            modifier = Modifier.fillMaxWidth().height(IcodeSizes.TopBar).background(IcodeColors.Background),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.back) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = IcodeColors.TextPrimary)
            }
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolPanelTab.entries.forEach { tab ->
                    BottomBarEntry(
                        BottomBarItem(
                            label = when (tab) {
                                ToolPanelTab.Terminal -> "Terminal"
                                ToolPanelTab.Problems -> "Problems"
                                ToolPanelTab.Output -> "Output"
                            },
                            onClick = { actions.openPanel(tab) },
                            active = actions.panelTab == tab,
                            // Only the open tab is coloured; the pill says there are problems.
                            count = state.diagnostics.size.takeIf { tab == ToolPanelTab.Problems && it > 0 },
                        ),
                    )
                }
            }
            if (actions.panelTab == ToolPanelTab.Terminal) {
                IconButton(
                    onClick = { gateway.dispatch(WorkbenchIntent.OpenTerminalSession) },
                    enabled = runtimeReady,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "New terminal session",
                        tint = if (runtimeReady) IcodeColors.Accent else IcodeColors.TextMuted,
                    )
                }
            }
        }
        when (actions.panelTab) {
            ToolPanelTab.Problems -> ProblemsPanelContent(
                filter = problemsFilter,
                onFilterChange = { problemsFilter = it },
                modifier = Modifier.weight(1f),
            )
            ToolPanelTab.Output -> OutputPanelContent(Modifier.weight(1f))
            ToolPanelTab.Terminal -> {
                if (terminal.sessions.isNotEmpty()) {
                    TerminalTabStrip(
                        sessions = terminal.sessions,
                        selectedId = session?.id,
                        onSelect = { gateway.dispatch(WorkbenchIntent.SelectTerminalSession(it)) },
                        onReorder = { movingId, targetId ->
                            gateway.dispatch(WorkbenchIntent.ReorderTerminalSession(movingId, targetId))
                        },
                        onClose = { gateway.dispatch(WorkbenchIntent.CloseTerminalSession(it)) },
                    )
                }
                if (session?.status == TerminalStatus.Exited) {
                    Text(
                        text = session.exitDescription.orEmpty(),
                        style = IcodeType.Caption.copy(color = IcodeColors.Error),
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = IcodeSpacing.Space3,
                            vertical = IcodeSpacing.Space1,
                        ),
                    )
                }
                when {
                    !runtimeReady -> RuntimeGate(state.runtime) {
                        actions.navigate(Route.RuntimeInstalling)
                    }
                    session == null -> EmptyState(
                        title = "No terminal sessions",
                        description = "Use + in the header to open a shell in this workspace.",
                        modifier = Modifier.padding(IcodeSpacing.Space6),
                    )
                    session.status == TerminalStatus.Failed -> EmptyState(
                        title = "The terminal could not start",
                        description = session.exitDescription.orEmpty(),
                        modifier = Modifier.padding(IcodeSpacing.Space6),
                    )
                    else -> TerminalHost(
                        sessionId = session.id,
                        settings = terminalSettings,
                        fontScale = fontScale,
                        keys = keys,
                        onScale = { fontScale = (fontScale * it).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) },
                        onUrl = { gateway.dispatch(WorkbenchIntent.OpenExternalUrl(it)) },
                        onEndedEnter = { gateway.dispatch(WorkbenchIntent.CloseTerminalSession(session.id)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (session != null && runtimeReady && terminalSettings.extraKeys.isNotEmpty()) {
                    ExtraKeyRow(
                        keys = terminalSettings.extraKeys,
                        enabled = session.isRunning,
                        ctrlHeld = keys.ctrl,
                        altHeld = keys.alt,
                        onKey = keys::press,
                    )
                }
            }
        }
    }
}

private const val MIN_FONT_SCALE = 0.6f
private const val MAX_FONT_SCALE = 2.4f

internal fun shouldAutoOpenTerminal(tab: ToolPanelTab, runtimeReady: Boolean, alreadyAttempted: Boolean): Boolean =
    tab == ToolPanelTab.Terminal && runtimeReady && !alreadyAttempted

internal fun shouldReturnAfterLastTerminal(tab: ToolPanelTab, hadSessions: Boolean): Boolean =
    tab == ToolPanelTab.Terminal && hadSessions
