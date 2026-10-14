package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeCard
import androidx.compose.foundation.clickable
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IcodeWatermark
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.StoragePlace
import dev.icode.mobile.domain.WorkspaceListRules
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.WorkspaceRow

/**
 * The launcher: the recently opened workspaces (docs/workspaces-page-plan.md §2.1), Import and
 * Clone, and the way to the Workspaces page, where every workspace is managed and New Folder lives.
 * [empty] renders the first-run empty state.
 */
@Composable
fun LauncherScreen(actions: NavigationActions, empty: Boolean) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val workspaces = state.workspaces
    val showEmpty = empty || workspaces.isEmpty()
    val explore = { place: StoragePlace ->
        gateway.dispatch(ICodeIntent.BrowsePlace(place))
        actions.navigate(Route.Workspaces)
    }
    val showIn = { folder: String ->
        gateway.dispatch(ICodeIntent.BrowseFiles(folder))
        actions.navigate(Route.Workspaces)
    }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = "iCode",
            actions = {
                // Unsaved work waiting in Document Recovery keeps the dot until it is resolved.
                val unread = state.notices.any { !it.read } || state.recoverableDrafts.isNotEmpty()
                IconButton(onClick = { actions.navigate(Route.Notifications) }) {
                    Box {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = if (unread) "Notifications, unread" else "Notifications",
                            tint = IcodeColors.TextPrimary,
                        )
                        if (unread) Box(Modifier.align(Alignment.TopEnd)) { StatusDot(IcodeColors.Accent) }
                    }
                }
                IconButton(onClick = { actions.navigate(Route.SettingsRoot) }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = IcodeColors.TextPrimary)
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space4),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            // Import and Workspaces open the one file explorer, at phone storage and at iCode's (docs/storage-explorer-plan.md §1).
            LauncherActionTile(Icons.Outlined.FolderOpen, "Import", Modifier.weight(1f)) { explore(StoragePlace.Shared) }
            LauncherActionTile(Icons.Outlined.AccountTree, "Clone Repo", Modifier.weight(1f)) { actions.navigate(Route.CloneRepository) }
            LauncherActionTile(Icons.Outlined.FolderCopy, "Workspaces", Modifier.weight(1f)) { explore(StoragePlace.Private) }
        }
        // The logo as a letterpress in the free space, as VS Code's empty editor: workspaces
        // cover it as they fill the page.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            IcodeWatermark(Modifier.align(Alignment.Center))
            if (showEmpty) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        title = "No workspaces yet",
                        description = "Copy a folder in from phone storage with Import, clone\na repository, or make a new one in Workspaces.",
                        icon = Icons.Outlined.Folder,
                        modifier = Modifier.padding(top = IcodeSpacing.Space6 + IcodeSpacing.Space2),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = IcodeSpacing.Space4),
                    verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                ) {
                    item { SectionLabel("Recent", Modifier.padding(bottom = IcodeSpacing.Space1)) }
                    items(WorkspaceListRules.recent(workspaces), key = { it.location }) { workspace ->
                        // Shown where it is listed: the top of iCode's storage, the folder holding it.
                        WorkspaceRow(workspace, onShowInList = { showIn(workspace.location.substringBeforeLast('/')) })
                    }
                }
            }
        }
        // An unavailable runtime is something the user can act on, so the
        // status is the way in rather than a label reporting a dead end. Healthy
        // wears the accent the user chose (owner, 2026-09-30); unavailable stays a warning.
        StatusLabel(
            if (state.runtimeHealthy) "RUNTIME HEALTHY" else "RUNTIME UNAVAILABLE — INSTALL",
            if (state.runtimeHealthy) IcodeColors.Accent else IcodeColors.Amber,
            Modifier
                .align(Alignment.CenterHorizontally)
                .then(
                    if (state.runtimeHealthy) {
                        Modifier
                    } else {
                        Modifier.clickable { actions.navigate(Route.RuntimeInstalling) }
                    },
                )
                .padding(IcodeSpacing.Space5),
        )
    }
}

@Composable
private fun LauncherActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    IcodeCard(modifier = modifier.height(IcodeSizes.Tile), onClick = onClick) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconXl))
            Text(label, style = IcodeType.Label.copy(color = IcodeColors.TextPrimary))
        }
    }
}
