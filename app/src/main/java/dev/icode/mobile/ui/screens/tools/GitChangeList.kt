package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionHeader
import dev.icode.mobile.designsystem.gitStatusTone
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitChangeRow
import dev.icode.mobile.domain.GitDiscard
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.screens.workspace.fileTypeIcon
import dev.icode.mobile.ui.screens.workspace.fileTypeTint

/**
 * One section of Source Control's changes — staged, or not yet — as a tree (docs/workspace-
 * destinations-plan.md G14), compact as the Explorer's: its header with Stage all or Unstage all,
 * then [rows]. A tap opens: a folder expands and collapses, a file shows its diff (or itself, when
 * new). + or − stages. A long press gives the rest — Open file, Stage or Unstage, and the discard
 * that fits: Discard changes, Delete file, Restore file — for a file and for all a folder holds.
 */
internal fun LazyListScope.gitChangeSection(
    title: String,
    changes: List<GitChange>,
    staged: Boolean,
    rows: List<GitChangeRow>,
    workTreePath: String,
    onToggleFolder: (String) -> Unit,
    gateway: WorkbenchGateway,
    actions: NavigationActions,
) {
    val section = if (staged) "staged" else "changes"
    val toggleStage = { path: String -> gateway.dispatch(if (staged) WorkbenchIntent.UnstageFile(path) else WorkbenchIntent.StageFile(path)) }
    val discard = { path: String -> gateway.dispatch(WorkbenchIntent.RequestDiscardChanges(path)) }
    item(key = "$section:header") {
        SectionHeader(
            "$title (${changes.size})",
            modifier = Modifier.padding(top = IcodeSpacing.Space3, bottom = IcodeSpacing.Space1),
            actionLabel = if (changes.isEmpty()) null else if (staged) "UNSTAGE ALL" else "STAGE ALL",
            onAction = { gateway.dispatch(if (staged) WorkbenchIntent.UnstageAllFiles else WorkbenchIntent.StageAllFiles) },
        )
    }
    items(rows, key = { row -> "$section:" + if (row is GitChangeRow.Folder) "folder:${row.path}" else "file:${(row as GitChangeRow.File).change.path}" }) { row ->
        when (row) {
            is GitChangeRow.Folder -> ChangeFolderRow(
                row,
                staged,
                onToggle = { onToggleFolder(row.path) },
                onToggleStage = { toggleStage(row.path) },
                // Only what can be thrown away: nothing staged, no conflict.
                onDiscard = { discard(row.path) }.takeIf { GitDiscard.of(changes, row.path) != null },
            )
            is GitChangeRow.File -> {
                val openFile = { gateway.dispatch(WorkbenchIntent.OpenFile("$workTreePath/${row.change.path}")) }
                ChangeFileRow(
                    row,
                    onOpen = { openChange(gateway, actions, row.change, openFile) },
                    onToggleStage = { toggleStage(row.change.path) },
                    onOpenFile = openFile,
                    onDiscard = { discard(row.change.path) },
                )
            }
        }
    }
}

@Composable
private fun ChangeFolderRow(folder: GitChangeRow.Folder, staged: Boolean, onToggle: () -> Unit, onToggleStage: () -> Unit, onDiscard: (() -> Unit)?) {
    var held by remember(folder.path) { mutableStateOf(false) }
    IcodeListRow(
        rowShape = RectangleShape,
        compact = true,
        indent = IcodeSpacing.IndentStep * folder.depth,
        onClick = onToggle,
        onLongClick = { held = true },
        leading = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Icon(
                    if (folder.expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = if (folder.expanded) "Collapse ${folder.name}" else "Expand ${folder.name}",
                    tint = IcodeColors.TextSecondary,
                    modifier = Modifier.size(IcodeSizes.IconSm),
                )
                Icon(
                    if (folder.expanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = if (folder.expanded) IcodeColors.Accent else IcodeColors.TextSecondary,
                    modifier = Modifier.size(IcodeSizes.IconMd),
                )
            }
        },
        content = { Text(folder.name, Modifier.weight(1f), style = IcodeType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailing = {
            MonoLine(folder.changes.toString(), color = IcodeColors.TextMuted, style = IcodeType.MonoSmall)
            Box {
                StageButton(staged, folder.name, onToggleStage)
                IcodeActionMenu(
                    expanded = held,
                    onDismiss = { held = false },
                    actions = listOfNotNull(
                        stageAction(staged, onToggleStage),
                        onDiscard?.let { IcodeMenuAction("Discard changes", Icons.Outlined.DeleteOutline, it, destructive = true) },
                    ),
                )
            }
        },
    )
}

@Composable
private fun ChangeFileRow(
    file: GitChangeRow.File,
    onOpen: () -> Unit,
    onToggleStage: () -> Unit,
    onOpenFile: () -> Unit,
    onDiscard: () -> Unit,
) {
    val change = file.change
    val tone = gitStatusTone(change.status).tint()
    val type = FileTypes.of(change.name)
    val deleted = change.status == GitStatusCodes.DELETED
    var held by remember(change.path) { mutableStateOf(false) }
    IcodeListRow(
        rowShape = RectangleShape,
        compact = true,
        indent = IcodeSpacing.IndentStep * file.depth,
        onClick = onOpen,
        onLongClick = { held = true },
        leading = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Spacer(Modifier.width(IcodeSizes.IconSm))
                Icon(fileTypeIcon(type), contentDescription = null, tint = fileTypeTint(type), modifier = Modifier.size(IcodeSizes.IconMd))
            }
        },
        // As the Explorer: the name in its status colour, the status letter at the end.
        content = {
            Text(
                change.name,
                Modifier.weight(1f),
                style = IcodeType.Body.copy(color = tone, textDecoration = if (deleted) TextDecoration.LineThrough else null),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = {
            MonoLine(change.status, color = tone, style = IcodeType.MonoSmall)
            Box {
                StageButton(change.staged, change.name, onToggleStage)
                IcodeActionMenu(
                    expanded = held,
                    onDismiss = { held = false },
                    actions = listOfNotNull(
                        IcodeMenuAction("Open file", Icons.Outlined.Description, onOpenFile).takeUnless { deleted },
                        stageAction(change.staged, onToggleStage),
                        // Destructive, so last, and confirmed before anything is lost.
                        IcodeMenuAction(discardLabel(change), Icons.Outlined.DeleteOutline, onDiscard, destructive = true).takeIf { change.discardable },
                    ),
                )
            }
        },
    )
}

private fun stageAction(staged: Boolean, onClick: () -> Unit) =
    IcodeMenuAction(if (staged) "Unstage" else "Stage", if (staged) Icons.Outlined.Remove else Icons.Outlined.Add, onClick)

/** What discarding does to this change, as its menu says it. */
private fun discardLabel(change: GitChange): String = when (change.status) {
    GitStatusCodes.UNTRACKED -> "Delete file"
    GitStatusCodes.DELETED -> "Restore file"
    else -> "Discard changes"
}

/** + stages, − unstages: a file, or all a folder holds. */
@Composable
private fun StageButton(staged: Boolean, name: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (staged) Icons.Outlined.Remove else Icons.Outlined.Add,
            contentDescription = if (staged) "Unstage $name" else "Stage $name",
            tint = IcodeColors.Accent,
            modifier = Modifier.size(IcodeSizes.IconMd),
        )
    }
}


/**
 * Selecting a change opens the surface that explains it: the conflict workflow,
 * the confirmation that restores a deleted file, the file itself when it is new
 * to Git — there is nothing to compare it with, so VS Code opens it too — or the
 * diff. Staging stays on the trailing action so a review tap can never stage by
 * accident. The diff is requested before navigating so the diff surface renders
 * Git's own patch.
 */
private fun openChange(gateway: WorkbenchGateway, actions: NavigationActions, change: GitChange, openFile: () -> Unit) {
    when {
        change.status == GitStatusCodes.UNTRACKED -> openFile()
        change.status == GitStatusCodes.CONFLICTED -> {
            gateway.dispatch(WorkbenchIntent.OpenConflict(change.path))
            actions.navigate(Route.ConflictResolution)
        }
        // Opening a deletion asks whether to restore the file.
        change.status == GitStatusCodes.DELETED && change.discardable -> gateway.dispatch(WorkbenchIntent.RequestDiscardChanges(change.path))
        else -> {
            gateway.dispatch(WorkbenchIntent.OpenDiff(change.path, change.staged))
            actions.navigate(Route.DiffView)
        }
    }
}
