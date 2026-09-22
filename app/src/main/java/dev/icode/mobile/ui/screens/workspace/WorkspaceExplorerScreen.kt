package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.demo.DemoContent
import dev.icode.mobile.designsystem.ActivityRail
import dev.icode.mobile.designsystem.ActivityRailEntry
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** File tree with the workspace activity rail. */
@Composable
fun WorkspaceExplorerScreen(actions: DemoActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    var selectedPath by rememberSaveable { mutableStateOf(DemoContent.selectedExplorerPath) }
    Column(Modifier.fillMaxSize()) {
        WorkspaceHeader(actions)
        Row(Modifier.weight(1f)) {
            ActivityRail(
                entries = listOf(
                    ActivityRailEntry(Icons.Outlined.FolderOpen, "Explorer", active = true),
                    ActivityRailEntry(Icons.Outlined.Search, "Search") { actions.navigate(DemoScreen.WorkspaceSearch) },
                    ActivityRailEntry(Icons.Outlined.AccountTree, "Source control") { actions.navigate(DemoScreen.SourceControl) },
                ),
            )
            Column(Modifier.weight(1f)) {
                SectionLabel("Workspace Explorer", Modifier.padding(IcodeSpacing.Space3))
                state.files.forEach { entry ->
                    ExplorerRow(
                        entry = entry,
                        selected = entry.path == selectedPath,
                        onClick = {
                            selectedPath = entry.path
                            if (entry.kind == FileKind.Folder) {
                                gateway.dispatch(WorkbenchIntent.ToggleFolder(entry.path))
                            } else {
                                actions.navigate(DemoScreen.WorkspaceEditor)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Workspace header used by the explorer family. */
@Composable
private fun WorkspaceHeader(actions: DemoActions) {
    val state = LocalWorkbenchSnapshot.current
    IcodeTopBar(
        title = state.activeWorkspace,
        subtitle = state.activePath,
        actions = {
            IconButton(onClick = { actions.navigate(DemoScreen.WorkspaceSettings) }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Workspace settings", tint = IcodeColors.TextPrimary)
            }
        },
    )
}

@Composable
private fun ExplorerRow(entry: FileEntry, selected: Boolean, onClick: () -> Unit) {
    val icon = when (entry.kind) {
        FileKind.Folder -> if (entry.expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
        FileKind.TypeScript -> Icons.Outlined.Code
        FileKind.Json, FileKind.Markdown -> Icons.Outlined.Description
    }
    val iconTint = when (entry.kind) {
        FileKind.TypeScript -> IcodeColors.Blue
        FileKind.Json -> IcodeColors.Amber
        FileKind.Markdown -> IcodeColors.TextSecondary
        FileKind.Folder -> if (entry.expanded) IcodeColors.Accent else IcodeColors.TextSecondary
    }
    IcodeListRow(
        selected = selected,
        indent = IcodeSpacing.IndentStep * entry.depth,
        onClick = onClick,
        leading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
            ) {
                if (entry.kind == FileKind.Folder) {
                    Icon(
                        if (entry.expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = IcodeColors.TextSecondary,
                        modifier = Modifier.size(IcodeSizes.IconSm),
                    )
                } else {
                    Spacer(Modifier.width(IcodeSizes.IconSm))
                }
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(IcodeSizes.IconMd))
            }
        },
        content = {
            Text(
                entry.name,
                modifier = Modifier.weight(1f),
                style = IcodeType.Body,
                color = if (entry.kind == FileKind.Markdown) IcodeColors.TextSecondary else IcodeColors.TextPrimary,
            )
        },
        trailing = {
            if (entry.gitStatus != null) {
                val tone = if (entry.gitStatus == "M" || entry.gitStatus == "•") IcodeColors.Amber else IcodeColors.Green
                MonoLine(entry.gitStatus, color = tone, style = IcodeType.MonoSmall)
            }
        },
    )
}
