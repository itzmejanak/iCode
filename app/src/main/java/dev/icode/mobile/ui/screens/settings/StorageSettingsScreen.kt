package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.WorkspaceActionsMenu

private enum class CleanupAction { Cache, StaleRecents }

/** Device totals and bounded app/workspace measurements, never fabricated sizes. */
@Composable
fun StorageSettingsScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val device = state.storageDetails?.device ?: state.storage
    val details = state.storageDetails
    var pendingCleanup by remember { mutableStateOf<CleanupAction?>(null) }

    LaunchedEffect(Unit) { gateway.dispatch(WorkbenchIntent.RefreshStorage) }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Storage Settings", subtitle = "Disk usage and workspace folders", onBack = actions.back)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2 + IcodeSpacing.Micro),
        ) {
            item { SectionLabel("Storage Usage") }
            item {
                IcodeCard(Modifier.fillMaxWidth()) {
                    if (device == null || device.totalBytes <= 0) {
                        Text("Storage usage is unavailable", style = IcodeType.Body)
                    } else {
                        val used = (device.totalBytes - device.availableBytes).coerceAtLeast(0)
                        val fraction = (used.toFloat() / device.totalBytes).coerceIn(0f, 1f)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${ByteSize.format(used)} Used", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                            Text("${ByteSize.format(device.availableBytes)} Free of ${ByteSize.format(device.totalBytes)}", style = IcodeType.Caption)
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space3)
                                .height(IcodeSpacing.Space2).background(IcodeColors.SurfaceTrack),
                        ) {
                            Box(Modifier.fillMaxWidth(fraction).height(IcodeSpacing.Space2).background(IcodeColors.Accent))
                        }
                        Text(
                            "Device storage, including other apps and files",
                            modifier = Modifier.padding(top = IcodeSpacing.Space2),
                            style = IcodeType.Caption,
                        )
                    }
                    Text(
                        "Runtime: ${storageSizeLabel(details?.runtime)}  ·  Cache: ${storageSizeLabel(details?.cache)}",
                        modifier = Modifier.padding(top = IcodeSpacing.Space2),
                        style = IcodeType.Caption,
                    )
                }
            }
            item { SectionLabel("Workspace Paths", Modifier.padding(top = IcodeSpacing.Space2)) }
            if (state.workspaces.isEmpty()) item {
                Text("No recent workspaces", style = IcodeType.Body)
            }
            items(state.workspaces, key = { it.location }) { workspace ->
                IcodeListRow(
                    card = true,
                    content = {
                        Column(Modifier.weight(1f)) {
                            Text(workspace.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                            MonoLine(workspace.path, style = IcodeType.MonoSmall)
                            MonoLine(
                                "${storageSizeLabel(details?.workspaceSizes?.get(workspace.location))}  · ${RelativeTime.since(workspace.lastOpenedAt)}",
                                color = IcodeColors.Accent,
                                style = IcodeType.MonoSmall,
                            )
                        }
                    },
                    trailing = { WorkspaceActionsMenu(workspace) },
                )
            }
            item { SectionLabel("Cleanup Operations", Modifier.padding(top = IcodeSpacing.Space2)) }
            item {
                TwoColumnActions(
                    left = { SecondaryButton("Clear Cache", { pendingCleanup = CleanupAction.Cache }, Modifier.fillMaxWidth()) },
                    right = { DangerButton("Remove Stale", { pendingCleanup = CleanupAction.StaleRecents }, Modifier.fillMaxWidth()) },
                )
            }
        }
    }

    pendingCleanup?.let { action ->
        IcodeConfirmDialog(
            title = if (action == CleanupAction.Cache) "Clear runtime cache?" else "Remove stale recents?",
            confirmLabel = if (action == CleanupAction.Cache) "Clear cache" else "Remove shortcuts",
            destructive = action == CleanupAction.StaleRecents,
            onConfirm = {
                gateway.dispatch(if (action == CleanupAction.Cache) WorkbenchIntent.ClearAppCache else WorkbenchIntent.RemoveStaleRecents)
                pendingCleanup = null
            },
            onDismiss = { pendingCleanup = null },
        ) {
            Text(
                if (action == CleanupAction.Cache)
                    "Removes cached runtime downloads and idle PRoot temporary files. Workspace files and recovery drafts stay untouched. Active runtime tasks must finish first."
                else "Removes only shortcuts to missing workspace folders from Recents. No workspace files are deleted.",
                style = IcodeType.Body,
            )
        }
    }
}
