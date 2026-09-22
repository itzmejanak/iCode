package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
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
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.WorkspaceSummary
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Launcher with recent workspaces; [empty] renders the first-run empty state. */
@Composable
fun LauncherScreen(actions: DemoActions, empty: Boolean) {
    val workspaces = LocalWorkbenchSnapshot.current.workspaces
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = "iCode",
            actions = {
                IconButton(onClick = { actions.navigate(DemoScreen.SettingsRoot) }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = IcodeColors.TextPrimary)
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space4),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            LauncherActionTile(Icons.Outlined.FolderOpen, "Open Folder", Modifier.weight(1f)) { actions.navigate(DemoScreen.OpenFolderBrowser) }
            LauncherActionTile(Icons.Outlined.AccountTree, "Clone Repo", Modifier.weight(1f)) { actions.navigate(DemoScreen.CloneRepository) }
            LauncherActionTile(Icons.Outlined.CreateNewFolder, "New Folder", Modifier.weight(1f)) { actions.navigate(DemoScreen.CreateFolder) }
        }
        if (empty) {
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
            Column(
                modifier = Modifier.padding(horizontal = IcodeSpacing.Space4).weight(1f),
                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
            ) {
                SectionLabel("Recent Workspaces", Modifier.padding(bottom = IcodeSpacing.Space1))
                workspaces.forEach { workspace ->
                    WorkspaceRow(workspace) { actions.navigate(DemoScreen.EditorEmpty) }
                }
            }
        }
        StatusLabel(
            "RUNTIME HEALTHY",
            IcodeColors.Green,
            Modifier.align(Alignment.CenterHorizontally).padding(IcodeSpacing.Space5),
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
private fun WorkspaceRow(item: WorkspaceSummary, onClick: () -> Unit) {
    IcodeListRow(
        card = true,
        onClick = onClick,
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
            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = IcodeColors.TextSecondary,
                    modifier = Modifier.size(IcodeSizes.IconMd),
                )
                Text(item.lastOpened, style = IcodeType.Caption)
            }
        },
    )
}
