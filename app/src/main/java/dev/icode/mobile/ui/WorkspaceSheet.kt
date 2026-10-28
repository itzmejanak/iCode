package dev.icode.mobile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.WorkspaceSummary

/**
 * A workspace's [ItemSheet] on the home screen, from its ⋮ or a long press (docs/workspaces-page-plan.md
 * §2.6): the actions every explorer has on a folder ([FileItemActions]), with Open and Open Terminal.
 * Cut and Copy put it on iCode's one clipboard; Paste puts what is held inside it. [onShowInList] is
 * the way from a recent row to the file explorer, where the workspace is listed.
 */
@Composable
internal fun WorkspaceSheet(
    item: WorkspaceSummary,
    onShowInList: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gateway = LocalICodeGateway.current
    val context = LocalContext.current
    val explorer = LocalICodeSnapshot.current.explorer
    val targets = listOf(ActionTarget(item.location, item.name, folder = true, runtimePath = item.path, workspace = true))
    ItemSheet(
        targets = targets,
        title = item.name,
        detail = listOfNotNull(item.path, item.branch).joinToString(" · "),
        leading = { SheetIcon(Icons.Outlined.Folder, IcodeColors.Accent) },
        onClose = onClose,
        modifier = modifier,
        branch = item.branch,
    ) { rename, pages ->
        FileItemActions.of(
            context = context,
            dispatch = gateway::dispatch,
            targets = targets,
            explorer = explorer,
            onRename = rename,
            pages = pages,
            open = IcodeMenuAction("Open", Icons.Outlined.FolderOpen, { gateway.dispatch(ICodeIntent.OpenWorkspace(item.location)) }),
            onPaste = { gateway.dispatch(FilesIntent.Paste(item.location)) },
            openTerminal = { gateway.dispatch(ICodeIntent.OpenWorkspaceTerminal(item.location)) },
            showInList = onShowInList,
        )
    }
}
