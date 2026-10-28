package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeOverflowMenu
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.WorkspaceSummary

/**
 * One set of actions for a whole workspace, wherever one is listed, shown by a long press on it —
 * and on the home screen by its ⋮ too ([overflow]) (docs/workspaces-page-plan.md §2.6): every
 * file's and folder's ([FileItemActions]), with Open Terminal. Cut and Copy put it on iCode's one
 * clipboard; Paste puts what is held inside it. [onShowInList] is the way from the home screen's
 * recent rows to the file explorer; the explorer itself has none.
 */
@Composable
fun WorkspaceActionsMenu(
    item: WorkspaceSummary,
    longPressed: Boolean,
    onLongPressDismiss: () -> Unit,
    onShowInList: (() -> Unit)?,
    modifier: Modifier = Modifier,
    overflow: Boolean = true,
) {
    val gateway = LocalICodeGateway.current
    val context = LocalContext.current
    val explorer = LocalICodeSnapshot.current.explorer
    var renaming by remember(item.location) { mutableStateOf(false) }
    val actions = FileItemActions.of(
        context = context,
        dispatch = gateway::dispatch,
        target = ActionTarget(item.location, item.name, folder = true, runtimePath = item.path, workspace = true),
        explorer = explorer,
        open = IcodeMenuAction("Open", Icons.Outlined.FolderOpen, { gateway.dispatch(ICodeIntent.OpenWorkspace(item.location)) }),
        onRename = { renaming = true },
        onPaste = { gateway.dispatch(FilesIntent.Paste(item.location)) },
        openTerminal = { gateway.dispatch(ICodeIntent.OpenWorkspaceTerminal(item.location)) },
        showInList = onShowInList,
    )
    Box(modifier) {
        if (overflow) IcodeOverflowMenu(contentDescription = "More options for ${item.name}", actions = actions)
        IcodeActionMenu(expanded = longPressed, onDismiss = onLongPressDismiss, actions = actions)
    }
    if (renaming) RenameDialog(item.location, item.name, folder = true, workspace = true) { renaming = false }
}
