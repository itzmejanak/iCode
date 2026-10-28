package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSizes
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

/**
 * The file explorer's [ItemSheet] (docs/explorer-gaps-plan.md §3.1–§3.3): one item, from its long
 * press, or a selection's [items], from its ⋯ — every explorer's actions ([FileItemActions]), with
 * the explorer's own [screen] actions. Open is what a tap does; at
 * Private's top the items are workspaces, which Open opens in the IDE, with Open Terminal beside.
 * Paste goes into a folder, or beside a file into the folder shown. Each action but a page also does
 * [onActed]: a selection ends once it is acted on.
 */
@Composable
internal fun FilesSheet(
    items: List<BrowsedItem>,
    browser: FilesBrowserState,
    browse: (String) -> Unit,
    screen: ScreenActions,
    onActed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val gateway = LocalICodeGateway.current
    val context = LocalContext.current
    val state = LocalICodeSnapshot.current
    val top = browser.place == StoragePlace.Private && browser.atRoot
    val targets = items.map { ActionTarget(it.location, it.name, it.folder, browser.runtimePathOf(it.location), workspace = top) }
    val folder = SheetFolder(browser.name.takeUnless { browser.atRoot }, browser.runtimePathOf(browser.path), browser.items.map { it.name }.toSet(), browser.place == StoragePlace.Shared)
    val one = items.singleOrNull()
    val open = one?.let { if (top) ({ gateway.dispatch(ICodeIntent.OpenWorkspace(it.location)) }) else openOf(it, browser, browse) }
    ItemSheet(
        targets = targets,
        title = one?.name ?: "${items.size} selected",
        detail = one?.let(FilesRules::detail) ?: FileSelection.summary(items, state.workspaceSizes),
        leading = {
            val (icon, tint) = one?.let { iconOf(it) } ?: (Icons.Outlined.Checklist to IcodeColors.Accent)
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IcodeSizes.IconXl))
        },
        onClose = onClose,
        modifier = modifier,
        folder = folder,
        branch = one?.branch,
    ) { rename, pages ->
        FileItemActions.of(
            context = context,
            dispatch = gateway::dispatch,
            targets = targets,
            explorer = state.explorer,
            onRename = rename,
            pages = pages,
            open = one?.let { item -> open?.let { if (item.folder) IcodeMenuAction("Open", Icons.Outlined.FolderOpen, it) else FileItemActions.openFile(it) } },
            onPaste = one?.let { item -> { gateway.dispatch(FilesIntent.Paste(if (item.folder) item.location else browser.path)) } },
            openTerminal = one?.takeIf { top }?.let { item -> { gateway.dispatch(ICodeIntent.OpenWorkspaceTerminal(item.location)) } },
            screen = screen,
        ).map { action -> if (action.opensPage) action else action.copy(onClick = { action.onClick(); onActed() }) }
    }
}
