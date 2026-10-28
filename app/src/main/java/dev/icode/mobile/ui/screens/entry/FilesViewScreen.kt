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
import dev.icode.mobile.domain.files.TrashedItem
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository
import dev.icode.mobile.ui.ScreenActions
import kotlinx.coroutines.launch

/**
 * One of the explorer's views (docs/explorer-gaps-plan.md §3.10) — Recent or Trash — as the explorer
 * itself: its rows and tiles, the ☰ panel kept for this view alone, 🔍, ⋮, selection and the item's
 * sheet, under the view's name and how much it found. Each row says the folder it is in; there is no
 * `..`, +, breadcrumb or paste bar: nothing is made or pasted into a view. Recent opens a file as its
 * folder would, and its sheet adds Show in folder ([onReveal]); Move to… and Copy to… pick a folder
 * ([onPick]). Trash (§3.7) is read as it opens; a tap on its row opens the item's own sheet
 * ([TrashSheet]), its selection restores or deletes for good, and its top bar empties it. Back leaves
 * the selection, then the search, then the view ([onView]).
 */
@Composable
internal fun ViewExplorer(
    view: ExplorerView,
    actions: NavigationActions,
    onView: (ExplorerView?) -> Unit,
    onPick: (FolderPick) -> Unit,
    onReveal: (String) -> Unit,
) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val preferences = LocalWorkspacePreferencesRepository.current
    val scope = rememberCoroutineScope()
    val trash = view == ExplorerView.Trash
    val look = state.explorer.settings.look(view)
    var searching by rememberSaveable(view.encoded) { mutableStateOf(false) }
    var query by rememberSaveable(view.encoded) { mutableStateOf("") }
    var goToOpen by remember { mutableStateOf(false) }
    var actionsFor by rememberSaveable(view.encoded) { mutableStateOf<String?>(null) }
    var ticked by rememberSaveable(view.encoded) { mutableStateOf<List<String>?>(null) }
    var selectionSheet by rememberSaveable(view.encoded) { mutableStateOf(false) }
    // Trash's items to delete for good, asked first; all of them, emptying it.
    var forGood by remember { mutableStateOf<List<TrashedItem>?>(null) }
    var emptying by remember { mutableStateOf(false) }
    LaunchedEffect(view) { if (trash) gateway.dispatch(FilesIntent.ReadTrash) }
    val rows = ExplorerViews.rows(view, state.explorer)
    val shown = FilesRules.sorted(FilesRules.matching(rows, query), look.sort, look.descending, emptyMap(), foldersFirst = false)
    // What is ticked and still here; once all of it has gone, the selection ends.
    val selected = ticked?.let { locations -> rows.filter { it.location in locations } }
    LaunchedEffect(selected?.isEmpty()) { if (selected?.isEmpty() == true && ticked?.isNotEmpty() == true) ticked = null }
    val leave = { ticked = null; selectionSheet = false }
    val back = {
        when {
            ticked != null -> leave()
            searching -> { searching = false; query = "" }
            else -> onView(null)
        }
    }
    BackHandler { back() }
    // A view's files open as in their folders; going to one leaves the view.
    val opening = opening { folder -> gateway.dispatch(ICodeIntent.BrowseFiles(folder)); onView(null) }
    // Trash's rows are where its items are held; what is done to them goes by their ids.
    val trashed = { locations: Collection<String> -> state.explorer.trash.filter { it.heldAt in locations } }
    val sheetItems = if (selectionSheet) selected.orEmpty() else listOfNotNull(rows.firstOrNull { it.location == actionsFor })
    val sheetShown = sheetItems.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(sheetShown)) {
            IcodeTopBar(
                view.title,
                subtitle = ExplorerViews.found(view, rows),
                onBack = back,
                onTitleClick = { goToOpen = true }.takeUnless { ticked != null },
                titleMenu = {
                    IcodeActionMenu(
                        expanded = goToOpen,
                        onDismiss = { goToOpen = false },
                        actions = goTo(
                            state.filesBrowser,
                            state.explorer,
                            view,
                            onPlace = { gateway.dispatch(ICodeIntent.BrowsePlace(it)); onView(null) },
                            onFolder = { gateway.dispatch(ICodeIntent.BrowseFiles(it)); onView(null) },
                            onView = onView,
                        ),
                    )
                },
                leaving = if (ticked != null) BarLeave.Selection else BarLeave.Back,
                actions = {
                    IconButton(onClick = { searching = !searching; query = "" }) {
                        Icon(Icons.Outlined.Search, contentDescription = if (searching) "Close search" else "Search", tint = IcodeColors.TextPrimary)
                    }
                    if (ticked != null) {
                        SelectionActions(shown.map { it.location }, ticked.orEmpty().toSet()) { ticked = it.toList() }
                    } else {
                        ViewPanelButton(look, state.explorer.settings.showHidden, onLook = { scope.launch { preferences.setFilesLook(view, it) } }, onHidden = null)
                        if (trash) {
                            IconButton(onClick = { emptying = true }, enabled = rows.isNotEmpty()) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Empty Trash", tint = if (rows.isNotEmpty()) IcodeColors.Error else IcodeColors.TextMuted)
                            }
                        }
                        ExplorerMenu(
                            state.explorer.history,
                            onSelect = { ticked = emptyList() },
                            onUndo = { gateway.dispatch(FilesIntent.Undo) },
                            onRedo = { gateway.dispatch(FilesIntent.Redo) },
                            onStorage = { actions.navigate(Route.StorageSettings) },
                        )
                    }
                },
            )
            if (searching) SearchField(query, "Search in ${view.title}") { query = it }
            Box(Modifier.weight(1f)) {
                val taps = ListingTaps(
                    selection = ticked?.toSet(),
                    onTick = { item -> ticked = FileSelection.toggled(ticked.orEmpty().toSet(), item.location).toList() },
                    onActions = { item -> actionsFor = item.location },
                    onOpen = if (trash) ({ item -> actionsFor = item.location }) else opening::tap,
                )
                FilesListing(shown, look, taps, detail = FilesRules::gatheredDetail, empty = { ViewEmpty(view, held = rows.isNotEmpty(), query) })
            }
            selected?.let { items ->
                val locations = items.map { it.location }
                SelectionBar(
                    items,
                    emptyMap(),
                    if (trash) trashActions(trashed(locations).map { it.id }, gateway::dispatch, onForGood = { forGood = trashed(locations) }, onActed = leave)
                    else selectionActions(locations, gateway::dispatch, leave) { selectionSheet = true },
                )
            }
        }
        if (sheetShown) {
            val close = { actionsFor = null; selectionSheet = false }
            IcodeSheetOverlay(onClose = close) {
                val one = sheetItems.singleOrNull()
                val item = one?.let { trashed(listOf(it.location)).singleOrNull() }
                when {
                    trash -> item?.let { TrashSheet(it, onForGood = { forGood = listOf(it) }, onClose = close, modifier = Modifier.align(Alignment.BottomCenter)) }
                    else -> FilesSheet(
                        sheetItems,
                        here = null,
                        opening,
                        ScreenActions({ chosen -> leave(); onPick(chosen) }, select = if (selectionSheet) null else ({ locations -> ticked = locations })),
                        onActed = { if (selectionSheet) leave() },
                        onClose = close,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        showInFolder = one?.let { { onReveal(it.location) } },
                    )
                }
            }
        }
    }
    forGood?.let { items -> ForGoodDialog(items, all = false, onActed = leave) { forGood = null } }
    if (emptying) ForGoodDialog(state.explorer.trash, all = true, onActed = leave) { emptying = false }
}
