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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.WorkspaceListRules
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.domain.storageSizeLabel
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** Device totals and bounded app/workspace measurements, never fabricated sizes. */
@Composable
fun StorageSettingsScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val device = state.storageDetails?.device ?: state.storage
    val details = state.storageDetails
    var confirmClearCache by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        gateway.dispatch(ICodeIntent.RefreshStorage)
        gateway.dispatch(ICodeIntent.MeasureWorkspaces)
    }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Storage Settings", subtitle = settingsSubtitle(Route.StorageSettings), onBack = actions.back)
        IcodePageColumn {
            IcodeSection("Storage Usage") {
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
            IcodeSection("Workspaces") {
                Text(
                    "Workspaces live in iCode's storage. Uninstalling iCode removes them unless you choose to keep its data.",
                    style = IcodeType.Caption,
                )
                // The workspaces themselves are the Workspaces page's: one list, not two.
                SettingRow(
                    "Workspaces",
                    Modifier.fillMaxWidth(),
                    subtitle = WorkspaceListRules.summary(state.workspaces, state.workspaceSizes),
                    leadingIcon = Icons.Outlined.FolderCopy,
                    onClick = {
                        gateway.dispatch(ICodeIntent.BrowsePlace(StoragePlace.Private))
                        actions.navigate(Route.Workspaces)
                    },
                )
            }
            // Android's own setting; iCode shows what it reads and opens the page to change it.
            IcodeSection("Phone Storage") {
                val allowed = state.systemAccess?.storage == true
                SettingRow(
                    "All Files Access",
                    Modifier.fillMaxWidth(),
                    subtitle = if (allowed) "The file explorer can open Shared, phone storage" else "Off: the file explorer asks for it at Shared",
                    onClick = { gateway.dispatch(ICodeIntent.OpenSystemSettings(SystemPermission.Storage)) },
                ) {
                    AccessState(if (allowed) "ALLOWED" else "OFF", allowed = allowed)
                }
            }
            IcodeSection("Cleanup Operations") {
                SecondaryButton("Clear Cache", { confirmClearCache = true }, Modifier.fillMaxWidth())
            }
        }
    }

    if (confirmClearCache) {
        IcodeConfirmDialog(
            title = "Clear runtime cache?",
            confirmLabel = "Clear cache",
            onConfirm = {
                gateway.dispatch(ICodeIntent.ClearAppCache)
                confirmClearCache = false
            },
            onDismiss = { confirmClearCache = false },
        ) {
            Text(
                "Removes cached runtime downloads and idle PRoot temporary files. Workspace files and recovery drafts stay untouched. Active runtime tasks must finish first.",
                style = IcodeType.Body,
            )
        }
    }
}
