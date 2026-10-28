package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeCheckbox
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.FileViewer
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.FilesBrowserState
import dev.icode.mobile.domain.files.FilesRules
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.ui.LocalFileViewer
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.SharedFiles
import dev.icode.mobile.ui.screens.workspace.fileTypeIcon
import dev.icode.mobile.ui.screens.workspace.fileTypeTint

/** What the listing does with a tap, a long press and a tap on an item's icon (docs/explorer-gaps-plan.md §3.2). */
internal class ListingTaps(
    /** Ticked, in selection; null where nothing is selected — a folder picker. */
    val selection: Set<String>?,
    val onTick: ((BrowsedItem) -> Unit)?,
    val onActions: ((BrowsedItem) -> Unit)?,
)

/**
 * What is here, found and ordered (docs/storage-explorer-plan.md §3.2): `..` first while there is a
 * folder above, then [shown] as flat rows — at Private's top, the workspaces. In selection a tap
 * ticks an item, and `..` is not there; otherwise a tap opens it, a tap on its icon starts selecting
 * with it, and a long press asks for its sheet ([taps]). Room is left under the last row for the +.
 */
@Composable
internal fun FilesListing(browser: FilesBrowserState, shown: List<BrowsedItem>, query: String, foldersOnly: Boolean, browse: (String) -> Unit, taps: ListingTaps) {
    val top = browser.place == StoragePlace.Private && browser.atRoot
    val here = if (foldersOnly) browser.items.filter { it.folder } else browser.items
    LazyColumn(contentPadding = PaddingValues(start = IcodeSpacing.RowGutter, end = IcodeSpacing.RowGutter, bottom = IcodeSizes.FabClearance)) {
        browser.parent?.takeIf { taps.selection == null }?.let { parent -> item(key = "..") { UpRow { browse(parent) } } }
        when {
            // Empty as far as this list goes: choosing, a folder of files has no place to offer.
            here.isEmpty() -> item(key = "empty") {
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
            shown.isEmpty() -> item(key = "none") {
                EmptyState(
                    title = "Nothing here matches “${query.trim()}”",
                    icon = Icons.Outlined.Search,
                    modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space6),
                )
            }
            else -> items(shown, key = { it.location }) { item -> ItemRow(item, browser, browse, taps) }
        }
    }
}

/** `..`: up a folder, as Back. */
@Composable
private fun UpRow(onClick: () -> Unit) {
    FileRow(
        leading = { Icon(Icons.Outlined.DriveFolderUpload, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconXl)) },
        name = "..",
        detail = null,
        changedAt = null,
        onClick = onClick,
    )
}

/**
 * One folder or file, in a workspace or on phone storage — at Private's top, a workspace. Selecting,
 * a tap or a long press ticks it. Otherwise a tap does what opening it does ([openOf]) — a file with
 * nowhere in iCode to open goes to another app — a tap on its icon starts selecting with it, and a
 * long press asks for its sheet.
 */
@Composable
private fun ItemRow(item: BrowsedItem, browser: FilesBrowserState, browse: (String) -> Unit, taps: ListingTaps) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    val (icon, tint) = iconOf(item)
    val ticked = taps.selection?.let { item.location in it }
    val tick = taps.onTick?.let { { it(item) } }
    FileRow(
        leading = {
            if (ticked != null) {
                IcodeCheckbox(ticked, onCheckedChange = null, modifier = Modifier.size(IcodeSizes.IconXl))
            } else {
                Icon(
                    icon,
                    contentDescription = tick?.let { "Select ${item.name}" },
                    tint = tint,
                    modifier = Modifier.size(IcodeSizes.IconXl).then(if (tick != null) Modifier.clickable(onClick = tick) else Modifier),
                )
            }
        },
        name = item.name,
        detail = FilesRules.detail(item),
        changedAt = item.changedAt,
        selected = ticked == true,
        onClick = if (ticked != null && tick != null) tick else openOf(item, browser, browse) ?: { SharedFiles.open(context, item.location, gateway::dispatch) },
        onLongClick = if (ticked != null) tick else taps.onActions?.let { { it(item) } },
    )
}

/**
 * What opening [item] does: a folder goes into the list; a file of a workspace into the editor, which
 * hands one it cannot show to another app (docs/open-with-plan.md); one on phone storage, having no
 * editor, into iCode's viewer when it has one for it (docs/file-viewers-plan.md), and into Recent.
 * Null for one with nowhere in iCode to open.
 */
@Composable
internal fun openOf(item: BrowsedItem, browser: FilesBrowserState, browse: (String) -> Unit): (() -> Unit)? {
    val gateway = LocalICodeGateway.current
    val viewer = LocalFileViewer.current
    return when {
        item.folder -> { { browse(item.location) } }
        browser.place == StoragePlace.Private -> { { gateway.dispatch(ICodeIntent.OpenFile(item.location)) } }
        else -> FileViewer.of(item.name)?.let { kind ->
            {
                viewer.show(item.location, item.name, kind)
                gateway.dispatch(FilesIntent.Opened(item.location))
            }
        }
    }
}

/** A folder's icon in the accent; a file's by its type. */
@Composable
internal fun iconOf(item: BrowsedItem): Pair<ImageVector, Color> =
    if (item.folder) Icons.Outlined.Folder to IcodeColors.Accent else FileTypes.of(item.name).let { fileTypeIcon(it) to fileTypeTint(it) }

/** A flat row, as ZArchiver's: [leading] — the type's icon, or a tick — the name over its detail, when it changed at the end. */
@Composable
private fun FileRow(
    leading: @Composable () -> Unit,
    name: String,
    detail: String?,
    changedAt: Long?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    IcodeListRow(
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        leading = leading,
        content = {
            Column(Modifier.weight(1f)) {
                Text(name, style = IcodeType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                detail?.let { Text(it, style = IcodeType.Caption, maxLines = 1) }
            }
        },
        trailing = { changedAt?.let { Text(RelativeTime.since(it), style = IcodeType.Caption) } },
    )
}
