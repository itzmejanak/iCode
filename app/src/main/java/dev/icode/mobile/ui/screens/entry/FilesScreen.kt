package dev.icode.mobile.ui.screens.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.BarLeave
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeBreadcrumb
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFab
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.files.FileSelection
import dev.icode.mobile.domain.files.FilesRules
import dev.icode.mobile.domain.files.FolderPick
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.ClipboardBar
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository
import dev.icode.mobile.ui.NewWorkspaceDialog
import dev.icode.mobile.ui.ScreenActions
import dev.icode.mobile.ui.SelectionActions
import dev.icode.mobile.ui.SelectionBar
import dev.icode.mobile.ui.among
import dev.icode.mobile.ui.rememberSelection
import dev.icode.mobile.ui.screens.workspace.CreateWorkspaceItemDialog
import dev.icode.mobile.ui.screens.workspace.WorkspaceItemType
import dev.icode.mobile.ui.selectionActions
import kotlinx.coroutines.launch

/**
 * The file explorer (docs/storage-explorer-plan.md §3; docs/explorer-gaps-plan.md), as ZArchiver's:
 * where it is — a folder ([FolderExplorer]) or one of its views (§3.10) ([ViewExplorer]), ▾ going
 * between them — as the snapshot says. The folder picker (§3.5), started from either, picks among
 * folders; Show in folder, from a view, opens the file's folder with it ticked. [choosing] is that
 * picker from the clone's page, in Private, choosing where the clone goes: always among folders.
 */
@Composable
fun FilesScreen(actions: NavigationActions, choosing: Boolean = false) {
    val gateway = LocalICodeGateway.current
    val view = LocalICodeSnapshot.current.explorer.view.takeUnless { choosing }
    var pick by remember { mutableStateOf<FolderPick?>(FolderPick.Clone.takeIf { choosing }) }
    var reveal by rememberSaveable { mutableStateOf<String?>(null) }
    // Opened onto a view — from Manage storage, Settings — its Back is the way out; once in a folder,
    // a view's Back is to the folder.
    var openedOnView by rememberSaveable { mutableStateOf(view != null) }
    LaunchedEffect(view == null) { if (view == null) openedOnView = false }
    when (view) {
        null -> FolderExplorer(actions, choosing, pick, { pick = it }, reveal, onRevealed = { reveal = null })
        else -> ViewExplorer(
            view,
            actions,
            onPick = { pick = it },
            onReveal = { reveal = it },
            onLeave = if (openedOnView) actions.back else ({ gateway.dispatch(ICodeIntent.BrowseFiles()) }),
        )
    }
}

/**
 * The folder shown and where it is, ▾ to the other place, a favorite or a view, 🔍 and ☰ for the
 * folder shown, ⋮, `..` up, a long press for an item's sheet, + to make something here, and the paste
 * bar while something is held. At Private's top the folders are the workspaces. A tap on an item's
 * icon, or Select, starts a selection (§3.2): the bars change to its own, and it ends with Back, ✕,
 * an action, or going elsewhere. [pick] makes it a folder picker (§3.5): folders only, and the
 * picker's bar. [reveal] is the file Show in folder asked for, ticked once its folder is listed.
 */
@Composable
private fun FolderExplorer(
    actions: NavigationActions,
    choosing: Boolean,
    pick: FolderPick?,
    onPick: (FolderPick?) -> Unit,
    reveal: String?,
    onRevealed: () -> Unit,
) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val browser = state.filesBrowser
    // Folders' one look, and hidden files listed or not, as kept (§3.12, §3.13).
    val settings = state.explorer.settings
    val look = settings.look(null)
    val preferences = LocalWorkspacePreferencesRepository.current
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(browser?.path) { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf<WorkspaceItemType?>(null) }
    var placesOpen by remember { mutableStateOf(false) }
    var newOpen by remember { mutableStateOf(false) }
    // One item's sheet; or, selecting, the selection's. A selection belongs to the folder shown (§3.2).
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    // Show in folder's file, once its folder is listed: ticked, and brought into view.
    val focus by rememberSaveable(browser?.path) { mutableStateOf(reveal?.takeIf { it.substringBeforeLast('/') == browser?.path }) }
    val selection = rememberSelection(browser?.path, focus?.let { listOf(it) })
    LaunchedEffect(focus) { if (focus != null) onRevealed() }
    val browse = { path: String -> gateway.dispatch(ICodeIntent.BrowseFiles(path)) }
    val opening = opening(browse)
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
    val listed = browser?.items.orEmpty()
    val shown = FilesRules.sorted(FilesRules.matching(if (pick != null) listed.filter { it.folder } else listed, query), look.sort, look.descending, state.workspaceSizes)
    val selected = selection.among(listed) { it.location }
    val top = browser == null || (browser.place == StoragePlace.Private && browser.atRoot)
    // Back leaves the selection, closes the search, then goes up a folder, as in a file manager; at
    // the top a folder picker stops, and otherwise it leaves.
    val up = browser?.takeIf { it.access }?.parent
    val back = {
        when {
            selection.selecting -> selection.leave()
            searching -> { searching = false; query = "" }
            up != null -> browse(up)
            pick != null && !choosing -> onPick(null)
            else -> actions.back()
        }
    }
    BackHandler(enabled = selection.selecting || searching || up != null || (pick != null && !choosing)) { back() }
    val screen = ScreenActions(
        pick = { chosen -> selection.leave(); onPick(chosen) },
        select = selection::start,
    )
    val creation = FileItemActions.creation(
        // Choosing a folder, only folders are made.
        onNewFile = { creating = WorkspaceItemType.File }.takeUnless { pick != null || top },
        onNewFolder = { creating = WorkspaceItemType.Folder },
    )

    // One item's sheet, or the selection's, over the whole screen; gone with what it is about.
    val sheetItems = if (selection.sheet) selected.orEmpty() else listOfNotNull(listed.firstOrNull { it.location == actionsFor })
    val sheetShown = browser != null && sheetItems.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(sheetShown)) {
            IcodeTopBar(
                browser?.name.orEmpty(),
                onBack = back,
                onTitleClick = { placesOpen = true }.takeUnless { choosing || selection.selecting },
                titleMenu = {
                    IcodeActionMenu(
                        expanded = placesOpen,
                        onDismiss = { placesOpen = false },
                        actions = goTo(browser, state.explorer, view = null, onPlace = { gateway.dispatch(ICodeIntent.BrowsePlace(it)) }, onFolder = browse, onView = { gateway.dispatch(ICodeIntent.BrowseView(it)) }),
                    )
                },
                leaving = if (selection.selecting) BarLeave.Selection else BarLeave.Back,
                // The path in segments, each where a tap goes (§3.14).
                subtitleContent = browser?.crumbs?.let { crumbs -> { IcodeBreadcrumb(crumbs.map { it.label }, onCrumb = { browse(crumbs[it].location) }) } },
                actions = {
                    IconButton(onClick = { searching = !searching; query = "" }) {
                        Icon(Icons.Outlined.Search, contentDescription = if (searching) "Close search" else "Search", tint = IcodeColors.TextPrimary)
                    }
                    if (selection.selecting) {
                        SelectionActions(selection, shown.map { it.location })
                    } else {
                        ViewPanelButton(
                            look,
                            settings.showHidden,
                            onLook = { scope.launch { preferences.setFilesLook(null, it) } },
                            onHidden = { scope.launch { preferences.setShowHidden(it) } },
                        )
                        if (pick == null) {
                            ExplorerMenu(
                                state.explorer.history,
                                onSelect = { selection.start() },
                                onUndo = { gateway.dispatch(FilesIntent.Undo) },
                                onRedo = { gateway.dispatch(FilesIntent.Redo) },
                                onManage = { actions.navigate(Route.ManageStorage) },
                                onStorage = { actions.navigate(Route.StorageSettings) },
                            )
                        }
                    }
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
                        val taps = ListingTaps(
                            selection = selection.ticked?.toSet(),
                            onTick = if (pick == null) ({ item -> selection.toggle(item.location) }) else null,
                            onActions = if (pick == null) ({ item -> actionsFor = item.location }) else null,
                            onOpen = opening::tap,
                        )
                        FilesListing(
                            FilesRules.grouped(shown, look.sort),
                            look,
                            taps,
                            detail = FilesRules::detail,
                            empty = { FolderEmpty(browser, query, foldersOnly = pick != null) },
                            up = up?.let { parent -> { browse(parent) } },
                            focus = focus,
                        )
                        if (!selection.selecting) {
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
            }
            val picking = pick
            when {
                picking != null -> PickBar(picking, browser?.takeUnless { picking == FolderPick.Clone && it.place != StoragePlace.Private }, top) { path ->
                    when (picking) {
                        FolderPick.Clone -> {
                            gateway.dispatch(ICodeIntent.ChooseDestination(path.takeUnless { top }))
                            actions.back()
                        }
                        is FolderPick.Transfer -> gateway.dispatch(FilesIntent.Transfer(picking.items.map { it.location }, path, picking.move))
                        is FolderPick.Extract -> picking.archives.forEach { gateway.dispatch(FilesIntent.Extract(it, path)) }
                    }
                    if (!choosing) onPick(null)
                }
                selected != null -> SelectionBar(selected.size, FileSelection.summary(selected, state.workspaceSizes), selectionActions(selected.map { it.location }, gateway::dispatch, selection::leave) { selection.sheet = true })
                browser != null && browser.access -> state.explorer.clipboard?.let { clipboard ->
                    ClipboardBar(clipboard, destination = if (top) null else browser.runtimePathOf(browser.path), onPaste = { gateway.dispatch(FilesIntent.Paste(browser.path)) })
                }
            }
        }
        if (browser != null && sheetShown) {
            val close = { actionsFor = null; selection.sheet = false }
            IcodeSheetOverlay(onClose = close) {
                FilesSheet(
                    sheetItems,
                    browser,
                    opening,
                    if (selection.sheet) ScreenActions(screen.pick, select = null) else screen,
                    onActed = selection.acted,
                    onClose = close,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
