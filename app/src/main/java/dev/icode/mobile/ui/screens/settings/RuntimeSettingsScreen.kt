package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.RuntimeDetails
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

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
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val details = state.runtimeDetails
    var pending by rememberSaveable { mutableStateOf<RuntimeRebuild?>(null) }

    LaunchedEffect(Unit) { gateway.dispatch(WorkbenchIntent.RefreshRuntimeDetails) }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Runtime Environment", onBack = actions.back)
        Column(
            modifier = Modifier.fillMaxSize().padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4 + IcodeSpacing.Micro),
        ) {
            val healthy = state.runtime is RuntimeState.Ready
            NoticeCard(
                tone = if (healthy) IcodeTone.Success else IcodeTone.Warning,
                title = if (healthy) "Runtime Healthy" else "Runtime Unavailable",
                icon = if (healthy) Icons.Outlined.Check else Icons.Outlined.WarningAmber,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    details.release ?: "No base system is installed yet",
                    style = IcodeType.Caption,
                )
            }
            SectionLabel("Technical Details")
            IcodeCard(Modifier.fillMaxWidth()) {
                DetailRow("Architecture", details.architecture.ifBlank { UNKNOWN })
                DetailRow("Runtime Home", details.rootfsPath.ifBlank { UNKNOWN })
                DetailRow("Installed Packages", packageCount(details))
                DetailRow("Disk Usage", diskUsage(details))
                DetailRow("PRoot Version", details.prootVersion ?: UNKNOWN)
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton(
                "Repair Runtime",
                { pending = RuntimeRebuild.Repair },
                Modifier.fillMaxWidth(),
            )
            Text(
                "Reinstalls the base system and keeps your home directory. " +
                    "Packages you installed are not kept.",
                style = IcodeType.Caption,
            )
            DangerButton(
                "Reset Runtime",
                { pending = RuntimeRebuild.Reset },
                Modifier.fillMaxWidth(),
            )
            Text(
                "Removes everything, including your home directory and installed tools.",
                style = IcodeType.Caption,
            )
        }
    }

    pending?.let { rebuild ->
        IcodeConfirmDialog(
            title = if (rebuild == RuntimeRebuild.Repair) "Repair runtime" else "Reset runtime",
            message = if (rebuild == RuntimeRebuild.Repair) {
                "The base system is downloaded and rebuilt. Your home directory is kept; " +
                    "packages you installed are not. This cannot be undone."
            } else {
                "Everything in the runtime is removed and rebuilt, including your home " +
                    "directory and installed tools. This cannot be undone."
            },
            detail = details.release,
            confirmLabel = if (rebuild == RuntimeRebuild.Repair) "REPAIR" else "RESET",
            onConfirm = {
                gateway.dispatch(
                    if (rebuild == RuntimeRebuild.Repair) {
                        WorkbenchIntent.RepairRuntime
                    } else {
                        WorkbenchIntent.ResetRuntime
                    },
                )
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

private fun packageCount(details: RuntimeDetails): String =
    details.installedPackages?.let { "$it packages" } ?: UNKNOWN

/** Shows the measured size against real free space, or neither. */
private fun diskUsage(details: RuntimeDetails): String {
    val used = details.rootfsBytes ?: return UNKNOWN
    return "${ByteSize.format(used)} used · ${ByteSize.format(details.availableBytes)} free"
}

private const val UNKNOWN = "Not measured"
