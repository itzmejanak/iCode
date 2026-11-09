package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeCheckbox
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.gitStatusTone
import dev.icode.mobile.domain.DiagnosticRules
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.files.FileSelection
import dev.icode.mobile.ui.ActionTarget
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.ItemSheet
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.ScreenActions
import dev.icode.mobile.ui.SheetFolder
import dev.icode.mobile.ui.SheetIcon

/**
 * The tree's [ItemSheet] (docs/explorer-gaps-plan.md §3.1, §3.2): one row's, from its long press, or
 * the selection's [entries], from its ⋯ — every explorer's actions ([FileItemActions]). For one, Open
 * is a file into the editor, a folder's Expand or Collapse, which only the tree has; Paste goes into a
 * folder row itself, or beside a file row into the folder it sits in ([creationParentLocation]); and
 * Select ([onSelect]) starts the tree's selection with it. A selection's entries are what it covers:
 * many in one folder are renamed and compressed in it, many across folders are not. Acted on, a
 * selection ends ([onActed]).
 */
@Composable
internal fun ExplorerItemSheet(
    entries: List<FileEntry>,
    onSelect: ((List<String>) -> Unit)?,
    onActed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val context = LocalContext.current
    val root = state.activeWorkspaceLocation
    val targets = entries.map { ActionTarget(it.path, it.name, it.kind == FileKind.Folder, state.runtimePathOf(it.path)) }
    val one = entries.singleOrNull()
    val folders = entries.count { it.kind == FileKind.Folder }
    val (icon, tint) = one?.let { itemIcon(it.name, it.kind == FileKind.Folder) } ?: (Icons.Outlined.Checklist to IcodeColors.Accent)
    // Many in one folder are acted on in it: its name, at the top the workspace's, and the names it holds.
    val folder = entries.map { it.path.substringBeforeLast('/') }.distinct().singleOrNull()?.takeIf { one == null }?.let { location ->
        SheetFolder(
            if (location == root) state.activeWorkspace else location.substringAfterLast('/'),
            state.runtimePathOf(location),
            state.files.filter { it.path.substringBeforeLast('/') == location }.map { it.name }.toSet(),
            ignoreCase = false,
        )
    }
    ItemSheet(
        targets = targets,
        title = one?.name ?: "${entries.size} selected",
        detail = one?.let { state.runtimePathOf(it.path) } ?: FileSelection.counted(folders, entries.size - folders),
        leading = { SheetIcon(icon, tint) },
        onClose = onClose,
        modifier = modifier,
        folder = folder,
        onActed = onActed,
    ) { rename, pages ->
        FileItemActions.of(
            context = context,
            dispatch = gateway::dispatch,
            targets = targets,
            explorer = state.explorer,
            onRename = rename,
            pages = pages,
            open = one?.let { entry ->
                if (entry.kind == FileKind.Folder) {
                    IcodeMenuAction(
                        if (entry.expanded) "Collapse" else "Expand",
                        if (entry.expanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                        { gateway.dispatch(ICodeIntent.ToggleFolder(entry.path)) },
                    )
                } else {
                    FileItemActions.openFile { gateway.dispatch(ICodeIntent.OpenFile(entry.path)) }
                }
            },
            onPaste = one?.let { entry -> { creationParentLocation(root, entry.path, state.files)?.let { gateway.dispatch(FilesIntent.Paste(it)) } } },
            screen = ScreenActions(pick = null, select = onSelect),
        )
    }
}

/**
 * One row of the Explorer's tree; a long press asks for its sheet ([ExplorerItemSheet]). In selection
 * its icon is its tick, [ticked] or not, and a ticked row is drawn as the selected one is.
 */
@Composable
internal fun ExplorerRow(
    entry: FileEntry,
    selected: Boolean,
    ticked: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** Its errors and warnings, or those beneath it (DiagnosticRules.countsByLocation). */
    problems: DiagnosticRules.Count? = null,
) {
    val folder = entry.kind == FileKind.Folder
    val problemTone = problems?.worst?.let { if (it == Severity.Error) IcodeTone.Error else IcodeTone.Warning }
    val type = FileTypes.of(entry.name)
    val icon = when {
        folder -> if (entry.expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
        else -> fileTypeIcon(type)
    }
    val iconTint = when {
        folder -> if (entry.expanded) IcodeColors.Accent else IcodeColors.TextSecondary
        else -> fileTypeTint(type)
    }
    IcodeListRow(
        selected = ticked ?: selected,
        selectionBackground = IcodeColors.Surface,
        rowShape = RectangleShape,
        indent = IcodeSpacing.IndentStep * entry.depth,
        onClick = onClick,
        onLongClick = onLongClick,
        leading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
            ) {
                if (entry.kind == FileKind.Folder) {
                    Icon(
                        if (entry.expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = IcodeColors.TextSecondary,
                        modifier = Modifier.size(IcodeSizes.IconSm),
                    )
                } else {
                    Spacer(Modifier.width(IcodeSizes.IconSm))
                }
                if (ticked != null) IcodeCheckbox(ticked, onCheckedChange = null, modifier = Modifier.size(IcodeSizes.IconMd))
                else Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(IcodeSizes.IconMd))
            }
        },
        // As in VS Code: a changed name takes its status colour; a file shows
        // its status letter, a folder a dot for the strongest change beneath it.
        content = {
            Text(
                entry.name,
                modifier = Modifier.weight(1f),
                style = IcodeType.Body,
                color = when {
                    // As in VS Code, a problem's colour comes before Git's.
                    problemTone != null -> problemTone.tint()
                    entry.gitStatus != null -> gitStatusTone(entry.gitStatus).tint()
                    !folder && type == FileTypes.Markdown -> IcodeColors.TextSecondary
                    else -> IcodeColors.TextPrimary
                },
            )
        },
        trailing = {
            val status = entry.gitStatus
            when {
                problemTone != null && problems != null ->
                    if (folder) StatusDot(problemTone.tint()) else MonoLine(problems.count.toString(), color = problemTone.tint(), style = IcodeType.MonoSmall)
                status == null -> Unit
                entry.kind == FileKind.Folder -> StatusDot(gitStatusTone(status).tint())
                else -> MonoLine(status, color = gitStatusTone(status).tint(), style = IcodeType.MonoSmall)
            }
        },
    )
}
