package dev.icode.mobile.ui.screens.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.files.ExplorerViews
import dev.icode.mobile.domain.files.FileSelection
import dev.icode.mobile.domain.files.FilesRules
import dev.icode.mobile.domain.files.FolderPick
import dev.icode.mobile.domain.files.StorageInsights
import dev.icode.mobile.domain.files.TrashedItem
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository
import dev.icode.mobile.ui.ScreenActions
import dev.icode.mobile.ui.SelectionActions
import dev.icode.mobile.ui.SelectionBar
import dev.icode.mobile.ui.among
import dev.icode.mobile.ui.rememberSelection
import dev.icode.mobile.ui.selectionActions
import kotlinx.coroutines.launch

/**
 * One of the explorer's views (docs/workspaces-and-files.md) as the explorer itself: its rows and
 * tiles, the ☰ panel kept for this view alone, 🔍, ⋮, selection and the item's sheet, under the
 * view's name and how much it found. Each row says the folder it is in; there is no `..`, +,
 * breadcrumb or paste bar: nothing is made or pasted into a view. A file opens as in its folder, and
 * its sheet adds Show in folder ([onReveal]); Move to… and Copy to… pick a folder ([onPick]). Manage
 * storage's views are listed from its scan while shown; Duplicates set by set, its ⋮ selecting the
 * extra copies. Trash is read as it opens; a tap on its row opens the item's own sheet
 * ([TrashSheet]), its selection restores or deletes for good, and its top bar empties it. Back leaves
 * the selection, then the search, then the view ([onLeave]); so does any browse.
 */
@Composable
internal fun ViewExplorer(
    view: ExplorerView,
    actions: NavigationActions,
    onPick: (FolderPick) -> Unit,
    onReveal: (String) -> Unit,
    onLeave: () -> Unit,
) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val preferences = LocalWorkspacePreferencesRepository.current
    val scope = rememberCoroutineScope()
    val trash = view == ExplorerView.Trash
    val look = state.explorer.settings.look(view)
    var searching by rememberSaveable(view) { mutableStateOf(false) }
    var query by rememberSaveable(view) { mutableStateOf("") }
    var goToOpen by remember { mutableStateOf(false) }
    var actionsFor by rememberSaveable(view) { mutableStateOf<String?>(null) }
    val selection = rememberSelection(view)
    // Trash's items to delete for good, asked first; all of them, emptying it.
    var forGood by remember { mutableStateOf<List<TrashedItem>?>(null) }
    var emptying by remember { mutableStateOf(false) }
    LaunchedEffect(view) { if (trash) gateway.dispatch(FilesIntent.ReadTrash) }
    // Manage storage's views are listed while they are shown.
    if (view.scope != null) {
        DisposableEffect(view) {
            gateway.dispatch(FilesIntent.ListView(view))
            onDispose { gateway.dispatch(FilesIntent.CloseView) }
        }
    }
    val rows = ExplorerViews.rows(view, state.explorer)
    val groups = state.explorer.scanned?.groups.orEmpty()
    // Thousands of rows: filtered, sorted and grouped once for what they are, not on every frame that recomposes.
    val shown = remember(view, rows, query, look.sort, look.descending) {
        val found = FilesRules.matching(rows, query)
        if (ExplorerViews.sortable(view)) FilesRules.sorted(found, look.sort, look.descending, emptyMap(), foldersFirst = false) else found
    }
    val listed = remember(view, shown, look.sort, groups) { ExplorerViews.grouped(view, shown, look.sort, groups) }
    val selected = selection.among(rows) { it.location }
    val leave = selection::leave
    val back = {
        when {
            selection.selecting -> leave()
            searching -> { searching = false; query = "" }
            else -> onLeave()
        }
    }
    BackHandler { back() }
    // A view's files open as in their folders.
    val opening = opening { folder -> gateway.dispatch(ICodeIntent.BrowseFiles(folder)) }
    // Trash's rows are where its items are held; what is done to them goes by their ids.
    val trashed = { locations: Collection<String> -> state.explorer.trash.filter { it.heldAt in locations } }
    val sheetItems = if (selection.sheet) selected.orEmpty() else listOfNotNull(rows.firstOrNull { it.location == actionsFor })
    val sheetShown = sheetItems.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(sheetShown)) {
            IcodeTopBar(
                view.title,
                subtitle = ExplorerViews.found(view, rows),
                onBack = back,
                onTitleClick = { goToOpen = true }.takeUnless { selection.selecting },
                titleMenu = {
                    IcodeActionMenu(
                        expanded = goToOpen,
                        onDismiss = { goToOpen = false },
                        actions = goTo(
                            state.filesBrowser,
                            state.explorer,
                            view,
                            onPlace = { gateway.dispatch(ICodeIntent.BrowsePlace(it)) },
                            onFolder = { gateway.dispatch(ICodeIntent.BrowseFiles(it)) },
                            onView = { gateway.dispatch(ICodeIntent.BrowseView(it)) },
                        ),
                    )
                },
                leaving = if (selection.selecting) BarLeave.Selection else BarLeave.Back,
                actions = {
                    IconButton(onClick = { searching = !searching; query = "" }) {
                        Icon(Icons.Outlined.Search, contentDescription = if (searching) "Close search" else "Search", tint = IcodeColors.TextPrimary)
                    }
                    if (selection.selecting) {
                        SelectionActions(selection, shown.map { it.location })
                    } else {
                        ViewPanelButton(
                            look,
                            state.explorer.settings.showHidden,
                            onLook = { scope.launch { preferences.setFilesLook(view, it) } },
                            onHidden = null,
                            sortable = ExplorerViews.sortable(view),
                        )
                        if (trash) {
                            IconButton(onClick = { emptying = true }, enabled = rows.isNotEmpty()) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Empty Trash", tint = if (rows.isNotEmpty()) IcodeColors.Error else IcodeColors.TextMuted)
                            }
                        }
                        ExplorerMenu(
                            onSelect = { selection.start() },
                            onManage = { actions.navigate(Route.ManageStorage) },
                            onStorage = { actions.navigate(Route.StorageSettings) },
                            onExtraCopies = { selection.start(StorageInsights.extraCopies(groups)) }.takeIf { view is ExplorerView.Duplicates },
                        )
                    }
                },
            )
            if (searching) SearchField(query, "Search in ${view.title}") { query = it }
            Box(Modifier.weight(1f)) {
                val taps = ListingTaps(
                    selection = selection.ticked?.toSet(),
                    onTick = { item -> selection.toggle(item.location) },
                    onActions = { item -> actionsFor = item.location },
                    onOpen = if (trash) ({ item -> actionsFor = item.location }) else opening::tap,
                )
                FilesListing(
                    listed,
                    look,
                    taps,
                    detail = FilesRules::gatheredDetail,
                    empty = { ViewEmpty(view, held = rows.isNotEmpty(), query) },
                )
            }
            selected?.let { items ->
                val locations = items.map { it.location }
                SelectionBar(
                    items.size,
                    FileSelection.summary(items, emptyMap()),
                    if (trash) trashActions(trashed(locations).map { it.id }, gateway::dispatch, onForGood = { forGood = trashed(locations) }, onActed = leave)
                    else selectionActions(locations, gateway::dispatch, leave) { selection.sheet = true },
                )
            }
        }
        if (sheetShown) {
            val close = { actionsFor = null; selection.sheet = false }
            IcodeSheetOverlay(onClose = close) {
                val one = sheetItems.singleOrNull()
                val item = one?.let { trashed(listOf(it.location)).singleOrNull() }
                when {
                    trash -> item?.let { TrashSheet(it, onForGood = { forGood = listOf(it) }, onClose = close, modifier = Modifier.align(Alignment.BottomCenter)) }
                    else -> FilesSheet(
                        sheetItems,
                        here = null,
                        opening,
                        ScreenActions(
                            pick = { chosen ->
                                leave()
                                onPick(chosen)
                                gateway.dispatch(ICodeIntent.BrowseFiles())
                            },
                            select = if (selection.sheet) null else selection::start,
                        ),
                        onActed = selection.acted,
                        onClose = close,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        showInFolder = one?.let { item ->
                            {
                                onReveal(item.location)
                                gateway.dispatch(ICodeIntent.BrowseFiles(item.location.substringBeforeLast('/')))
                            }
                        },
                    )
                }
            }
        }
    }
    forGood?.let { items -> ForGoodSheet(items, all = false, onActed = leave) { forGood = null } }
    if (emptying) ForGoodSheet(state.explorer.trash, all = true, onActed = leave) { emptying = false }
}
