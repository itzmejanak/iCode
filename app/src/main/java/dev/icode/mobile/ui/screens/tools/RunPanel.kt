package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeIconButton
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RunConfiguration
import dev.icode.mobile.domain.RunRules
import dev.icode.mobile.domain.RunStatus
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.screens.entry.PermissionExplanation
import kotlinx.coroutines.delay

/** What an Android app's ▶ asks for before it builds. */
private enum class RunNeed { AndroidSdk, NativeTools, InstallApps }

/**
 * The Terminal panel's Run tab (docs/run-tab-plan.md §2.3): the open workspace's Android apps, then
 * the commands the user saved, as flat rows — name and kind, the command, the state, and ▶ or ■. A
 * tap opens the run's output ([onShowOutput]); a long press on a saved command opens its sheet
 * ([onHold], [RunCommandAsks]).
 */
@Composable
internal fun RunPanelContent(
    onHold: (String) -> Unit,
    onShowOutput: (String) -> Unit,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val workspace = state.activeWorkspaceLocation
    // What an Android app's ▶ asked for, and the configuration it waits to start.
    var need by rememberSaveable { mutableStateOf<RunNeed?>(null) }
    var waiting by rememberSaveable { mutableStateOf<String?>(null) }
    // The state lines' running time moves once a second while something runs.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.processes.any { it.running }) {
        while (state.processes.any { it.running }) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    // Allowed on Android's page: the run ▶ asked for goes on.
    LaunchedEffect(state.systemAccess?.installApps) {
        if (need == RunNeed.InstallApps && state.systemAccess?.installApps == true) {
            need = null
            waiting?.let { gateway.dispatch(ICodeIntent.StartRun(it)) }
            waiting = null
        }
    }

    fun run(configuration: RunConfiguration) {
        if (configuration is RunConfiguration.AndroidApp) {
            if (!state.runtimeTools.isInstalled(RuntimeTools.AndroidSdk)) return run { need = RunNeed.AndroidSdk }
            if (configuration.project.native && !state.runtimeTools.buildsNative) return run { need = RunNeed.NativeTools }
            if (state.systemAccess?.installApps != true) return run { need = RunNeed.InstallApps; waiting = configuration.id }
        }
        gateway.dispatch(ICodeIntent.StartRun(configuration.id))
    }

    Box(modifier.fillMaxSize()) {
        when {
            workspace == null -> EmptyState(
                title = "No workspace open",
                description = "Open a workspace to run its apps and commands.",
                modifier = Modifier.padding(IcodeSpacing.Space6),
            )
            need == RunNeed.InstallApps -> PermissionExplanation(
                permission = SystemPermission.InstallApps,
                onAllow = { gateway.dispatch(ICodeIntent.RequestSystemPermission(SystemPermission.InstallApps)) },
                onNotNow = { need = null; waiting = null },
                modifier = Modifier.fillMaxSize().padding(IcodeSpacing.Space6),
            )
            state.run.configurations.isEmpty() -> EmptyState(
                title = "Nothing to run yet",
                description = "Android apps in this workspace appear here. Save a command with +.",
                modifier = Modifier.padding(IcodeSpacing.Space6),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(state.run.configurations, key = { _, it -> it.id }) { index, configuration ->
                    if (index > 0) Divider()
                    val record = state.run.recordOf(workspace, configuration.id)
                    val process = record?.process?.let { id -> state.processes.firstOrNull { it.id == id } }
                    val status = RunRules.status(configuration, record, process, now)
                    RunRow(
                        configuration = configuration,
                        status = status,
                        onRun = { run(configuration) },
                        onStop = { gateway.dispatch(ICodeIntent.StopRun(configuration.id)) },
                        onConfirmInstall = { gateway.dispatch(ICodeIntent.ConfirmAndroidAppInstall) },
                        onOpen = { process?.let { onShowOutput(it.id) } },
                        onLongPress = { if (configuration is RunConfiguration.Command) onHold(configuration.id) },
                        starting = state.isBusy(BusyAction.RunStart, configuration.id),
                    )
                }
            }
        }
    }

    if (need == RunNeed.AndroidSdk) {
        IcodeConfirmSheet(
            title = "Android SDK Needed",
            confirmLabel = "Open Runtime Tools",
            onConfirm = { need = null; navigate(Route.RuntimeTools) },
            onDismiss = { need = null },
        ) {
            Text("Build & Run builds with the Android SDK: ${RuntimeTools.AndroidSdk.summary}. Install it in Runtime Tools.", style = IcodeType.Body)
        }
    }
    if (need == RunNeed.NativeTools) {
        IcodeConfirmSheet(
            title = "NDK and CMake Needed",
            confirmLabel = "Open Runtime Tools",
            onConfirm = { need = null; navigate(Route.RuntimeTools) },
            onDismiss = { need = null },
        ) {
            Text("${RuntimeTools.nativeNeed}. Install them in Runtime Tools.", style = IcodeType.Body)
        }
    }
}

/** One configuration: its name and kind, the command, the state line, and ▶ — or ■ while it runs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RunRow(
    configuration: RunConfiguration,
    status: RunStatus,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onConfirmInstall: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    /** ▶ was tapped and its process has not started yet. */
    starting: Boolean,
) {
    val stoppable = RunRules.stoppable(status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(start = IcodeSpacing.Space4, top = IcodeSpacing.Space3, bottom = IcodeSpacing.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro)) {
            Text(
                "${configuration.name} · ${configuration.kind}",
                style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            MonoLine(configuration.command, style = IcodeType.MonoSmall, color = IcodeColors.TextSecondary, maxLines = 1)
            Text(label(status), style = IcodeType.Caption.copy(color = color(status)), maxLines = 3)
            if (status == RunStatus.Installing) IcodeTextButton("Install", onConfirmInstall)
        }
        IcodeIconButton(
            if (stoppable) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
            contentDescription = if (stoppable) "Stop ${configuration.name}" else "Run ${configuration.name}",
            onClick = if (stoppable) onStop else onRun,
            busy = starting && !stoppable,
            tint = if (stoppable) IcodeColors.Error else IcodeColors.Accent,
        )
    }
}

/** What a row's state line says. */
private fun label(status: RunStatus): String = when (status) {
    RunStatus.NeverRun -> "Never run"
    is RunStatus.Running -> status.label + status.ports.firstOrNull()?.let { " · port $it" }.orEmpty()
    RunStatus.Installing -> "Installing · Android asks you to confirm"
    RunStatus.Opened -> "Opened"
    is RunStatus.Ended -> status.label
    is RunStatus.Failed -> "Failed · ${status.reason}"
}

@Composable
private fun color(status: RunStatus) = when (status) {
    is RunStatus.Running, RunStatus.Installing -> IcodeColors.Accent
    RunStatus.Opened -> IcodeColors.Green
    is RunStatus.Ended -> when (status.succeeded) {
        true -> IcodeColors.Green
        false -> IcodeColors.Error
        null -> IcodeColors.TextMuted
    }
    is RunStatus.Failed -> IcodeColors.Error
    RunStatus.NeverRun -> IcodeColors.TextMuted
}
