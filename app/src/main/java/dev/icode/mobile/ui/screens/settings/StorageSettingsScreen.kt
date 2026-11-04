package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.ShareTrack
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.WorkspaceListRules
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.domain.files.TrashRules
import dev.icode.mobile.domain.storageSizeLabel
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository
import dev.icode.mobile.ui.screens.entry.icon

/**
 * Settings › Storage (docs/explorer-gaps-plan.md §3.15), one list: the device's use on a bar, then
 * what iCode keeps — the workspaces, which open Private; the runtime; Trash, which opens its view; the
 * cache, cleared when asked first — and Manage storage. Then the explorers' hidden files, and All
 * Files Access, Android's own setting, which iCode shows and opens. Sizes are measured, never guessed.
 */
@Composable
fun StorageSettingsScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val preferences = LocalWorkspacePreferences.current
    val repository = LocalWorkspacePreferencesRepository.current
    val saver = rememberSettingSaver(preferences, "Could not save setting")
    val device = state.storageDetails?.device ?: state.storage
    val details = state.storageDetails
    var confirmClearCache by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        gateway.dispatch(ICodeIntent.RefreshStorage)
        gateway.dispatch(ICodeIntent.MeasureWorkspaces)
        gateway.dispatch(FilesIntent.ReadTrash)
    }
    // The explorer, where [going] takes it.
    val explore = { going: ICodeIntent ->
        gateway.dispatch(going)
        actions.navigate(Route.Workspaces)
    }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Storage Settings", subtitle = settingsSubtitle(Route.StorageSettings), onBack = actions.back)
        IcodePageColumn {
            saver.error?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
            IcodeSection("Storage") {
                IcodeCard(Modifier.fillMaxWidth()) {
                    if (device == null || device.totalBytes <= 0) {
                        Text("Storage usage is unavailable", style = IcodeType.Body)
                    } else {
                        val used = (device.totalBytes - device.availableBytes).coerceAtLeast(0)
                        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                            ShareTrack(listOf(used.toFloat() / device.totalBytes to IcodeColors.Accent), Modifier.fillMaxWidth())
                            Text(
                                "${ByteSize.format(used)} used · ${ByteSize.format(device.availableBytes)} free of ${ByteSize.format(device.totalBytes)}",
                                style = IcodeType.Caption,
                            )
                        }
                    }
                }
                SettingRow(
                    "Workspaces",
                    Modifier.fillMaxWidth(),
                    subtitle = "${WorkspaceListRules.summary(state.workspaces, state.workspaceSizes)} · removed with iCode unless you keep its data",
                    leadingIcon = Icons.Outlined.FolderCopy,
                    onClick = { explore(ICodeIntent.BrowsePlace(StoragePlace.Private)) },
                )
                SettingRow("Runtime", Modifier.fillMaxWidth(), leadingIcon = Icons.Outlined.Storage) {
                    Text(storageSizeLabel(details?.runtime), style = IcodeType.Mono)
                }
                val trash = TrashRules.summary(state.explorer.trash)
                SettingRow(
                    "Trash",
                    Modifier.fillMaxWidth(),
                    subtitle = "${countOf(trash.items, "item")} · each kept ${TrashRules.KEEP_DAYS} days",
                    leadingIcon = ExplorerView.Trash.icon,
                    onClick = { explore(ICodeIntent.BrowseView(ExplorerView.Trash)) },
                ) {
                    Text(ByteSize.format(trash.bytes), style = IcodeType.Mono)
                }
                SettingRow("Cache", Modifier.fillMaxWidth(), subtitle = storageSizeLabel(details?.cache), leadingIcon = Icons.Outlined.Cached) {
                    IcodeTextButton("Clear", { confirmClearCache = true })
                }
                SettingRow(
                    "Manage storage",
                    Modifier.fillMaxWidth(),
                    subtitle = "Every kind of file, the largest, duplicates",
                    leadingIcon = Icons.Outlined.DataUsage,
                    onClick = { actions.navigate(Route.ManageStorage) },
                )
            }
            IcodeSection("Files") {
                SettingRow("Show Hidden Files", Modifier.fillMaxWidth(), subtitle = "Names starting with a dot, in both explorers", leadingIcon = Icons.Outlined.Visibility) {
                    IcodeToggle(preferences.explorer.showHidden) { saver.save { repository.setShowHidden(it) } }
                }
            }
            // Android's own setting; iCode shows what it reads and opens the page to change it.
            IcodeSection("Phone Storage") {
                val allowed = state.systemAccess?.storage == true
                SettingRow(
                    "All Files Access",
                    Modifier.fillMaxWidth(),
                    subtitle = if (allowed) "The file explorer and the assistant can read phone storage" else "Off: the file explorer asks for it at Shared",
                    onClick = { gateway.dispatch(ICodeIntent.OpenSystemSettings(SystemPermission.Storage)) },
                ) {
                    AccessState(if (allowed) "ALLOWED" else "OFF", allowed = allowed)
                }
            }
        }
    }

    if (confirmClearCache) {
        IcodeConfirmSheet(
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
