package dev.icode.mobile.ui.screens.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFab
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.domain.files.FilesBrowserState
import dev.icode.mobile.domain.files.FilesSort
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.ClipboardBar
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.NewWorkspaceDialog
import dev.icode.mobile.ui.screens.workspace.CreateWorkspaceItemDialog
import dev.icode.mobile.ui.screens.workspace.WorkspaceItemType

/**
 * The file explorer (docs/storage-explorer-plan.md §3), as ZArchiver's: the folder shown and where it
 * is, ▾ to the other place — Private, iCode's storage where workspaces live, or Shared, phone
 * storage — 🔍 and ⇅ for the folder shown, `..` up, a long press for an item's sheet, + to make
 * something here, and the paste bar while something is held. At Private's top the folders are the
 * workspaces. [choosing]: the same explorer, in Private and folders only, choosing where a clone
 * goes — Place here, or at the top a new workspace.
 */
@Composable
fun FilesScreen(actions: NavigationActions, choosing: Boolean = false) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val browser = state.filesBrowser
    var sort by rememberSaveable { mutableStateOf(FilesSort.Date) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(browser?.path) { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf<WorkspaceItemType?>(null) }
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var placesOpen by remember { mutableStateOf(false) }
    var newOpen by remember { mutableStateOf(false) }
    val browse = { path: String -> gateway.dispatch(ICodeIntent.BrowseFiles(path)) }
    // Where it was when last shown; choosing, at the place chosen before, else in iCode's storage.
    LaunchedEffect(Unit) {
        val destination = state.projectDestination?.location
        gateway.dispatch(
            when {
                !choosing -> ICodeIntent.BrowseFiles()
                destination != null -> ICodeIntent.BrowseFiles(destination)
                else -> ICodeIntent.BrowsePlace(StoragePlace.Private)
            },
        )
    }
    // A workspace's size is measured on arrival, and again when the list of them changes: Size sorts by it.
    LaunchedEffect(state.workspaces.map { it.location }) { gateway.dispatch(ICodeIntent.MeasureWorkspaces) }
    // Back closes the search, then goes up a folder, as in a file manager; at the top it leaves.
    val up = browser?.takeIf { it.access }?.parent
    val back = {
        when {
            searching -> { searching = false; query = "" }
            up != null -> browse(up)
            else -> actions.back()
        }
    }
    BackHandler(enabled = searching || up != null) { back() }
    // At Private's top a folder is a workspace, and Paste makes one.
    val top = browser == null || (browser.place == StoragePlace.Private && browser.atRoot)
    val pasteHere = { browser?.let { gateway.dispatch(FilesIntent.Paste(it.path)) } }
    val creation = FileItemActions.creation(
        // Choosing a place, only folders are made.
        onNewFile = { creating = WorkspaceItemType.File }.takeUnless { choosing || top },
        onNewFolder = { creating = WorkspaceItemType.Folder },
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            IcodeTopBar(
                browser?.name.orEmpty(),
                subtitle = browser?.where,
                onBack = back,
                onTitleClick = { placesOpen = true }.takeUnless { choosing },
                titleMenu = { IcodeActionMenu(expanded = placesOpen, onDismiss = { placesOpen = false }, actions = places(browser) { gateway.dispatch(ICodeIntent.BrowsePlace(it)) }) },
                actions = {
                    IconButton(onClick = { searching = !searching; query = "" }) {
                        Icon(Icons.Outlined.Search, contentDescription = if (searching) "Close search" else "Search", tint = IcodeColors.TextPrimary)
                    }
                    SortButton(sort) { sort = it }
                },
            )
            if (searching && browser != null) SearchField(query, "Search in ${browser.name}") { query = it }
            Box(Modifier.weight(1f)) {
                when {
                    browser == null -> Unit
                    !browser.access -> PermissionExplanation(
                        permission = SystemPermission.Storage,
                        onAllow = { gateway.dispatch(ICodeIntent.RequestSystemPermission(SystemPermission.Storage)) },
                        onNotNow = actions.back,
                        modifier = Modifier.fillMaxSize().padding(IcodeSpacing.Gutter),
                    )
                    else -> {
                        FilesListing(browser, query, sort, choosing, browse) { actionsFor = it.location }
                        IcodeFab(
                            contentDescription = "New",
                            // One choice is made at once; two are offered.
                            onClick = { if (creation.size == 1) creation.single().onClick() else newOpen = true },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(IcodeSpacing.Space4),
                            menu = { IcodeActionMenu(expanded = newOpen, onDismiss = { newOpen = false }, actions = creation) },
                        )
                    }
                }
            }
            if (choosing) {
                ChooseBar(browser?.takeIf { it.place == StoragePlace.Private }) { path ->
                    gateway.dispatch(ICodeIntent.ChooseDestination(path))
                    actions.back()
                }
            } else if (browser != null && browser.access) {
                state.explorer.clipboard?.let { clipboard ->
                    ClipboardBar(clipboard, destination = if (top) null else browser.runtimePathOf(browser.path), onPaste = { pasteHere() })
                }
            }
        }
        // An item's sheet, over the whole screen; gone with the item.
        browser?.items?.firstOrNull { it.location == actionsFor }?.let { item ->
            IcodeSheetOverlay(onClose = { actionsFor = null }) {
                FilesItemSheet(item, browser, browse, onClose = { actionsFor = null }, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    creating?.let { type ->
        val here = browser
        if (here == null || top) {
            NewWorkspaceDialog { creating = null }
        } else {
            CreateWorkspaceItemDialog(
                type,
                here.path,
                onDismiss = { creating = null },
                workspace = here.workspace ?: here.rootPath,
                shownAs = here.runtimePathOf(here.path),
            )
        }
    }
}

/** ▾'s menu: the two places, each with its path, the one shown checked. */
private fun places(browser: FilesBrowserState?, onChoose: (StoragePlace) -> Unit): List<IcodeMenuAction> =
    StoragePlace.entries.map { place ->
        IcodeMenuAction(
            label = place.label,
            icon = if (place == StoragePlace.Private) Icons.Outlined.FolderCopy else Icons.Outlined.PhoneAndroid,
            onClick = { onChoose(place) },
            detail = if (place == StoragePlace.Private) WorkspaceRuntimePath.ROOT else browser?.sharedRoot?.takeIf { it.isNotEmpty() },
            checked = browser?.place == place,
        )
    }

/** ⇅: Name, Date or Size, the one in use checked; folders come first whichever it is. */
@Composable
private fun SortButton(sort: FilesSort, onSort: (FilesSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort by ${sort.name}", tint = IcodeColors.TextPrimary)
        }
        IcodeActionMenu(
            expanded = open,
            onDismiss = { open = false },
            actions = FilesSort.entries.map { option ->
                val icon = when (option) {
                    FilesSort.Name -> Icons.Outlined.SortByAlpha
                    FilesSort.Date -> Icons.Outlined.Schedule
                    FilesSort.Size -> Icons.Outlined.Straighten
                    FilesSort.Type -> Icons.Outlined.Category
                }
                IcodeMenuAction(option.name, icon, { onSort(option) }, checked = option == sort)
            },
        )
    }
}

/** 🔍's field, under the bar, ready to type in. */
@Composable
private fun SearchField(query: String, placeholder: String, onQuery: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    IcodeTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Gutter, vertical = IcodeSpacing.Space2),
        placeholder = placeholder,
        leadingIcon = Icons.Outlined.Search,
        inputModifier = Modifier.focusRequester(focus),
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
