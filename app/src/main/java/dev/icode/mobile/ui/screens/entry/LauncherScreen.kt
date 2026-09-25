package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeCard
import androidx.compose.foundation.clickable
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.FolderBrowserPurpose
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkspaceSummary
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.WorkspaceActionsMenu

/** Launcher with recent workspaces; [empty] renders the first-run empty state. */
@Composable
fun LauncherScreen(actions: NavigationActions, empty: Boolean) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val workspaces = state.workspaces
    val showEmpty = empty || workspaces.isEmpty()
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = "iCode",
            actions = {
                val unread = state.notices.any { !it.read }
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
        // Declining the permission during onboarding used to be a one-way
        // door: the launcher looked identical, every action failed, and no
        // screen led back. The way back lives here now.
        if (state.systemAccess?.storage == false) {
            NoticeCard(
                tone = IcodeTone.Warning,
                modifier = Modifier.fillMaxWidth().padding(
                    start = IcodeSpacing.Space4,
                    end = IcodeSpacing.Space4,
                    top = IcodeSpacing.Space4,
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    StatusLabel("Storage access is off", IcodeColors.Amber)
                    MonoLine(
                        "iCode cannot open folders or clone repositories until Android grants it.",
                        color = IcodeColors.TextSecondary,
                        style = IcodeType.MonoSmall,
                    )
                    SecondaryButton(
                        "Grant access",
                        { gateway.dispatch(WorkbenchIntent.RequestSystemPermission(SystemPermission.Storage)) },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space4),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            LauncherActionTile(Icons.Outlined.FolderOpen, "Open Folder", Modifier.weight(1f)) {
                gateway.dispatch(WorkbenchIntent.StartFolderBrowser(FolderBrowserPurpose.OpenWorkspace))
            }
            LauncherActionTile(Icons.Outlined.AccountTree, "Clone Repo", Modifier.weight(1f)) { actions.navigate(Route.CloneRepository) }
            LauncherActionTile(Icons.Outlined.CreateNewFolder, "New Folder", Modifier.weight(1f)) { actions.navigate(Route.CreateFolder) }
        }
        if (showEmpty) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    title = "No recent workspaces",
                    description = "Open a folder, clone a repository, or create a new project\nto get started.",
                    icon = Icons.Outlined.Folder,
                    modifier = Modifier.padding(top = IcodeSpacing.Space6 + IcodeSpacing.Space2),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = IcodeSpacing.Space4).weight(1f),
                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
            ) {
                item { SectionLabel("Recent Workspaces", Modifier.padding(bottom = IcodeSpacing.Space1)) }
                items(workspaces, key = { it.location }) { workspace ->
                    WorkspaceRow(
                        item = workspace,
                        onOpen = { gateway.dispatch(WorkbenchIntent.OpenWorkspace(workspace.location)) },
                    )
                }
            }
        }
        // An unavailable runtime is something the user can act on, so the
        // status is the way in rather than a label reporting a dead end.
        StatusLabel(
            if (state.runtimeHealthy) "RUNTIME HEALTHY" else "RUNTIME UNAVAILABLE — INSTALL",
            if (state.runtimeHealthy) IcodeColors.Green else IcodeColors.Amber,
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

@Composable
private fun WorkspaceRow(
    item: WorkspaceSummary,
    onOpen: () -> Unit,
) {
    IcodeListRow(
        card = true,
        cardPadding = PaddingValues(
            horizontal = IcodeSpacing.Space3,
            vertical = (IcodeSizes.Tile - IcodeSizes.TouchTarget) / 2,
        ),
        onClick = onOpen,
        leading = {
            IconBadge(
                icon = if (item.available) Icons.Outlined.Folder else Icons.Outlined.WarningAmber,
                tint = if (item.available) IcodeColors.Accent else IcodeColors.Amber,
                size = IcodeSizes.BadgeMd,
                iconSize = IcodeSizes.IconMd,
            )
        },
        content = {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
                ) {
                    Text(item.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                    if (!item.available) Text("• UNAVAILABLE", style = IcodeType.Micro.copy(color = IcodeColors.Amber))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                ) {
                    MonoLine(item.path, modifier = Modifier.weight(1f), style = IcodeType.MonoSmall)
                    if (item.branch != null) {
                        Text(item.branch, style = IcodeType.MonoMicro.copy(color = IcodeColors.Accent))
                    }
                }
            }
        },
        trailing = {
            Box {
                WorkspaceActionsMenu(item)
                Text(
                    RelativeTime.since(item.lastOpenedAt),
                    style = IcodeType.Caption,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = IcodeSpacing.Space1),
                )
            }
        },
    )
}
