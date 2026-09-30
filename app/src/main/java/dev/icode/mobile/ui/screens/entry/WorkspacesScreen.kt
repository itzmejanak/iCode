package dev.icode.mobile.ui.screens.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.domain.FilesBrowserState
import dev.icode.mobile.domain.FilesRules
import dev.icode.mobile.domain.FilesSort
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkspaceListRules
import dev.icode.mobile.domain.storageSizeLabel
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.ClipboardBar
import dev.icode.mobile.ui.FolderBreadcrumb
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.NewWorkspaceDialog
import dev.icode.mobile.ui.WorkspaceRow
import dev.icode.mobile.ui.screens.workspace.CreateWorkspaceItemDialog
import dev.icode.mobile.ui.screens.workspace.DeleteWorkspaceItemDialog
import dev.icode.mobile.ui.screens.workspace.RenameWorkspaceItemDialog
import dev.icode.mobile.ui.screens.workspace.WorkspaceItemType

/**
 * The file explorer of the workspaces (docs/workspace-destinations-plan.md §2.2), as MT Manager's:
 * the breadcrumb from `workspace`, Back up a folder, folders then files with their size or count
 * and date, Name · Date · Size and search in the folder shown, New folder and New file where it is,
 * each entry's one menu from ⋮ or a long press, and the clipboard bar to paste here. At the top its
 * folders are the workspaces, each with its own menu (Open, Open Terminal…). [choosing]: the same
 * explorer, folders only, choosing where a clone or an import goes — Place here, or at the top a new
 * workspace.
 */
@Composable
fun WorkspacesScreen(actions: NavigationActions, choosing: Boolean = false) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val browser = state.filesBrowser
    var sort by rememberSaveable { mutableStateOf(FilesSort.Date) }
    var query by rememberSaveable(browser?.path) { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf<WorkspaceItemType?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val browse = { path: String -> gateway.dispatch(WorkbenchIntent.BrowseFiles(path)) }
    // Where it was when it was last shown — choosing, at the place chosen before; the workspaces folder the first time.
    LaunchedEffect(Unit) { gateway.dispatch(WorkbenchIntent.BrowseFiles(state.projectDestination?.location?.takeIf { choosing })) }
    // A workspace's size is measured on arrival, and again when the list of them changes.
    LaunchedEffect(state.workspaces.map { it.location }) { gateway.dispatch(WorkbenchIntent.MeasureWorkspaces) }
    // Back goes up a folder, as in a file manager; at the top it leaves.
    val up = browser?.parent
    val back = { if (up != null) browse(up) else actions.back() }
    BackHandler(enabled = up != null) { back() }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            if (choosing) "Choose Where" else "Workspaces",
            subtitle = if (browser == null || browser.atRoot) {
                WorkspaceListRules.summary(state.workspaces, state.workspaceSizes)
            } else {
                FilesRules.contents(browser.items)
            },
            onBack = back,
            actions = { NewItemMenu(foldersOnly = choosing || browser?.atRoot != false) { creating = it } },
        )
        browser?.let { FolderBreadcrumb(it.crumbs, browse) }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Space4, vertical = IcodeSpacing.Space2),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            IcodeTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = if (browser == null || browser.atRoot) "Search workspaces" else "Search in ${browser.name}",
                leadingIcon = Icons.Outlined.Search,
            )
            SegmentedControl(FilesSort.entries.map { it.name }, sort.name) { sort = FilesSort.valueOf(it) }
        }
        Box(Modifier.weight(1f)) {
            if (browser != null) Listing(browser, query, sort, choosing, browse, { renaming = it }, { deleting = it })
        }
        if (choosing) {
            ChooseBar(browser) { path ->
                gateway.dispatch(WorkbenchIntent.ChooseDestination(path))
                actions.back()
            }
        } else state.clipboard?.let { held ->
            ClipboardBar(held, onPaste = {
                val here = browser
                gateway.dispatch(if (here == null || here.atRoot) WorkbenchIntent.PasteAsWorkspace else WorkbenchIntent.PasteInto(here.path))
            })
        }
    }
    creating?.let { type ->
        val here = browser
        if (here == null || here.atRoot) NewWorkspaceDialog { creating = null }
        else CreateWorkspaceItemDialog(type, here.path, onDismiss = { creating = null }, workspace = here.workspace)
    }
    browser?.items?.firstOrNull { it.location == renaming }?.let { item ->
        RenameWorkspaceItemDialog(item.location, item.name, item.folder, onDismiss = { renaming = null })
    }
    browser?.items?.firstOrNull { it.location == deleting }?.let { item ->
        DeleteWorkspaceItemDialog(item.location, item.name, item.folder, browser.runtimePathOf(item.location), onDismiss = { deleting = null })
    }
}

/** What is here, found and ordered: the workspaces at the top, else the folder's folders and files. */
@Composable
private fun Listing(
    browser: FilesBrowserState,
    query: String,
    sort: FilesSort,
    foldersOnly: Boolean,
    browse: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val state = LocalWorkbenchSnapshot.current
    val here = if (foldersOnly) browser.items.filter { it.folder } else browser.items
    val shown = FilesRules.sorted(FilesRules.matching(here, query), sort, state.workspaceSizes)
    when {
        browser.items.isEmpty() -> EmptyState(
            title = if (browser.atRoot) "No workspaces yet" else "${browser.name} is empty",
            description = if (browser.atRoot) {
                "New folder above makes one; Import and Clone are on the home screen."
            } else {
                "New folder or New file above; Paste puts what you cut or copied here."
            },
            icon = if (browser.atRoot) Icons.Outlined.Folder else Icons.Outlined.FolderOpen,
            modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space6),
        )
        shown.isEmpty() -> EmptyState(
            title = "Nothing here matches “${query.trim()}”",
            icon = Icons.Outlined.Search,
            modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space6),
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(start = IcodeSpacing.Space4, end = IcodeSpacing.Space4, bottom = IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            items(shown, key = { it.location }) { item ->
                val workspace = if (browser.atRoot) state.workspaces.firstOrNull { it.location == item.location } else null
                if (workspace != null) {
                    WorkspaceRow(workspace, size = storageSizeLabel(state.workspaceSizes[workspace.location]), onOpen = { browse(workspace.location) })
                } else {
                    BrowsedItemRow(item, browser.runtimePathOf(item.location), browse, { onRename(item.location) }, { onDelete(item.location) })
                }
            }
        }
    }
}

/** + in the top bar: New folder — a new workspace at the top — and, inside one, New file. */
@Composable
private fun NewItemMenu(foldersOnly: Boolean, onCreate: (WorkspaceItemType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.Add, contentDescription = "New", tint = IcodeColors.TextPrimary)
        }
        IcodeActionMenu(
            expanded = open,
            onDismiss = { open = false },
            actions = listOfNotNull(
                IcodeMenuAction("New folder", Icons.Outlined.CreateNewFolder, { onCreate(WorkspaceItemType.Folder) }),
                IcodeMenuAction("New file", Icons.Outlined.Description, { onCreate(WorkspaceItemType.File) }).takeUnless { foldersOnly },
            ),
        )
    }
}

/** Choosing where a project goes: the place it will be, and the button that says so. */
@Composable
private fun ChooseBar(browser: FilesBrowserState?, onChoose: (String?) -> Unit) {
    // A folder inside a workspace; at the top, none: a new workspace.
    val place = browser?.takeUnless { it.atRoot }
    Column(Modifier.fillMaxWidth().background(IcodeColors.SurfaceElevated)) {
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Space4, vertical = IcodeSpacing.Space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            Column(Modifier.weight(1f)) {
                MonoLine(place?.runtimePathOf(place.path) ?: "/workspace/<name>", maxLines = 1)
                Text(if (place == null) "It becomes a workspace of its own" else "It goes in this folder", style = IcodeType.Caption)
            }
            PrimaryButton(if (place == null) "New workspace" else "Place here", { onChoose(place?.path) })
        }
    }
}

