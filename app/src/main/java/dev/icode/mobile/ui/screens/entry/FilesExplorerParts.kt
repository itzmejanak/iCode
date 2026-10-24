package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeActionBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.domain.BrowsedItem
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.FileViewer
import dev.icode.mobile.domain.FilesBrowserState
import dev.icode.mobile.domain.FilesRules
import dev.icode.mobile.domain.FilesSort
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.StoragePlace
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.LocalFileViewer
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.SharedFiles
import dev.icode.mobile.ui.WorkspaceActionsMenu
import dev.icode.mobile.ui.screens.workspace.fileTypeIcon
import dev.icode.mobile.ui.screens.workspace.fileTypeTint

/**
 * What is here, found and ordered (docs/storage-explorer-plan.md §3.2): `..` first while there is a
 * folder above, then folders and files as flat rows — at Private's top, the workspaces. Room is
 * left under the last row for the floating +.
 */
@Composable
internal fun FilesListing(
    browser: FilesBrowserState,
    query: String,
    sort: FilesSort,
    foldersOnly: Boolean,
    browse: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val state = LocalICodeSnapshot.current
    val here = if (foldersOnly) browser.items.filter { it.folder } else browser.items
    val shown = FilesRules.sorted(FilesRules.matching(here, query), sort, state.workspaceSizes)
    val top = browser.place == StoragePlace.Private && browser.atRoot
    LazyColumn(contentPadding = PaddingValues(start = IcodeSpacing.RowGutter, end = IcodeSpacing.RowGutter, bottom = IcodeSizes.FabClearance)) {
        browser.parent?.let { parent -> item(key = "..") { UpRow { browse(parent) } } }
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
                        top && foldersOnly -> "New workspace below gives it a workspace of its own."
                        top -> "+ makes one, or copy a folder in from Shared."
                        foldersOnly -> "Place here puts it in this folder; + makes a folder in it."
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
            else -> items(shown, key = { it.location }) { item ->
                if (top) WorkspaceItemRow(item, browse) else BrowsedItemRow(item, browser, browse, { onRename(item.location) }, { onDelete(item.location) })
            }
        }
    }
}

/** `..`: up a folder, as Back. */
@Composable
private fun UpRow(onClick: () -> Unit) {
    FileRow(Icons.Outlined.DriveFolderUpload, IcodeColors.Accent, "..", detail = null, changedAt = null, onClick = onClick, onLongClick = null) {}
}

/**
 * One folder or file, in a workspace or on phone storage. A tap goes into a folder, opens a file of
 * a workspace in the editor — one it cannot show in another app — and a file on phone storage, having
 * no editor to open in, in another app (docs/open-with-plan.md). A picture opens in iCode's image
 * viewer wherever it is (docs/file-viewers-plan.md). A long press shows every explorer's menu
 * ([FileItemActions]). Paste goes into a folder; beside a file, into the folder shown.
 */
@Composable
private fun BrowsedItemRow(item: BrowsedItem, browser: FilesBrowserState, browse: (String) -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val context = LocalContext.current
    var menuOpen by remember(item.location) { mutableStateOf(false) }
    val type = FileTypes.of(item.name)
    val viewer = LocalFileViewer.current
    val open: (() -> Unit)? = when {
        item.folder -> { { browse(item.location) } }
        browser.place == StoragePlace.Private -> { { gateway.dispatch(ICodeIntent.OpenFile(item.location)) } }
        else -> FileViewer.of(item.name)?.let { kind -> { viewer.show(item.location, item.name, kind) } }
    }
    val actions = FileItemActions.of(
        context = context,
        dispatch = gateway::dispatch,
        location = item.location,
        runtimePath = browser.runtimePathOf(item.location),
        clipboard = LocalICodeSnapshot.current.clipboard,
        open = open?.let { if (item.folder) IcodeMenuAction("Open", Icons.Outlined.FolderOpen, it) else FileItemActions.openFile(it) },
        onRename = onRename,
        onPaste = { gateway.dispatch(ICodeIntent.PasteInto(if (item.folder) item.location else browser.path)) },
        onDelete = onDelete,
        file = !item.folder,
    )
    FileRow(
        icon = if (item.folder) Icons.Outlined.Folder else fileTypeIcon(type),
        tint = if (item.folder) IcodeColors.Accent else fileTypeTint(type),
        name = item.name,
        detail = FilesRules.detail(item),
        changedAt = item.changedAt,
        onClick = open ?: { SharedFiles.open(context, item.location, gateway::dispatch) },
        onLongClick = { menuOpen = true },
    ) {
        IcodeActionMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, actions = actions)
    }
}

/** A workspace at Private's top: a tap goes into it; a long press gives a workspace's menu (Open, Open Terminal…). */
@Composable
private fun WorkspaceItemRow(item: BrowsedItem, browse: (String) -> Unit) {
    val workspace = LocalICodeSnapshot.current.workspaces.firstOrNull { it.location == item.location }
    var menuOpen by remember(item.location) { mutableStateOf(false) }
    FileRow(
        icon = Icons.Outlined.Folder,
        tint = IcodeColors.Accent,
        name = item.name,
        detail = FilesRules.detail(item),
        changedAt = item.changedAt,
        onClick = { browse(item.location) },
        onLongClick = { menuOpen = true }.takeIf { workspace != null },
    ) {
        workspace?.let { WorkspaceActionsMenu(it, menuOpen, onLongPressDismiss = { menuOpen = false }, onShowInList = null, overflow = false) }
    }
}

/** A flat row, as ZArchiver's: the type's icon, the name over its detail, when it changed at the end; [menu] opens there. */
@Composable
private fun FileRow(
    icon: ImageVector,
    tint: Color,
    name: String,
    detail: String?,
    changedAt: Long?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    menu: @Composable () -> Unit,
) {
    IcodeListRow(
        onClick = onClick,
        onLongClick = onLongClick,
        leading = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IcodeSizes.IconXl)) },
        content = {
            Column(Modifier.weight(1f)) {
                Text(name, style = IcodeType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                detail?.let { Text(it, style = IcodeType.Caption, maxLines = 1) }
            }
        },
        trailing = {
            Box {
                changedAt?.let { Text(RelativeTime.since(it), style = IcodeType.Caption) }
                menu()
            }
        },
    )
}

/** Choosing where a project goes: the place it will be, and the button that says so. */
@Composable
internal fun ChooseBar(browser: FilesBrowserState?, onChoose: (String?) -> Unit) {
    // A folder inside a workspace; at the top, none: a new workspace.
    val place = browser?.takeUnless { it.atRoot }
    IcodeActionBar(
        title = place?.runtimePathOf(place.path) ?: "/workspace/<name>",
        caption = if (place == null) "It becomes a workspace of its own" else "It goes in this folder",
        titleStyle = IcodeType.Mono,
    ) {
        PrimaryButton(if (place == null) "New workspace" else "Place here", { onChoose(place?.path) }, enabled = browser != null)
    }
}
