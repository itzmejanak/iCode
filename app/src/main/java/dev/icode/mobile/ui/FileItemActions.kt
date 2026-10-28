package dev.icode.mobile.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
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
import dev.icode.mobile.domain.files.CompressFormat
import dev.icode.mobile.domain.files.ExplorerState
import dev.icode.mobile.domain.files.FileClipboard

/** One item as its actions need it: where it is, its name, whether a folder or a whole workspace, and its runtime path. */
data class ActionTarget(val location: String, val name: String, val folder: Boolean, val runtimePath: String, val workspace: Boolean = false)

/** What only an item's sheet offers, as pages of it (docs/explorer-gaps-plan.md §3.1): Compress… and Properties. */
class SheetPageActions(val compress: () -> Unit, val properties: () -> Unit)

/**
 * The one owner of a file's or a folder's actions (docs/explorer-gaps-plan.md §3.1): every explorer
 * shows the same list, in the same order, in the item's sheet or in a dropdown where one is left.
 */
object FileItemActions {
    /** "Paste RevPlayer": what Paste would bring, named, so a clipboard from another page is never a surprise. */
    fun pasteLabel(clipboard: FileClipboard?): String = clipboard?.let { "Paste ${it.name}" } ?: "Paste"

    /**
     * [target]'s actions, in groups (§3.1). Opening: [open] — what opening is here, a file into the
     * editor, a folder into the list or open in the tree; none where there is nothing to open it in —
     * then, for a file, Open with… and Share… to hand it to another app (docs/open-with-plan.md); a
     * workspace's Open Terminal and, where it is listed apart, [showInList]. Then Cut, Copy, Paste —
     * named by what is held, greyed with nothing held; [onPaste] puts it where this item is the place
     * for it: into a folder, beside a file — and Duplicate. Rename… ([onRename]). An archive's Extract
     * here, or Compress to a zip, a 7z or, with [pages], Compress…; not for a whole workspace, whose
     * archive would be at Private's top. A folder's Favorites. Copy path, and with [pages] Properties.
     * Delete last, to Trash: it can be undone (§3.7). With [pages], the list is a sheet's: Rename… and
     * the pages open within it.
     */
    fun of(
        context: Context,
        dispatch: (ICodeIntent) -> Unit,
        target: ActionTarget,
        explorer: ExplorerState,
        open: IcodeMenuAction?,
        onRename: () -> Unit,
        onPaste: () -> Unit,
        openTerminal: (() -> Unit)? = null,
        showInList: (() -> Unit)? = null,
        pages: SheetPageActions? = null,
    ): List<IcodeMenuAction> {
        val file = !target.folder
        val only = listOf(target.location)
        val archive = ArchiveFormats.of(target.name)?.takeIf { file }
        return buildList {
            open?.let(::add)
            if (file) {
                add(IcodeMenuAction("Open with…", Icons.AutoMirrored.Outlined.OpenInNew, { SharedFiles.open(context, target.location, dispatch) }))
                add(IcodeMenuAction("Share…", Icons.Outlined.Share, { SharedFiles.share(context, target.location, dispatch) }))
            }
            openTerminal?.let { add(IcodeMenuAction("Open Terminal", Icons.Outlined.Terminal, it)) }
            showInList?.let { add(IcodeMenuAction("Show in Workspaces", Icons.Outlined.FolderCopy, it)) }
            add(IcodeMenuAction("Cut", Icons.Outlined.ContentCut, { dispatch(FilesIntent.Cut(only)) }, beginsGroup = true))
            add(IcodeMenuAction("Copy", Icons.Outlined.ContentCopy, { dispatch(FilesIntent.Copy(only)) }))
            add(IcodeMenuAction(pasteLabel(explorer.clipboard), Icons.Outlined.ContentPaste, onPaste, enabled = explorer.clipboard != null))
            add(IcodeMenuAction("Duplicate", Icons.Outlined.FileCopy, { dispatch(FilesIntent.Duplicate(only)) }))
            add(IcodeMenuAction("Rename…", Icons.Outlined.DriveFileRenameOutline, onRename, beginsGroup = true, opensPage = pages != null))
            when {
                archive != null -> add(
                    IcodeMenuAction("Extract here", Icons.Outlined.Unarchive, { dispatch(FilesIntent.Extract(target.location)) }, enabled = archive.unreadable == null, detail = archive.unreadable, beginsGroup = true),
                )
                !target.workspace -> {
                    val stem = ArchiveFormats.archiveName(target.name to target.folder, null)
                    add(IcodeMenuAction("Compress to $stem${CompressFormat.Zip.extension}", Icons.Outlined.Archive, { dispatch(FilesIntent.Compress(only, CompressFormat.Zip)) }, beginsGroup = true))
                    add(IcodeMenuAction("Compress to $stem${CompressFormat.SevenZ.extension}", Icons.Outlined.Archive, { dispatch(FilesIntent.Compress(only, CompressFormat.SevenZ)) }))
                    pages?.let { add(IcodeMenuAction("Compress…", Icons.Outlined.Archive, it.compress, opensPage = true)) }
                }
            }
            if (target.folder) {
                val pinned = target.location in explorer.favorites
                add(
                    IcodeMenuAction(
                        if (pinned) "Remove from Favorites" else "Add to Favorites",
                        if (pinned) Icons.Outlined.StarBorder else Icons.Outlined.Star,
                        { dispatch(FilesIntent.Favorite(target.location, pinned = !pinned)) },
                        beginsGroup = true,
                    ),
                )
            }
            add(copyPath(context, target.runtimePath, dispatch).copy(beginsGroup = true))
            pages?.let { add(IcodeMenuAction("Properties", Icons.Outlined.Info, it.properties, opensPage = true)) }
            add(IcodeMenuAction("Delete", Icons.Outlined.DeleteOutline, { dispatch(FilesIntent.Trash(only)) }, destructive = true))
        }
    }

    /** Copy path: the runtime path — what a terminal, a script or another workspace's config names it by. */
    fun copyPath(context: Context, runtimePath: String, dispatch: (ICodeIntent) -> Unit) =
        IcodeMenuAction("Copy path", Icons.Outlined.Link, { copyText(context, "Path", runtimePath, "Path copied", dispatch) })

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
