package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.FilesBrowserState
import dev.icode.mobile.domain.files.FilesLook
import dev.icode.mobile.domain.files.FilesRules
import dev.icode.mobile.domain.files.FilesSort
import dev.icode.mobile.domain.files.FilesView
import dev.icode.mobile.domain.files.StoragePlace

/** What the listing does with a tap, a long press and a tap on an item's icon (docs/explorer-gaps-plan.md §3.2). */
internal class ListingTaps(
    /** Ticked, in selection; null while not selecting. */
    val selection: Set<String>?,
    /** Null where nothing is selected: a folder picker. */
    val onTick: ((BrowsedItem) -> Unit)?,
    val onActions: ((BrowsedItem) -> Unit)?,
)

/**
 * What is here, found and ordered (docs/storage-explorer-plan.md §3.2; docs/explorer-gaps-plan.md
 * §3.12): `..` first while there is a folder above, then [shown] in [look]'s view — rows with detail,
 * single lines, or tiles — under a label for each type when sorted by Type. At Private's top they are
 * the workspaces. In selection `..` is not there ([taps]). Room is left at the end for the +.
 */
@Composable
internal fun FilesListing(
    browser: FilesBrowserState,
    shown: List<BrowsedItem>,
    query: String,
    foldersOnly: Boolean,
    look: FilesLook,
    browse: (String) -> Unit,
    taps: ListingTaps,
) {
    val up = browser.parent?.takeIf { taps.selection == null }
    val groups = if (look.sort == FilesSort.Type) shown.groupBy(FilesRules::typeGroup).toList() else listOf(null to shown)
    val empty = emptiness(browser, shown, query, foldersOnly)
    val padding = PaddingValues(start = IcodeSpacing.RowGutter, end = IcodeSpacing.RowGutter, bottom = IcodeSizes.FabClearance)
    if (look.view == FilesView.Grid) {
        LazyVerticalGrid(GridCells.Adaptive(IcodeSizes.GridTileMin), contentPadding = padding) {
            up?.let { parent -> item(key = "..") { UpTile { browse(parent) } } }
            if (empty != null) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { empty() }
            groups.forEach { (label, items) ->
                label?.let { groupLabel(it, items.size) }
                items(items, key = { it.location }) { item -> ItemTile(item, browser, browse, taps) }
            }
        }
    } else {
        val compact = look.view == FilesView.Compact
        LazyColumn(contentPadding = padding) {
            up?.let { parent -> item(key = "..") { UpRow(compact) { browse(parent) } } }
            if (empty != null) item(key = "empty") { empty() }
            groups.forEach { (label, items) ->
                label?.let { groupLabel(it, items.size) }
                items(items, key = { it.location }) { item -> ItemRow(item, browser, browse, taps, compact) }
            }
        }
    }
}

/** A type's label over its items, sorted by Type (§3.12): ".kt · 12". */
private fun LazyListScope.groupLabel(label: String, count: Int) = item(key = "group:$label") { TypeLabel(label, count) }

private fun LazyGridScope.groupLabel(label: String, count: Int) = item(key = "group:$label", span = { GridItemSpan(maxLineSpan) }) { TypeLabel(label, count) }

@Composable
private fun TypeLabel(label: String, count: Int) {
    SectionLabel("$label · $count", Modifier.padding(start = IcodeSpacing.RowInset, top = IcodeSpacing.Space3, bottom = IcodeSpacing.Space1))
}

/**
 * Why nothing is listed, or null: nothing here — at Private's top no workspace; choosing a folder, no
 * folder — or nothing the search found.
 */
private fun emptiness(browser: FilesBrowserState, shown: List<BrowsedItem>, query: String, foldersOnly: Boolean): (@Composable () -> Unit)? {
    if (shown.isNotEmpty()) return null
    val top = browser.place == StoragePlace.Private && browser.atRoot
    val here = if (foldersOnly) browser.items.filter { it.folder } else browser.items
    return {
        if (here.isEmpty()) {
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
        } else {
            EmptyState(
                title = "Nothing here matches “${query.trim()}”",
                icon = Icons.Outlined.Search,
                modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space6),
            )
        }
    }
}
