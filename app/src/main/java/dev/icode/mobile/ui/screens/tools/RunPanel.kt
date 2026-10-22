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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RunConfiguration
import dev.icode.mobile.domain.RunRules
import dev.icode.mobile.domain.RunStatus
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.SystemPermission
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
 * tap opens the run's output ([onShowOutput]); a long press on a saved command edits or deletes it.
 * [adding] is the header's +.
 */
@Composable
internal fun RunPanelContent(
    adding: Boolean,
    onAddingDone: () -> Unit,
    onShowOutput: (String) -> Unit,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val workspace = state.activeWorkspaceLocation
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
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
                    Box {
                        RunRow(
                            configuration = configuration,
                            status = status,
                            onRun = { run(configuration) },
                            onStop = { gateway.dispatch(ICodeIntent.StopRun(configuration.id)) },
                            onConfirmInstall = { gateway.dispatch(ICodeIntent.ConfirmAndroidAppInstall) },
                            onOpen = { process?.let { onShowOutput(it.id) } },
                            onLongPress = { if (configuration is RunConfiguration.Command) menuFor = configuration.id },
                        )
                        IcodeActionMenu(
                            expanded = menuFor == configuration.id,
                            onDismiss = { menuFor = null },
                            actions = listOf(
                                IcodeMenuAction("Edit", Icons.Outlined.Edit, { menuFor = null; editing = configuration.id }),
                                IcodeMenuAction("Delete", Icons.Outlined.Delete, { menuFor = null; deleting = configuration.id }, destructive = true),
                            ),
                        )
                    }
                }
            }
        }
    }

    if (need == RunNeed.AndroidSdk) {
        IcodeConfirmDialog(
            title = "Android SDK Needed",
            confirmLabel = "Open Runtime Tools",
            onConfirm = { need = null; navigate(Route.RuntimeTools) },
            onDismiss = { need = null },
        ) {
            Text("Build & Run builds with the Android SDK: JDK 17, SDK Platform 36 and Build-Tools 36. Install it in Runtime Tools.", style = IcodeType.Body)
        }
    }
    if (need == RunNeed.NativeTools) {
        IcodeConfirmDialog(
            title = "NDK and CMake Needed",
            confirmLabel = "Open Runtime Tools",
            onConfirm = { need = null; navigate(Route.RuntimeTools) },
            onDismiss = { need = null },
        ) {
            Text(
                "This app builds C or C++, with CMake ${RuntimeTools.CMAKE_VERSION} and an NDK built for this phone. " +
                    "Install CMake and the NDK the app names in Runtime Tools: ${RuntimeTools.ndks.values.joinToString(" or ") { it.name }}.",
                style = IcodeType.Body,
            )
        }
    }
    val commands = state.run.configurations.filterIsInstance<RunConfiguration.Command>()
    if (adding) RunCommandDialog(existing = null, onDismiss = onAddingDone)
    commands.firstOrNull { it.id == editing }?.let { RunCommandDialog(existing = it, onDismiss = { editing = null }) }
    commands.firstOrNull { it.id == deleting }?.let { command ->
        IcodeConfirmDialog(
            title = "Delete ${command.name}?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { gateway.dispatch(ICodeIntent.DeleteRunCommand(command.id)); deleting = null },
            onDismiss = { deleting = null },
        ) {
            MonoLine(command.command, style = IcodeType.MonoSmall, maxLines = 2)
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
        IconButton(onClick = if (stoppable) onStop else onRun) {
            Icon(
                if (stoppable) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                contentDescription = if (stoppable) "Stop ${configuration.name}" else "Run ${configuration.name}",
                tint = if (stoppable) IcodeColors.Error else IcodeColors.Accent,
            )
        }
    }
}

/** A saved command, new or changed: a name, which may be left to the command's first word, and the command line. */
@Composable
private fun RunCommandDialog(existing: RunConfiguration.Command?, onDismiss: () -> Unit) {
    val gateway = LocalICodeGateway.current
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var command by rememberSaveable(existing?.id) { mutableStateOf(existing?.command.orEmpty()) }
    IcodeConfirmDialog(
        title = if (existing == null) "New command" else "Edit command",
        confirmLabel = "Save",
        confirmEnabled = command.isNotBlank(),
        onConfirm = {
            gateway.dispatch(ICodeIntent.SaveRunCommand(existing?.id, name, command))
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        Text("It runs in bash at the workspace's top, with your login environment.", style = IcodeType.Body, color = IcodeColors.TextSecondary)
        LabeledTextField("Command", command, { command = it }, placeholder = "npm run dev", literal = true)
        LabeledTextField("Name", name, { name = it }, placeholder = "dev server")
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
