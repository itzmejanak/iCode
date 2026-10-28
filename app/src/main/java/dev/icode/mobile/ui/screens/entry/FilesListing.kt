package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.files.FilesBrowserState
import dev.icode.mobile.domain.files.FilesLook
import dev.icode.mobile.domain.files.FilesRules
import dev.icode.mobile.domain.files.FilesSort
import dev.icode.mobile.domain.files.FilesView
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.domain.files.TrashRules

/** What the listing does with a tap, a long press and a tap on an item's icon (docs/explorer-gaps-plan.md §3.2). */
internal class ListingTaps(
    /** Ticked, in selection; null while not selecting. */
    val selection: Set<String>?,
    /** Null where nothing is selected: a folder picker. */
    val onTick: ((BrowsedItem) -> Unit)?,
    val onActions: ((BrowsedItem) -> Unit)?,
    /** A tap, not selecting: the item opened ([Opening.tap]); in Trash, its sheet. */
    val onOpen: (BrowsedItem) -> Unit,
)

/**
 * What is listed, found and ordered (docs/storage-explorer-plan.md §3.2; docs/explorer-gaps-plan.md
 * §3.10, §3.12): [up] first — `..`, while there is a folder above and nothing is selected — then
 * [shown] in [look]'s view — rows with their [detail], single lines, or tiles — under a label for
 * each type when sorted by Type; or, nothing shown, why ([empty]). [focus] is brought into view once
 * it is listed: the file Show in folder ticked.
 */
@Composable
internal fun FilesListing(
    shown: List<BrowsedItem>,
    look: FilesLook,
    taps: ListingTaps,
    detail: (BrowsedItem) -> String,
    empty: @Composable () -> Unit,
    up: (() -> Unit)? = null,
    focus: String? = null,
) {
    val upShown = up?.takeIf { taps.selection == null }
    val groups = if (look.sort == FilesSort.Type) shown.groupBy(FilesRules::typeGroup).toList() else listOf(null to shown)
    // Where [focus] is in the list, `..` and the labels counted.
    val at = if (focus == null) -1 else buildList {
        if (upShown != null) add("..")
        groups.forEach { (label, items) -> label?.let { add("group:$it") }; items.forEach { add(it.location) } }
    }.indexOf(focus)
    // A little air under the top bar, so the first row's selection does not touch its line.
    val above = IcodeSpacing.Space1
    if (look.view == FilesView.Grid) {
        val state = rememberLazyGridState()
        LaunchedEffect(focus, at >= 0) { if (at >= 0) state.scrollToItem(at) }
        LazyVerticalGrid(
            GridCells.Adaptive(IcodeSizes.GridTileMin),
            state = state,
            contentPadding = PaddingValues(start = IcodeSpacing.RowGutter, top = above, end = IcodeSpacing.RowGutter),
        ) {
            upShown?.let { item(key = "..") { UpTile(it) } }
            if (shown.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { empty() }
            groups.forEach { (label, items) ->
                label?.let { groupLabel(it, items.size) }
                items(items, key = { it.location }) { item -> ItemTile(item, taps) }
            }
        }
    } else {
        val compact = look.view == FilesView.Compact
        val gutter = IcodeSpacing.Gutter
        val state = rememberLazyListState()
        LaunchedEffect(focus, at >= 0) { if (at >= 0) state.scrollToItem(at) }
        // Rows run edge to edge: their own gutter keeps the text in line, their selection reaches the sides.
        LazyColumn(state = state, contentPadding = PaddingValues(top = above)) {
            upShown?.let { item(key = "..") { UpRow(compact, it) } }
            if (shown.isEmpty()) item(key = "empty") { Box(Modifier.padding(horizontal = IcodeSpacing.RowGutter)) { empty() } }
            groups.forEach { (label, items) ->
                label?.let { groupLabel(it, items.size, start = gutter) }
                items(items, key = { it.location }) { item -> ItemRow(item, taps, compact, detail(item)) }
            }
        }
    }
}

/** A type's label over its items, sorted by Type (§3.12): ".kt · 12". */
private fun LazyListScope.groupLabel(label: String, count: Int, start: Dp) = item(key = "group:$label") { TypeLabel(label, count, start) }

private fun LazyGridScope.groupLabel(label: String, count: Int) = item(key = "group:$label", span = { GridItemSpan(maxLineSpan) }) { TypeLabel(label, count, IcodeSpacing.RowInset) }

/** [start] is in from the list's edge: a label stands where the text of the rows under it does. */
@Composable
private fun TypeLabel(label: String, count: Int, start: Dp) {
    SectionLabel("$label · $count", Modifier.padding(start = start, top = IcodeSpacing.Space3, bottom = IcodeSpacing.Space1))
}

/**
 * Why a folder lists nothing: nothing here — at Private's top no workspace; choosing a folder, no
 * folder — or nothing the search for [query] found.
 */
@Composable
internal fun FolderEmpty(browser: FilesBrowserState, query: String, foldersOnly: Boolean) {
    val top = browser.place == StoragePlace.Private && browser.atRoot
    val here = if (foldersOnly) browser.items.filter { it.folder } else browser.items
    if (here.isNotEmpty()) {
        NoMatch(query)
        return
    }
    EmptyState(
        title = when {
            top -> "No workspaces yet"
            browser.items.isEmpty() -> "${browser.name} is empty"
            else -> "No folders in ${browser.name}"
        },
        description = when {
            top && foldersOnly -> "What goes here becomes a workspace of its own."
            top -> "+ makes one, or copy a folder in from Shared."
            foldersOnly -> "The button below puts it in this folder; + makes a folder in it."
            else -> "+ makes a folder or a file here; Paste puts what you cut or copied here."
        },
        icon = if (top) Icons.Outlined.Folder else Icons.Outlined.FolderOpen,
        modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space6),
    )
}

/** Why a view (§3.10) lists nothing: nothing in it yet — [held] none — or nothing the search for [query] found. */
@Composable
internal fun ViewEmpty(view: ExplorerView, held: Boolean, query: String) {
    if (held) {
        NoMatch(query)
        return
    }
    EmptyState(
        title = if (view == ExplorerView.Trash) "Trash is empty" else "Nothing in ${view.title} yet",
        description = when (view) {
            ExplorerView.Recent -> "Files you open in iCode — in the editor, a viewer or another app — come here."
            ExplorerView.Trash -> "What you delete waits here ${TrashRules.KEEP_DAYS} days, to be restored."
            else -> null
        },
        icon = view.icon,
        modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space6),
    )
}

@Composable
private fun NoMatch(query: String) {
    EmptyState(
        title = "Nothing here matches “${query.trim()}”",
        icon = Icons.Outlined.Search,
        modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space6),
    )
}
