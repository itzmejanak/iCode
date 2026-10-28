package dev.icode.mobile.ui.screens.entry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.FilesBrowserState
import dev.icode.mobile.domain.files.FilesRules
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.ui.ActionTarget
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.ItemSheet
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * The sheet of one item the explorer lists (docs/explorer-gaps-plan.md §3.1): every explorer's
 * actions for it ([FileItemActions]), with [ItemSheet]'s pages. Open is what a tap does; at
 * Private's top the item is a workspace, which Open opens in the IDE, with Open Terminal beside.
 * Paste goes into a folder, or beside a file into the folder shown.
 */
@Composable
internal fun FilesItemSheet(item: BrowsedItem, browser: FilesBrowserState, browse: (String) -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val context = LocalContext.current
    val explorer = LocalICodeSnapshot.current.explorer
    val workspace = browser.place == StoragePlace.Private && browser.atRoot
    val target = ActionTarget(item.location, item.name, item.folder, browser.runtimePathOf(item.location), workspace)
    val open = if (workspace) ({ gateway.dispatch(ICodeIntent.OpenWorkspace(item.location)) }) else openOf(item, browser, browse)
    val (icon, tint) = iconOf(item)
    ItemSheet(target, FilesRules.detail(item), icon, tint, onClose, modifier, branch = item.branch) { rename, pages ->
        FileItemActions.of(
            context = context,
            dispatch = gateway::dispatch,
            target = target,
            explorer = explorer,
            open = open?.let { if (item.folder) IcodeMenuAction("Open", Icons.Outlined.FolderOpen, it) else FileItemActions.openFile(it) },
            onRename = rename,
            onPaste = { gateway.dispatch(FilesIntent.Paste(if (item.folder) item.location else browser.path)) },
            openTerminal = if (workspace) ({ gateway.dispatch(ICodeIntent.OpenWorkspaceTerminal(item.location)) }) else null,
            pages = pages,
        )
    }
}
