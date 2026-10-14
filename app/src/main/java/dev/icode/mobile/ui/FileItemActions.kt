package dev.icode.mobile.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
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
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ItemClipboard

/**
 * The one owner of a file's or a folder's actions (docs/file-explorer-unification-plan.md §3):
 * the Explorer's tree, the Workspaces page's files and its workspaces show the same list, in the
 * same order, from ⋮ and from a long press alike.
 */
object FileItemActions {
    /** "Paste RevPlayer": what Paste would bring, named, so a clipboard from another page is never a surprise. */
    fun pasteLabel(clipboard: ItemClipboard?): String = clipboard?.let { "Paste ${it.name}" } ?: "Paste"

    /**
     * Open, Rename…, Cut, Copy, Paste, Copy path, Delete…. [open] says what opening is here (a
     * file into the editor, a folder into the list or open in the tree). A workspace adds Open
     * Terminal after Open, and where it is listed apart, [showInList]. Paste is always there, and
     * named by what it holds; with nothing held it is there, greyed. [onPaste] puts it where this
     * item is the place for it: into a folder, beside a file.
     */
    fun of(
        context: Context,
        dispatch: (ICodeIntent) -> Unit,
        location: String,
        runtimePath: String,
        clipboard: ItemClipboard?,
        open: IcodeMenuAction,
        onRename: () -> Unit,
        onPaste: () -> Unit,
        onDelete: () -> Unit,
        openTerminal: (() -> Unit)? = null,
        showInList: (() -> Unit)? = null,
    ): List<IcodeMenuAction> = listOfNotNull(
        open,
        openTerminal?.let { IcodeMenuAction("Open Terminal", Icons.Outlined.Terminal, it) },
        IcodeMenuAction("Rename…", Icons.Outlined.DriveFileRenameOutline, onRename),
        IcodeMenuAction("Cut", Icons.Outlined.ContentCut, { dispatch(ICodeIntent.CutItem(location)) }),
        IcodeMenuAction("Copy", Icons.Outlined.ContentCopy, { dispatch(ICodeIntent.CopyItem(location)) }),
        IcodeMenuAction(pasteLabel(clipboard), Icons.Outlined.ContentPaste, onPaste, enabled = clipboard != null),
        showInList?.let { IcodeMenuAction("Show in Workspaces", Icons.Outlined.FolderCopy, it) },
        copyPath(context, runtimePath),
        IcodeMenuAction("Delete…", Icons.Outlined.DeleteOutline, onDelete, destructive = true),
    )

    /** Copy path: the runtime path — what a terminal, a script or another workspace's config names it by. */
    fun copyPath(context: Context, runtimePath: String) =
        IcodeMenuAction("Copy path", Icons.Outlined.Link, { copyText(context, "Path", runtimePath, "Path copied") })

    /**
     * What + makes in the folder shown: New file — where a file can go — New folder, and Paste,
     * named, while something is held.
     */
    fun creation(onNewFile: (() -> Unit)?, onNewFolder: () -> Unit, clipboard: ItemClipboard?, onPaste: (() -> Unit)?): List<IcodeMenuAction> =
        listOfNotNull(
            onNewFile?.let { IcodeMenuAction("New file", Icons.Outlined.Description, it) },
            IcodeMenuAction("New folder", Icons.Outlined.CreateNewFolder, onNewFolder),
            onPaste?.takeIf { clipboard != null }?.let { IcodeMenuAction(pasteLabel(clipboard), Icons.Outlined.ContentPaste, it) },
        )

    /** Open, for a file: into the editor. */
    fun openFile(onOpen: () -> Unit) = IcodeMenuAction("Open", Icons.Outlined.Description, onOpen)
}

/** + in a top bar, and the [actions] it offers ([FileItemActions.creation]). */
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
