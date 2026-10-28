package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.gitStatusTone
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.ui.ActionTarget
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.ItemSheet
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.SheetIcon

/**
 * One Explorer row's [ItemSheet], from a long press: every explorer's actions ([FileItemActions]).
 * Open is a file into the editor; a folder's is Expand or Collapse, which only the tree has. Paste
 * goes into a folder row itself, or beside a file row into the folder it sits in
 * ([creationParentLocation]).
 */
@Composable
internal fun ExplorerItemSheet(entry: FileEntry, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val context = LocalContext.current
    val root = state.activeWorkspaceLocation
    val folder = entry.kind == FileKind.Folder
    val runtimePath = root?.let { WorkspaceRuntimePath.within(it, state.activePath, entry.path) } ?: entry.path
    val targets = listOf(ActionTarget(entry.path, entry.name, folder, runtimePath))
    val (icon, tint) = itemIcon(entry.name, folder)
    ItemSheet(
        targets = targets,
        title = entry.name,
        detail = runtimePath,
        leading = { SheetIcon(icon, tint) },
        onClose = onClose,
        modifier = modifier,
    ) { rename, pages ->
        FileItemActions.of(
            context = context,
            dispatch = gateway::dispatch,
            targets = targets,
            explorer = state.explorer,
            onRename = rename,
            pages = pages,
            open = if (folder) {
                IcodeMenuAction(
                    if (entry.expanded) "Collapse" else "Expand",
                    if (entry.expanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                    { gateway.dispatch(ICodeIntent.ToggleFolder(entry.path)) },
                )
            } else {
                FileItemActions.openFile { gateway.dispatch(ICodeIntent.OpenFile(entry.path)) }
            },
            onPaste = { creationParentLocation(root, entry.path, state.files)?.let { gateway.dispatch(FilesIntent.Paste(it)) } },
        )
    }
}

/** One row of the Explorer's tree; a long press asks for its sheet ([ExplorerItemSheet]). */
@Composable
internal fun ExplorerRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val folder = entry.kind == FileKind.Folder
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
        selected = selected,
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
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(IcodeSizes.IconMd))
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
                    entry.gitStatus != null -> gitStatusTone(entry.gitStatus).tint()
                    !folder && type == FileTypes.Markdown -> IcodeColors.TextSecondary
                    else -> IcodeColors.TextPrimary
                },
            )
        },
        trailing = {
            val status = entry.gitStatus
            when {
                status == null -> Unit
                entry.kind == FileKind.Folder -> StatusDot(gitStatusTone(status).tint())
                else -> MonoLine(status, color = gitStatusTone(status).tint(), style = IcodeType.MonoSmall)
            }
        },
    )
}
