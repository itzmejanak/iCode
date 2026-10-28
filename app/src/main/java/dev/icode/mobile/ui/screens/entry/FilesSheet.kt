package dev.icode.mobile.ui.screens.entry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.FileSelection
import dev.icode.mobile.domain.files.FilesBrowserState
import dev.icode.mobile.domain.files.FilesRules
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.ui.ActionTarget
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.ItemSheet
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.ScreenActions
import dev.icode.mobile.ui.SheetFolder
import dev.icode.mobile.ui.SheetIcon

/**
 * The file explorer's [ItemSheet] (docs/explorer-gaps-plan.md §3.1–§3.3, §3.10): one item, from its
 * long press, or a selection's [items], from its ⋯ — every explorer's actions ([FileItemActions]),
 * with the explorer's own [screen] actions. Open is what a tap does ([opening]); at Private's top the
 * items are workspaces, which Open opens in the IDE, with Open Terminal beside. In a folder ([here])
 * Paste goes into a folder, or beside a file into the folder shown; a view has none, gathering from
 * many, and [showInFolder] opens the one item's. Acted on, a selection ends ([onActed]).
 */
@Composable
internal fun FilesSheet(
    items: List<BrowsedItem>,
    here: FilesBrowserState?,
    opening: Opening,
    screen: ScreenActions,
    onActed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    showInFolder: (() -> Unit)? = null,
) {
    val gateway = LocalICodeGateway.current
    val context = LocalContext.current
    val state = LocalICodeSnapshot.current
    val top = here != null && here.place == StoragePlace.Private && here.atRoot
    val targets = items.map { ActionTarget(it.location, it.name, it.folder, it.path, workspace = top) }
    val folder = here?.let { shown ->
        SheetFolder(shown.name.takeUnless { shown.atRoot }, shown.runtimePathOf(shown.path), shown.items.map { it.name }.toSet(), shown.place == StoragePlace.Shared)
    }
    val one = items.singleOrNull()
    val open = one?.let { if (top) ({ gateway.dispatch(ICodeIntent.OpenWorkspace(it.location)) }) else opening.inIcode(it) }
    ItemSheet(
        targets = targets,
        title = one?.name ?: "${items.size} selected",
        detail = one?.let { if (here == null) FilesRules.gatheredDetail(it) else FilesRules.detail(it) } ?: FileSelection.summary(items, state.workspaceSizes),
        leading = {
            val (icon, tint) = one?.let { iconOf(it) } ?: (Icons.Outlined.Checklist to IcodeColors.Accent)
            SheetIcon(icon, tint)
        },
        onClose = onClose,
        modifier = modifier,
        folder = folder,
        branch = one?.branch,
        onActed = onActed,
    ) { rename, pages ->
        FileItemActions.of(
            context = context,
            dispatch = gateway::dispatch,
            targets = targets,
            explorer = state.explorer,
            onRename = rename,
            pages = pages,
            open = one?.let { item -> open?.let { if (item.folder) IcodeMenuAction("Open", Icons.Outlined.FolderOpen, it) else FileItemActions.openFile(it) } },
            onPaste = here?.let { shown -> one?.let { item -> { gateway.dispatch(FilesIntent.Paste(if (item.folder) item.location else shown.path)) } } },
            openTerminal = one?.takeIf { top }?.let { item -> { gateway.dispatch(ICodeIntent.OpenWorkspaceTerminal(item.location)) } },
            showInList = showInFolder,
            screen = screen,
        )
    }
}
