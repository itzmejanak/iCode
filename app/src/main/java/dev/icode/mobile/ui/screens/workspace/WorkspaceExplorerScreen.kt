package dev.icode.mobile.ui.screens.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.ActivityRail
import dev.icode.mobile.designsystem.ActivityRailEntry
import dev.icode.mobile.designsystem.BarLeave
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.DiagnosticRules
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ToolBadges
import dev.icode.mobile.domain.WorkspaceTool
import dev.icode.mobile.domain.files.FileSelection
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.ClipboardBar
import dev.icode.mobile.ui.ExplorerSelection
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.NewItemButton
import dev.icode.mobile.ui.SelectionActions
import dev.icode.mobile.ui.SelectionBar
import dev.icode.mobile.ui.among
import dev.icode.mobile.ui.dot
import dev.icode.mobile.ui.icon
import dev.icode.mobile.ui.rememberSelection
import dev.icode.mobile.ui.selectionActions
import dev.icode.mobile.ui.tint

/**
 * File tree with the workspace activity rail. Select in a row's sheet starts a selection
 * (docs/explorer-gaps-plan.md §3.2): rows tick with a tap, the + gives way to Select all and Invert,
 * and its foot bar to the selection's. A folder ticked covers what is in it, so nothing is acted on
 * twice. Back, or ✕, leaves it; so does going to another workspace.
 */
@Composable
fun WorkspaceExplorerScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    var selectedPath by rememberSaveable(state.activePath) { mutableStateOf(state.initialExplorerSelection.orEmpty()) }
    var createParent by rememberSaveable { mutableStateOf<String?>(null) }
    var createType by rememberSaveable { mutableStateOf<WorkspaceItemType?>(null) }
    // The row whose sheet is shown; gone with the row (renamed, deleted, moved).
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    val selection = rememberSelection(state.activePath)
    // What the selection acts on: none under a folder that is itself ticked.
    val covered = selection.among(state.files) { it.path }?.let { ticked ->
        FileSelection.covering(ticked.map { it.path }).mapNotNull { path -> ticked.firstOrNull { it.path == path } }
    }
    BackHandler(enabled = selection.selecting) { selection.leave() }
    // iCode's one clipboard: cut or copied here, in another workspace, or on the Workspaces page.
    val clipboard = state.explorer.clipboard
    val rootSelectionInteraction = remember { MutableInteractionSource() }
    val root = state.activeWorkspaceLocation
    // The folder + makes in and the bar's Paste goes into: the selected folder, or the selected file's.
    val selectedFolder = creationParentLocation(root, selectedPath, state.files)
    val pasteHere = { selectedFolder?.let { gateway.dispatch(FilesIntent.Paste(it)) } }
    // One row's sheet, or the selection's; gone with what it is about.
    val problemCounts = remember(state.diagnostics, state.languageDiagnostics, root) { DiagnosticRules.countsByLocation(state.problems, root) }
    val sheetEntries = if (selection.sheet) covered.orEmpty() else listOfNotNull(state.files.firstOrNull { it.path == actionsFor })
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(sheetEntries.isNotEmpty())) {
            WorkspaceHeader(
                selection,
                creation = FileItemActions.creation(
                    onNewFile = { createParent = selectedFolder; createType = WorkspaceItemType.File },
                    onNewFolder = { createParent = selectedFolder; createType = WorkspaceItemType.Folder },
                ),
            )
            Row(Modifier.weight(1f).background(IcodeColors.Surface)) {
                ActivityRail(
                    // Each tool as the dock shows it: its icon, name and badge (docs/dock-navigation-plan.md D7).
                    entries = listOf(
                        railEntry(WorkspaceTool.Explorer, active = true),
                        railEntry(WorkspaceTool.Terminal, accented = true) { actions.openPanel(ToolPanelTab.Terminal) },
                        railEntry(WorkspaceTool.Search) { actions.navigate(Route.WorkspaceSearch) },
                        railEntry(
                            WorkspaceTool.SourceControl,
                            label = if (state.gitRepository == null) "Initialize Git" else null,
                            busy = state.isBusy(BusyAction.InitializeGit),
                        ) {
                            if (state.gitRepository == null) {
                                gateway.dispatch(ICodeIntent.InitializeGit)
                            } else {
                                actions.navigate(Route.SourceControl)
                            }
                        },
                        railEntry(WorkspaceTool.Assistant) { actions.navigate(Route.Assistant) },
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
                            val problems = problemCounts[entry.path]
                            val ticked = selection.ticked?.let { entry.path in it }
                            ExplorerRow(
                                entry = entry,
                                selected = entry.path == selectedPath,
                                ticked = ticked,
                                problems = problems,
                                opening = state.isBusy(BusyAction.OpenFile, entry.path),
                                onClick = {
                                    when {
                                        ticked != null -> selection.toggle(entry.path)
                                        entry.kind == FileKind.Folder -> gateway.dispatch(ICodeIntent.ToggleFolder(entry.path))
                                        else -> gateway.dispatch(ICodeIntent.OpenFile(entry.path))
                                    }
                                    selectedPath = entry.path
                                },
                                onLongClick = {
                                    if (ticked != null) selection.toggle(entry.path) else actionsFor = entry.path
                                    selectedPath = entry.path
                                },
                            )
                        }
                    }
                }
            }
            when {
                covered != null -> {
                    val folders = covered.count { it.kind == FileKind.Folder }
                    SelectionBar(
                        covered.size,
                        FileSelection.counted(folders, covered.size - folders),
                        selectionActions(covered.map { it.path }, gateway::dispatch, selection::leave) { selection.sheet = true },
                    )
                }
                // The Workspaces page's bar too: into the selected folder, as the header's Paste, and it says so.
                clipboard != null && selectedFolder != null -> ClipboardBar(clipboard, destination = state.runtimePathOf(selectedFolder), onPaste = { pasteHere() })
            }
        }
        // One row's sheet, or the selection's, over the whole screen, as in the file explorer.
        if (sheetEntries.isNotEmpty()) {
            val close = { actionsFor = null; selection.sheet = false }
            IcodeSheetOverlay(onClose = close) {
                ExplorerItemSheet(
                    sheetEntries,
                    onSelect = selection::start.takeUnless { selection.sheet },
                    onActed = selection.acted,
                    onClose = close,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
    createType?.let { type ->
        CreateWorkspaceItemSheet(type, createParent, onDismiss = { createType = null })
    }
}

/**
 * The tree's header: the workspace, and its + offering [creation] ([FileItemActions.creation]); in
 * [selection], ✕ to leave it, and Select all and Invert over the rows shown.
 */
@Composable
private fun WorkspaceHeader(selection: ExplorerSelection, creation: List<IcodeMenuAction>) {
    val state = LocalICodeSnapshot.current
    IcodeTopBar(
        title = state.activeWorkspace,
        subtitle = state.activePath,
        onBack = if (selection.selecting) selection::leave else null,
        leaving = BarLeave.Selection,
        actions = {
            if (selection.selecting) SelectionActions(selection, state.files.map { it.path })
            else NewItemButton(contentDescription = "Create in workspace", actions = creation)
        },
    )
}

/**
 * [tool] on the rail, named [label] where it does something else for now (Initialize Git), with its
 * badge: the Assistant in the accent while it works, wherever the user went meanwhile.
 */
@Composable
private fun railEntry(
    tool: WorkspaceTool,
    label: String? = null,
    active: Boolean = false,
    accented: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit = {},
): ActivityRailEntry {
    val badge = ToolBadges.of(tool, LocalICodeSnapshot.current)
    return ActivityRailEntry(tool.icon, label ?: tool.label, active, badge.count, accented, busy, badge.tint, badge.dot, onClick)
}

/** A selected file targets its containing visible directory; a folder targets itself. */
internal fun creationParentLocation(root: String?, selectedPath: String, files: List<FileEntry>): String? {
    val index = files.indexOfFirst { it.path == selectedPath }
    if (index < 0) return root
    val selected = files[index]
    if (selected.kind == FileKind.Folder) return selected.path
    return files.take(index).lastOrNull { it.kind == FileKind.Folder && it.depth == selected.depth - 1 }?.path ?: root
}
