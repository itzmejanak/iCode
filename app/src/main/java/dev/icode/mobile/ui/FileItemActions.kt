package dev.icode.mobile.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
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
import dev.icode.mobile.domain.files.FileClipboard

/**
 * The one owner of a file's or a folder's actions (docs/file-explorer-unification-plan.md §3;
 * docs/storage-explorer-plan.md §3.2): the Explorer's tree and the file explorer — its workspaces,
 * their files, phone storage's — show the same list, in the same order, from a long press.
 */
object FileItemActions {
    /** "Paste RevPlayer": what Paste would bring, named, so a clipboard from another page is never a surprise. */
    fun pasteLabel(clipboard: FileClipboard?): String = clipboard?.let { "Paste ${it.name}" } ?: "Paste"

    /**
     * Open, Rename…, Cut, Copy, Paste, Copy path, Delete. [open] says what opening is here (a
     * file into the editor, a folder into the list or open in the tree); none where there is
     * nothing to open it in (a file on phone storage). A [file] adds Open with… and Share… after
     * it, to hand it to another app (docs/open-with-plan.md). A workspace adds Open Terminal after
     * Open, and where it is listed apart, [showInList]. Paste is always there, and named by what it
     * holds; with nothing held it is there, greyed. [onPaste] puts it where this item is the place
     * for it: into a folder, beside a file. Delete moves it to Trash at once: it can be undone
     * (docs/explorer-gaps-plan.md §3.7).
     */
    fun of(
        context: Context,
        dispatch: (ICodeIntent) -> Unit,
        location: String,
        runtimePath: String,
        clipboard: FileClipboard?,
        open: IcodeMenuAction?,
        onRename: () -> Unit,
        onPaste: () -> Unit,
        file: Boolean = false,
        openTerminal: (() -> Unit)? = null,
        showInList: (() -> Unit)? = null,
    ): List<IcodeMenuAction> = listOfNotNull(
        open,
        if (file) IcodeMenuAction("Open with…", Icons.AutoMirrored.Outlined.OpenInNew, { SharedFiles.open(context, location, dispatch) }) else null,
        if (file) IcodeMenuAction("Share…", Icons.Outlined.Share, { SharedFiles.share(context, location, dispatch) }) else null,
        openTerminal?.let { IcodeMenuAction("Open Terminal", Icons.Outlined.Terminal, it) },
        IcodeMenuAction("Rename…", Icons.Outlined.DriveFileRenameOutline, onRename),
        IcodeMenuAction("Cut", Icons.Outlined.ContentCut, { dispatch(FilesIntent.Cut(listOf(location))) }),
        IcodeMenuAction("Copy", Icons.Outlined.ContentCopy, { dispatch(FilesIntent.Copy(listOf(location))) }),
        IcodeMenuAction(pasteLabel(clipboard), Icons.Outlined.ContentPaste, onPaste, enabled = clipboard != null),
        showInList?.let { IcodeMenuAction("Show in Workspaces", Icons.Outlined.FolderCopy, it) },
        copyPath(context, runtimePath, dispatch),
        IcodeMenuAction("Delete", Icons.Outlined.DeleteOutline, { dispatch(FilesIntent.Trash(listOf(location))) }, destructive = true),
    )

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
