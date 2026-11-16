package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.CHECKING
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeFact
import dev.icode.mobile.designsystem.IcodeFactGrid
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeSpinner
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RuntimeDetails
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.RuntimeToolsState
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.domain.storageSizeLabel
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** Which rebuild the user asked for; both are destructive, so both confirm. */
private enum class RuntimeRebuild { Repair, Reset }

/**
 * The runtime (docs/design-and-navigation.md): one card — the release, its state as a dot and a word,
 * and what can be measured of it — then its tools, and the way out of a broken one. An interrupted
 * package operation can leave a runtime that cannot repair itself from inside — measured on device —
 * so rebuilding it has to be reachable here rather than only by clearing the app's data. What each
 * rebuild keeps is said in a line; all of it, in the sheet that confirms it.
 */
@Composable
fun RuntimeSettingsScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val details = state.runtimeDetails
    var pending by rememberSaveable { mutableStateOf<RuntimeRebuild?>(null) }
    // The rebuild confirmed here, until the runtime says which runs (it waits for the lock first).
    var confirmed by rememberSaveable { mutableStateOf<RuntimeRebuild?>(null) }

    LaunchedEffect(Unit) { gateway.dispatch(ICodeIntent.RefreshRuntimeDetails) }

    // From the confirmed tap until the runtime is rebuilt: neither starts twice, and the one asked says it runs.
    val rebuilding = state.isBusy(BusyAction.Runtime) || state.runtime is RuntimeState.Downloading || state.runtime is RuntimeState.Installing
    val running = when (state.runtimeInstallReason) {
        RuntimeInstallReason.Repair -> RuntimeRebuild.Repair
        RuntimeInstallReason.Reset -> RuntimeRebuild.Reset
        RuntimeInstallReason.Initial -> confirmed
    }.takeIf { rebuilding }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Runtime Environment", subtitle = settingsSubtitle(Route.SettingsRuntime), onBack = actions.back)
        IcodePageColumn {
            RuntimeCard(state.runtime, details, onSetUp = { actions.navigate(Route.RuntimeInstalling) })
            IcodeSection("Tools") {
                SettingRow(
                    "Runtime Tools",
                    Modifier.fillMaxWidth(),
                    subtitle = toolsCount(state.runtimeTools),
                    leadingIcon = Icons.Outlined.Extension,
                    onClick = { actions.navigate(Route.RuntimeTools) },
                    trailing = { Chevron() },
                )
            }
            IcodeSection("Maintenance") {
                Rebuild("Repair Runtime", "Keeps your home and workspaces", Icons.Outlined.Build, IcodeColors.Accent, rebuilding, running == RuntimeRebuild.Repair) {
                    pending = RuntimeRebuild.Repair
                }
                Rebuild("Reset Runtime", "Keeps only your workspaces", Icons.Outlined.RestartAlt, IcodeColors.Error, rebuilding, running == RuntimeRebuild.Reset) {
                    pending = RuntimeRebuild.Reset
                }
            }
        }
    }

    pending?.let { rebuild ->
        IcodeConfirmSheet(
            title = if (rebuild == RuntimeRebuild.Repair) "Repair runtime" else "Reset runtime",
            message = if (rebuild == RuntimeRebuild.Repair) {
                "The base system is downloaded and rebuilt. Your home directory and workspaces are kept; " +
                    "packages you installed are not. This cannot be undone."
            } else {
                "Everything in the runtime is removed and rebuilt, including your home " +
                    "directory and installed tools. Workspaces are kept. This cannot be undone."
            },
            detail = details.release,
            confirmLabel = if (rebuild == RuntimeRebuild.Repair) "REPAIR" else "RESET",
            onConfirm = {
                confirmed = rebuild
                gateway.dispatch(
                    if (rebuild == RuntimeRebuild.Repair) {
                        ICodeIntent.RepairRuntime
                    } else {
                        ICodeIntent.ResetRuntime
                    },
                )
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

/**
 * The runtime in one card: its release, by name, over its codename and the phone's architecture; its
 * state beside it, a dot and a word — healthy in the accent, as the Launcher says it. Ready, what can
 * be measured of it; otherwise what it is doing, or why it cannot run, and the way to set it up.
 */
@Composable
private fun RuntimeCard(runtime: RuntimeState, details: RuntimeDetails, onSetUp: () -> Unit) {
    IcodeCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro)) {
                    Text(details.releaseName ?: "Linux runtime", style = IcodeType.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val about = listOfNotNull(details.codename, details.architecture.ifBlank { null }).joinToString(" · ")
                    if (about.isNotEmpty()) Text(about, style = IcodeType.MonoSmall, maxLines = 1)
                }
                val (word, color) = stateOf(runtime)
                StatusLabel(word, color)
            }
            if (runtime is RuntimeState.Ready) {
                IcodeFactGrid(facts(details))
            } else {
                Text(summaryOf(runtime), style = IcodeType.Caption)
                if (runtime is RuntimeState.NotInstalled || runtime is RuntimeState.Failed) IcodeTextButton("Set Up the Runtime", onSetUp)
            }
        }
    }
}

/** A rebuild's row: what it is, what it keeps; busy while it runs, and neither taps while one does. */
@Composable
private fun Rebuild(title: String, keeps: String, icon: ImageVector, tint: Color, rebuilding: Boolean, busy: Boolean, onClick: () -> Unit) {
    SettingRow(
        title,
        Modifier.fillMaxWidth(),
        subtitle = keeps,
        leadingIcon = icon,
        leadingTint = if (rebuilding) IcodeColors.TextMuted else tint,
        titleColor = when {
            rebuilding -> IcodeColors.TextMuted
            tint == IcodeColors.Error -> IcodeColors.Error
            else -> IcodeColors.TextPrimary
        },
        onClick = onClick.takeUnless { rebuilding },
        trailing = { if (busy) IcodeSpinner() else Chevron() },
    )
}

/** What it is now, as a word and its colour. */
@Composable
private fun stateOf(runtime: RuntimeState): Pair<String, Color> = when (runtime) {
    is RuntimeState.Ready -> "Healthy" to IcodeColors.Accent
    is RuntimeState.Downloading, is RuntimeState.Installing -> "Installing" to IcodeColors.Amber
    is RuntimeState.Failed -> "Failed" to IcodeColors.Error
    is RuntimeState.NotInstalled -> "Not installed" to IcodeColors.Amber
    RuntimeState.Unknown -> "Checking" to IcodeColors.TextMuted
}

/** What can be measured of a ready runtime; each says it is checking until the first read lands. */
private fun facts(details: RuntimeDetails): List<IcodeFact> {
    val checking = CHECKING.takeUnless { details.inspected }
    return listOf(
        IcodeFact("Packages", checking ?: details.installedPackages?.toString() ?: UNKNOWN),
        IcodeFact("PRoot", checking ?: details.prootVersion ?: UNKNOWN),
        IcodeFact("Runtime size", checking ?: details.rootfsSize?.bytes?.let(ByteSize::format) ?: storageSizeLabel(details.rootfsSize)),
        IcodeFact("Free on phone", checking ?: ByteSize.format(details.availableBytes)),
    )
}

/** What the runtime is doing, or why it cannot run. */
private fun summaryOf(runtime: RuntimeState): String = when (runtime) {
    RuntimeState.Unknown -> CHECKING
    is RuntimeState.Ready -> runtime.release
    is RuntimeState.NotInstalled -> runtime.reason
    is RuntimeState.Downloading -> runtime.label
    is RuntimeState.Installing -> runtime.label
    is RuntimeState.Failed -> runtime.reason
}

private const val UNKNOWN = "Not measured"

/** "12 of 13 installed": their names are on the tools' own page. */
private fun toolsCount(tools: RuntimeToolsState): String {
    if (tools.installed == null) return CHECKING
    return "${RuntimeTools.all.count(tools::isInstalled)} of ${RuntimeTools.all.size} installed"
}
