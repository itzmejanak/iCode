package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.CHECKING
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RuntimeDetails
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.RuntimeToolsState
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.domain.storageSizeLabel
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** Which rebuild the user asked for; both are destructive, so both confirm. */
private enum class RuntimeRebuild { Repair, Reset }

/**
 * Runtime health, what is actually installed, and the way out of a broken one.
 *
 * An interrupted package operation can leave a runtime that cannot repair
 * itself from inside — measured on device — so rebuilding it has to be
 * reachable here rather than only by clearing the app's data.
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

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Runtime Environment", onBack = actions.back)
        // The page's rhythm, without scrolling: the rebuilds stay at its foot.
        Column(
            modifier = Modifier.fillMaxSize().padding(IcodeSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.SectionGap),
        ) {
            val healthy = state.runtime is RuntimeState.Ready
            NoticeCard(
                tone = if (healthy) IcodeTone.Success else IcodeTone.Warning,
                title = if (healthy) "Runtime Healthy" else "Runtime Unavailable",
                icon = if (healthy) Icons.Outlined.Check else Icons.Outlined.WarningAmber,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(runtimeSummary(state.runtime), style = IcodeType.Caption)
            }
            IcodeSection("Technical Details") {
                IcodeCard(Modifier.fillMaxWidth()) {
                    // Until the first read lands, every row says so rather than "Not measured".
                    val checking = CHECKING.takeUnless { details.inspected }
                    DetailRow("Architecture", checking ?: details.architecture.ifBlank { UNKNOWN })
                    DetailRow("Runtime Home", checking ?: details.rootfsPath.ifBlank { UNKNOWN })
                    DetailRow("Installed Packages", checking ?: packageCount(details))
                    DetailRow("Disk Usage", checking ?: diskUsage(details))
                    DetailRow("PRoot Version", checking ?: details.prootVersion ?: UNKNOWN)
                    DetailRow("Manifest", state.web.manifest.let { if (it.fromWebsite) "iCode website · rev ${it.revision ?: 1}" else "Built-in" })
                }
                SettingRow(
                    "Runtime Tools",
                    modifier = Modifier.fillMaxWidth(),
                    subtitle = toolsSummary(state.runtimeTools),
                    leadingIcon = Icons.Outlined.Extension,
                    onClick = { actions.navigate(Route.RuntimeTools) },
                )
            }
            Spacer(Modifier.weight(1f))
            // From the confirmed tap until the runtime is rebuilt: neither starts twice, and the one asked says it runs.
            val rebuilding = state.isBusy(BusyAction.Runtime) || state.runtime is RuntimeState.Downloading || state.runtime is RuntimeState.Installing
            val running = when (state.runtimeInstallReason) {
                RuntimeInstallReason.Repair -> RuntimeRebuild.Repair
                RuntimeInstallReason.Reset -> RuntimeRebuild.Reset
                RuntimeInstallReason.Initial -> confirmed
            }.takeIf { rebuilding }
            IcodeSection {
                SecondaryButton(
                    "Repair Runtime",
                    { pending = RuntimeRebuild.Repair },
                    Modifier.fillMaxWidth(),
                    enabled = !rebuilding,
                    busy = running == RuntimeRebuild.Repair,
                )
                Text(
                    "Reinstalls the base system and keeps your home directory and workspaces. " +
                        "Packages you installed are not kept.",
                    style = IcodeType.Caption,
                )
            }
            IcodeSection {
                DangerButton(
                    "Reset Runtime",
                    { pending = RuntimeRebuild.Reset },
                    Modifier.fillMaxWidth(),
                    enabled = !rebuilding,
                    busy = running == RuntimeRebuild.Reset,
                )
                Text(
                    "Removes everything in the runtime, including your home directory and installed tools. Workspaces are kept.",
                    style = IcodeType.Caption,
                )
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

private fun packageCount(details: RuntimeDetails): String =
    details.installedPackages?.let { countOf(it, "package") } ?: UNKNOWN

/** The measured size, or where its measurement stands, against real free space. */
private fun diskUsage(details: RuntimeDetails): String {
    val used = details.rootfsSize?.bytes?.let { "${ByteSize.format(it)} used" } ?: storageSizeLabel(details.rootfsSize)
    return "$used · ${ByteSize.format(details.availableBytes)} free"
}

/** What the runtime is right now, known from start-up, before any detail is read. */
private fun runtimeSummary(runtime: RuntimeState): String = when (runtime) {
    RuntimeState.Unknown -> CHECKING
    is RuntimeState.Ready -> runtime.release
    is RuntimeState.NotInstalled -> runtime.reason
    is RuntimeState.Downloading -> runtime.label
    is RuntimeState.Installing -> runtime.label
    is RuntimeState.Failed -> runtime.reason
}

private const val UNKNOWN = "Not measured"

/** "2 of 6 installed: Git, SSH client". */
private fun toolsSummary(tools: RuntimeToolsState): String {
    if (tools.installed == null) return CHECKING
    val installed = RuntimeTools.all.filter(tools::isInstalled)
    val count = "${installed.size} of ${RuntimeTools.all.size} installed"
    return if (installed.isEmpty()) count else "$count: ${installed.joinToString { it.name }}"
}
