package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import dev.icode.mobile.designsystem.ActivityRail
import dev.icode.mobile.designsystem.ActivityRailEntry
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.gitStatusTone
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** File tree with the workspace activity rail. */
@Composable
fun WorkspaceExplorerScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    var selectedPath by rememberSaveable(state.activePath) { mutableStateOf(state.initialExplorerSelection.orEmpty()) }
    var createParent by rememberSaveable { mutableStateOf<String?>(null) }
    var createType by rememberSaveable { mutableStateOf<WorkspaceItemType?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    // Cut or copied, per workspace: a path from another workspace cannot be pasted here.
    var clipPath by rememberSaveable(state.activeWorkspaceLocation) { mutableStateOf<String?>(null) }
    var clipCut by rememberSaveable(state.activeWorkspaceLocation) { mutableStateOf(false) }
    val clipboard = clipPath?.let { ExplorerClipboard(it, clipCut) }
    val pasteInto = { target: String? ->
        if (clipboard != null && target != null) {
            val left = paste(clipboard, target, gateway::dispatch)
            clipPath = left?.path
        }
    }
    Column(Modifier.fillMaxSize()) {
        WorkspaceHeader(
            onNewItem = { type ->
                createParent = creationParentLocation(state.activeWorkspaceLocation, selectedPath, state.files)
                createType = type
            },
            onPaste = if (clipboard == null) null else {
                { pasteInto(creationParentLocation(state.activeWorkspaceLocation, selectedPath, state.files)) }
            },
        )
        Row(Modifier.weight(1f).background(IcodeColors.Surface)) {
            ActivityRail(
                entries = listOf(
                    ActivityRailEntry(Icons.Outlined.FolderOpen, "Explorer", active = true),
                    ActivityRailEntry(Icons.Outlined.Terminal, "Terminal", accented = true) {
                        actions.openPanel(ToolPanelTab.Terminal)
                    },
                    ActivityRailEntry(Icons.Outlined.Search, "Search") { actions.navigate(Route.WorkspaceSearch) },
                    ActivityRailEntry(
                        Icons.Outlined.AccountTree,
                        if (state.gitRepository == null) "Initialize Git" else "Source control",
                        // One badge per changed file, counting a file staged and
                        // modified once, the way VS Code counts it.
                        badge = state.gitChanges.map { it.path }.distinct().size,
                    ) {
                        if (state.gitRepository == null) {
                            gateway.dispatch(WorkbenchIntent.InitializeGit)
                        } else {
                            actions.navigate(Route.SourceControl)
                        }
                    },
                ),
                bottomEntries = listOf(
                    ActivityRailEntry(Icons.Outlined.Settings, "Workspace settings") {
                        actions.navigate(Route.WorkspaceSettings)
                    },
                ),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(IcodeShapes.RailContent)
                    .background(IcodeColors.Background),
            ) {
                SectionLabel("Workspace Explorer", Modifier.padding(IcodeSpacing.Space3))
                LazyColumn(Modifier.weight(1f)) {
                    if (state.files.isEmpty() && state.activeWorkspaceLocation != null) {
                        item {
                            Text(
                                "This folder is empty. Create a file or folder with +.",
                                modifier = Modifier.padding(IcodeSpacing.Space3),
                                style = IcodeType.Caption,
                            )
                        }
                    }
                    items(state.files, key = { it.path }) { entry ->
                        // The menu anchors to its row, where the long press was.
                        Box {
                            ExplorerRow(
                                entry = entry,
                                selected = entry.path == selectedPath,
                                onClick = {
                                    selectedPath = entry.path
                                    if (entry.kind == FileKind.Folder) {
                                        gateway.dispatch(WorkbenchIntent.ToggleFolder(entry.path))
                                    } else {
                                        gateway.dispatch(WorkbenchIntent.OpenFile(entry.path))
                                    }
                                },
                                onLongClick = {
                                    selectedPath = entry.path
                                    menuFor = entry.path
                                },
                            )
                            ExplorerItemMenu(
                                entry = entry,
                                expanded = menuFor == entry.path,
                                clipboard = clipboard,
                                onDismiss = { menuFor = null },
                                onRename = { menuFor = null; renaming = entry.path },
                                onClipboard = { clip ->
                                    menuFor = null
                                    clipPath = clip.path
                                    clipCut = clip.cut
                                },
                                onPaste = {
                                    menuFor = null
                                    pasteInto(pasteTarget(state.activeWorkspaceLocation, entry, state.files))
                                },
                                onDelete = { menuFor = null; deleting = entry.path },
                            )
                        }
                    }
                }
            }
        }
    }
    createType?.let { type ->
        CreateWorkspaceItemDialog(type, createParent, onDismiss = { createType = null })
    }
    state.files.firstOrNull { it.path == renaming }?.let { entry ->
        RenameWorkspaceItemDialog(entry, onDismiss = { renaming = null })
    }
    state.files.firstOrNull { it.path == deleting }?.let { entry ->
        DeleteWorkspaceItemDialog(entry, onDismiss = { deleting = null })
    }
}

/** Workspace header used by the explorer family. */
@Composable
private fun WorkspaceHeader(onNewItem: (WorkspaceItemType) -> Unit, onPaste: (() -> Unit)?) {
    val state = LocalWorkbenchSnapshot.current
    var menuExpanded by remember { mutableStateOf(false) }
    IcodeTopBar(
        title = state.activeWorkspace,
        subtitle = state.activePath,
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create in workspace", tint = IcodeColors.TextPrimary)
                }
                IcodeActionMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    actions = listOfNotNull(
                        IcodeMenuAction("New file", Icons.Outlined.Description, { onNewItem(WorkspaceItemType.File) }),
                        IcodeMenuAction("New folder", Icons.Outlined.CreateNewFolder, { onNewItem(WorkspaceItemType.Folder) }),
                        onPaste?.let { IcodeMenuAction("Paste", Icons.Outlined.ContentPaste, { menuExpanded = false; it() }) },
                    ),
                )
            }
        },
    )
}

/** A selected file targets its containing visible directory; a folder targets itself. */
internal fun creationParentLocation(root: String?, selectedPath: String, files: List<FileEntry>): String? {
    val index = files.indexOfFirst { it.path == selectedPath }
    if (index < 0) return root
    val selected = files[index]
    if (selected.kind == FileKind.Folder) return selected.path
    return files.take(index).lastOrNull { it.kind == FileKind.Folder && it.depth == selected.depth - 1 }?.path ?: root
}

@Composable
private fun ExplorerRow(entry: FileEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val icon = when (entry.kind) {
        FileKind.Folder -> if (entry.expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
        FileKind.TypeScript -> Icons.Outlined.Code
        FileKind.Code -> Icons.Outlined.Code
        FileKind.Json, FileKind.Markdown, FileKind.Other -> Icons.Outlined.Description
    }
    val iconTint = when (entry.kind) {
        FileKind.TypeScript -> IcodeColors.Blue
        FileKind.Code -> IcodeColors.Accent
        FileKind.Json -> IcodeColors.Amber
        FileKind.Markdown, FileKind.Other -> IcodeColors.TextSecondary
        FileKind.Folder -> if (entry.expanded) IcodeColors.Accent else IcodeColors.TextSecondary
    }
    IcodeListRow(
        selected = selected,
        selectionBackground = IcodeColors.Surface,
        rowShape = RectangleShape,
        indent = IcodeSpacing.IndentStep * entry.depth,
        onClick = onClick,
        onLongClick = onLongClick,
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
        // As in VS Code: a changed name takes its status colour; a file shows
        // its status letter, a folder a dot for the strongest change beneath it.
        content = {
            Text(
                entry.name,
                modifier = Modifier.weight(1f),
                style = IcodeType.Body,
                color = when {
                    entry.gitStatus != null -> gitStatusTone(entry.gitStatus).tint()
                    entry.kind == FileKind.Markdown -> IcodeColors.TextSecondary
                    else -> IcodeColors.TextPrimary
                },
            )
        },
        trailing = {
            val status = entry.gitStatus
            when {
                status == null -> Unit
                entry.kind == FileKind.Folder -> StatusDot(gitStatusTone(status).tint())
                else -> MonoLine(status, color = gitStatusTone(status).tint(), style = IcodeType.MonoSmall)
            }
        },
    )
}
