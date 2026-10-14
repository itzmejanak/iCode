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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
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
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.ui.ClipboardBar
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.NewItemButton
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** File tree with the workspace activity rail. */
@Composable
fun WorkspaceExplorerScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    var selectedPath by rememberSaveable(state.activePath) { mutableStateOf(state.initialExplorerSelection.orEmpty()) }
    var createParent by rememberSaveable { mutableStateOf<String?>(null) }
    var createType by rememberSaveable { mutableStateOf<WorkspaceItemType?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    // iCode's one clipboard: cut or copied here, in another workspace, or on the Workspaces page.
    val clipboard = state.clipboard
    val rootSelectionInteraction = remember { MutableInteractionSource() }
    val root = state.activeWorkspaceLocation
    // The folder + makes in and the header's and the bar's Paste go into: the selected folder, or the selected file's.
    val selectedFolder = creationParentLocation(root, selectedPath, state.files)
    val pasteHere = { selectedFolder?.let { gateway.dispatch(ICodeIntent.PasteInto(it)) } }
    // Each path as the runtime names it, as Copy path gives it and the terminal shows it.
    val runtimePathOf = { location: String -> root?.let { WorkspaceRuntimePath.within(it, state.activePath, location) } ?: location }
    Column(Modifier.fillMaxSize()) {
        WorkspaceHeader(
            actions = FileItemActions.creation(
                onNewFile = { createParent = selectedFolder; createType = WorkspaceItemType.File },
                onNewFolder = { createParent = selectedFolder; createType = WorkspaceItemType.Folder },
                clipboard = clipboard,
                onPaste = { pasteHere() },
            ),
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
                            gateway.dispatch(ICodeIntent.InitializeGit)
                        } else {
                            actions.navigate(Route.SourceControl)
                        }
                    },
                    // Accented while it works, wherever the user went meanwhile.
                    ActivityRailEntry(Icons.Outlined.AutoAwesome, "Assistant", accented = state.assistant.running) {
                        actions.navigate(Route.Assistant)
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
                                actions = explorerItemActions(
                                    entry, root, state.activePath, state.files, clipboard,
                                    onRename = { renaming = entry.path },
                                    onDelete = { deleting = entry.path },
                                ),
                                menuOpen = menuFor == entry.path,
                                onMenuDismiss = { menuFor = null },
                                onClick = {
                                    selectedPath = entry.path
                                    if (entry.kind == FileKind.Folder) {
                                        gateway.dispatch(ICodeIntent.ToggleFolder(entry.path))
                                    } else {
                                        gateway.dispatch(ICodeIntent.OpenFile(entry.path))
                                    }
                                },
                                onLongClick = {
                                    selectedPath = entry.path
                                    menuFor = entry.path
                                },
                            )
                        }
                    }
                }
            }
        }
        // The Workspaces page's bar too: into the selected folder, as the header's Paste, and it says so.
        if (clipboard != null && selectedFolder != null) {
            ClipboardBar(clipboard, destination = runtimePathOf(selectedFolder), onPaste = { pasteHere() })
        }
    }
    createType?.let { type ->
        CreateWorkspaceItemDialog(type, createParent, onDismiss = { createType = null })
    }
    state.files.firstOrNull { it.path == renaming }?.let { entry ->
        RenameWorkspaceItemDialog(entry.path, entry.name, entry.kind == FileKind.Folder, onDismiss = { renaming = null })
    }
    state.files.firstOrNull { it.path == deleting }?.let { entry ->
        DeleteWorkspaceItemDialog(entry.path, entry.name, entry.kind == FileKind.Folder, runtimePathOf(entry.path), onDismiss = { deleting = null })
    }
}

/** Workspace header used by the explorer family: its + offers [actions] ([FileItemActions.creation]). */
@Composable
private fun WorkspaceHeader(actions: List<IcodeMenuAction>) {
    val state = LocalICodeSnapshot.current
    IcodeTopBar(
        title = state.activeWorkspace,
        subtitle = state.activePath,
        actions = { NewItemButton(contentDescription = "Create in workspace", actions = actions) },
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
