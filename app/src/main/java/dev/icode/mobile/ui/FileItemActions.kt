package dev.icode.mobile.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.files.ArchiveFormats
import dev.icode.mobile.domain.files.ExplorerState
import dev.icode.mobile.domain.files.FileClipboard
import dev.icode.mobile.domain.files.FolderPick
import dev.icode.mobile.domain.files.HeldItem

/** One item as its actions need it: where it is, its name, whether a folder or a whole workspace, and its runtime path. */
data class ActionTarget(val location: String, val name: String, val folder: Boolean, val runtimePath: String, val workspace: Boolean = false) {
    val held: HeldItem get() = HeldItem(location, name, folder, workspace)
}

/**
 * What opens a page of the item sheet (docs/explorer-gaps-plan.md §3.1): Compress… — none where the
 * sheet has no one folder for many — and Properties.
 */
class SheetPageActions(val compress: (() -> Unit)?, val properties: () -> Unit)

/**
 * What only an explorer's own screen offers: the file explorer's folder picker (§3.5), and selection,
 * starting with the items given (§3.2) — the file explorer's, and the IDE tree's.
 */
class ScreenActions(val pick: ((FolderPick) -> Unit)?, val select: ((List<String>) -> Unit)?)

/**
 * The one owner of the actions on files and folders (docs/explorer-gaps-plan.md §3.1, §3.3): every
 * explorer and the home screen show the same list, in the same order, in the item's sheet
 * ([ItemSheet]) — an item's, or a selection's. What differs is only what a place has to offer:
 * [open] (Expand in the IDE's tree), [showInList] on the home screen and in a view, [screen] in the
 * explorers' own screens.
 */
object FileItemActions {
    /** "Paste RevPlayer": what Paste would bring, named, so a clipboard from another page is never a surprise. */
    fun pasteLabel(clipboard: FileClipboard?): String = clipboard?.let { "Paste ${it.name}" } ?: "Paste"

    /**
     * The actions on [targets] — one item, or a selection's many — in groups (§3.1, §3.3). Opening,
     * for one: [open], what opening is here — a file into the editor, a folder into the list or open
     * in the tree; none where there is nothing to open it in — and for a file Open with…; Share… for
     * files, every one at once; a workspace's Open Terminal; and [showInList], the way to where it is
     * listed — a workspace's Workspaces, a view's file's folder (§3.10). Then Cut, Copy, one item's
     * Paste — named by what is held, greyed with nothing held; [onPaste] puts it into a folder, or
     * beside a file — the explorer's Move to… and Copy to…, and Duplicate. Rename…: one's name, or
     * many by a rule ([onRename], where there is one). Archives extracted here, or to a folder; else
     * Compress…, where the format and name are chosen, in one place: never at Private's top, where
     * only workspaces are. Folders' Favorites. Copy path or paths, and Properties. The explorer's
     * Select. Delete last, to Trash: it can be undone (§3.7). Rename…, Compress… and Properties open
     * [pages] of the sheet.
     */
    fun of(
        context: Context,
        dispatch: (ICodeIntent) -> Unit,
        targets: List<ActionTarget>,
        explorer: ExplorerState,
        onRename: (() -> Unit)?,
        pages: SheetPageActions,
        open: IcodeMenuAction? = null,
        onPaste: (() -> Unit)? = null,
        openTerminal: (() -> Unit)? = null,
        showInList: (() -> Unit)? = null,
        screen: ScreenActions? = null,
    ): List<IcodeMenuAction> {
        val one = targets.singleOrNull()
        val locations = targets.map { it.location }
        val files = targets.filterNot { it.folder }
        val folders = targets.filter { it.folder }
        val archives = files.mapNotNull { target -> ArchiveFormats.of(target.name)?.let { target to it } }
        val compressible = archives.size < targets.size && targets.none { it.workspace }
        return buildList {
            open?.let(::add)
            if (one != null && !one.folder) add(IcodeMenuAction("Open with…", Icons.AutoMirrored.Outlined.OpenInNew, { SharedFiles.open(context, one.location, dispatch) }))
            if (files.isNotEmpty() && folders.isEmpty()) add(IcodeMenuAction("Share…", Icons.Outlined.Share, { SharedFiles.share(context, files.map { it.location }, dispatch) }))
            openTerminal?.let { add(IcodeMenuAction("Open Terminal", Icons.Outlined.Terminal, it)) }
            showInList?.let { add(IcodeMenuAction(if (one?.workspace == true) "Show in Workspaces" else "Show in folder", Icons.Outlined.FolderCopy, it)) }
            add(IcodeMenuAction("Cut", Icons.Outlined.ContentCut, { dispatch(FilesIntent.Cut(locations)) }, beginsGroup = true))
            add(IcodeMenuAction("Copy", Icons.Outlined.ContentCopy, { dispatch(FilesIntent.Copy(locations)) }))
            onPaste?.let { add(IcodeMenuAction(pasteLabel(explorer.clipboard), Icons.Outlined.ContentPaste, it, enabled = explorer.clipboard != null)) }
            screen?.pick?.let { pick ->
                val held = targets.map { it.held }
                add(IcodeMenuAction("Move to…", Icons.AutoMirrored.Outlined.DriveFileMove, { pick(FolderPick.Transfer(held, move = true)) }))
                add(IcodeMenuAction("Copy to…", Icons.Outlined.FolderCopy, { pick(FolderPick.Transfer(held, move = false)) }))
            }
            add(IcodeMenuAction("Duplicate", Icons.Outlined.FileCopy, { dispatch(FilesIntent.Duplicate(locations)) }))
            onRename?.let { add(IcodeMenuAction("Rename…", Icons.Outlined.DriveFileRenameOutline, it, beginsGroup = true, opensPage = true)) }
            if (archives.size == targets.size) {
                val unreadable = archives.firstNotNullOfOrNull { it.second.unreadable }
                add(IcodeMenuAction("Extract here", Icons.Outlined.Unarchive, { locations.forEach { dispatch(FilesIntent.Extract(it)) } }, enabled = unreadable == null, detail = unreadable, beginsGroup = true))
                screen?.pick?.let { pick -> add(IcodeMenuAction("Extract to…", Icons.Outlined.Unarchive, { pick(FolderPick.Extract(locations)) }, enabled = unreadable == null)) }
            } else if (compressible) {
                pages.compress?.let { add(IcodeMenuAction("Compress…", Icons.Outlined.Archive, it, beginsGroup = true, opensPage = true)) }
            }
            if (folders.isNotEmpty()) {
                val unpinned = folders.filterNot { folder -> explorer.favorites.any { it.location == folder.location } }
                add(
                    if (unpinned.isEmpty()) {
                        IcodeMenuAction("Remove from Favorites", Icons.Outlined.StarBorder, { folders.forEach { dispatch(FilesIntent.Favorite(it.location, pinned = false)) } }, beginsGroup = true)
                    } else {
                        IcodeMenuAction("Add to Favorites", Icons.Outlined.Star, { unpinned.forEach { dispatch(FilesIntent.Favorite(it.location, pinned = true)) } }, beginsGroup = true)
                    },
                )
            }
            add(copyPath(context, targets.joinToString("\n") { it.runtimePath }, dispatch, many = one == null).copy(beginsGroup = true))
            add(IcodeMenuAction("Properties", Icons.Outlined.Info, pages.properties, opensPage = true))
            screen?.select?.let { select -> add(IcodeMenuAction("Select", Icons.Outlined.Checklist, { select(locations) }, beginsGroup = true)) }
            add(IcodeMenuAction("Delete", Icons.Outlined.DeleteOutline, { dispatch(FilesIntent.Trash(locations)) }, destructive = true))
        }
    }

    /**
     * Copy path: the runtime path — what a terminal, a script or another workspace's config names it
     * by; for [many], their paths, one to a line.
     */
    fun copyPath(context: Context, runtimePath: String, dispatch: (ICodeIntent) -> Unit, many: Boolean = false) =
        if (many) IcodeMenuAction("Copy paths", Icons.Outlined.Link, { copyText(context, "Paths", runtimePath, "Paths copied", dispatch) })
        else IcodeMenuAction("Copy path", Icons.Outlined.Link, { copyText(context, "Path", runtimePath, "Path copied", dispatch) })

    /**
     * What + makes in the folder shown: New folder, and New file where a file can go. Paste is the
     * paste bar's, shown whenever something is held (docs/storage-explorer-plan.md §3.3).
     */
    fun creation(onNewFile: (() -> Unit)?, onNewFolder: () -> Unit): List<IcodeMenuAction> =
        listOfNotNull(
            IcodeMenuAction("New folder", Icons.Outlined.CreateNewFolder, onNewFolder),
            onNewFile?.let { IcodeMenuAction("New file", Icons.Outlined.Description, it) },
        )

    /** Open, for a file: into the editor. */
    fun openFile(onOpen: () -> Unit) = IcodeMenuAction("Open", Icons.Outlined.Description, onOpen)
}

/** + in the Explorer's top bar, and the [actions] it offers ([FileItemActions.creation]). */
@Composable
fun NewItemButton(contentDescription: String, actions: List<IcodeMenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.Add, contentDescription = contentDescription, tint = IcodeColors.TextPrimary)
        }
        IcodeActionMenu(expanded = open, onDismiss = { open = false }, actions = actions)
    }
}
