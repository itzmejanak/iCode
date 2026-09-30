package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.icode.mobile.designsystem.ActivityRail
import dev.icode.mobile.designsystem.ActivityRailEntry
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.ClipboardBar
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
    // iCode's one clipboard: cut or copied here, in another workspace, or on the Workspaces page.
    val clipboard = state.clipboard
    val rootSelectionInteraction = remember { MutableInteractionSource() }
    val pasteInto = { target: String? -> target?.let { gateway.dispatch(WorkbenchIntent.PasteInto(it)) } }
    Column(Modifier.fillMaxSize()) {
        WorkspaceHeader(
            onNewItem = { type ->
                createParent = creationParentLocation(state.activeWorkspaceLocation, selectedPath, state.files)
                createType = type
            },
            pasteLabel = clipboard?.let { pasteLabel(it) },
            onPaste = { pasteInto(creationParentLocation(state.activeWorkspaceLocation, selectedPath, state.files)) },
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
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = rootSelectionInteraction,
                            indication = null,
                            onClickLabel = "Select workspace root",
                        ) { selectedPath = "" },
                ) {
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
        // The Workspaces page's bar too: into the selected folder, as the header's Paste.
        clipboard?.let { held ->
            ClipboardBar(held, onPaste = { pasteInto(creationParentLocation(state.activeWorkspaceLocation, selectedPath, state.files)) })
        }
    }
    createType?.let { type ->
        CreateWorkspaceItemDialog(type, createParent, onDismiss = { createType = null })
    }
    state.files.firstOrNull { it.path == renaming }?.let { entry ->
        RenameWorkspaceItemDialog(entry.path, entry.name, entry.kind == FileKind.Folder, onDismiss = { renaming = null })
    }
    state.files.firstOrNull { it.path == deleting }?.let { entry ->
        val shown = state.activeWorkspaceLocation?.let { entry.path.removePrefix("$it/") } ?: entry.path
        DeleteWorkspaceItemDialog(entry.path, entry.name, entry.kind == FileKind.Folder, shown, onDismiss = { deleting = null })
    }
}

/** Workspace header used by the explorer family. */
@Composable
private fun WorkspaceHeader(onNewItem: (WorkspaceItemType) -> Unit, pasteLabel: String?, onPaste: () -> Unit) {
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
                        pasteLabel?.let { IcodeMenuAction(it, Icons.Outlined.ContentPaste, { menuExpanded = false; onPaste() }) },
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
