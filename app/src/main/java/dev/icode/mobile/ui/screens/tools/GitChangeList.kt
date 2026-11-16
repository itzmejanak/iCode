package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeActionSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeIconButton
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionHeader
import dev.icode.mobile.designsystem.gitStatusTone
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.domain.BusyRules
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitChangeRow
import dev.icode.mobile.domain.GitDiscard
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.FileTypeIcon
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.SheetIcon
import dev.icode.mobile.ui.parentName

/**
 * One section of Source Control's changes — staged, or not yet — as a tree (docs/workspace-
 * docs/workspaces-and-files.md), compact as the Explorer's: its header with Stage all or Unstage all,
 * then [rows]. A tap opens: a folder expands and collapses, a file shows its diff (or itself, when
 * new). + or − stages. A long press opens the row's sheet ([onHold], [GitChangeSheet]).
 */
internal fun LazyListScope.gitChangeSection(
    title: String,
    changes: List<GitChange>,
    staged: Boolean,
    rows: List<GitChangeRow>,
    workTreePath: String,
    onToggleFolder: (String) -> Unit,
    onHold: (GitChangeRow) -> Unit,
    gateway: ICodeGateway,
    actions: NavigationActions,
    /** The paths being staged or unstaged now; [BusyRules.ALL] for all of them. */
    busy: Set<String>,
) {
    val section = if (staged) "staged" else "changes"
    item(key = "$section:header") {
        SectionHeader(
            "$title (${changes.size})",
            modifier = Modifier.padding(top = IcodeSpacing.Space3, bottom = IcodeSpacing.Space1),
            actionLabel = if (changes.isEmpty()) null else if (staged) "UNSTAGE ALL" else "STAGE ALL",
            onAction = { gateway.dispatch(if (staged) ICodeIntent.UnstageAllFiles else ICodeIntent.StageAllFiles) },
            busy = BusyRules.ALL in busy,
        )
    }
    items(rows, key = { row -> changeRowKey(staged, row) }) { row ->
        when (row) {
            is GitChangeRow.Folder -> ChangeFolderRow(
                row,
                staged,
                onToggle = { onToggleFolder(row.path) },
                onToggleStage = { gateway.dispatch(stageIntent(staged, row.path)) },
                onHold = { onHold(row) },
                staging = row.path in busy || BusyRules.ALL in busy,
            )
            is GitChangeRow.File -> ChangeFileRow(
                row,
                onOpen = { openChange(gateway, actions, row.change, workTreePath) },
                onToggleStage = { gateway.dispatch(stageIntent(staged, row.change.path)) },
                onHold = { onHold(row) },
                staging = row.change.path in busy || BusyRules.ALL in busy,
            )
        }
    }
}

/** What names [row] in its section's list, and the row whose sheet is shown. */
internal fun changeRowKey(staged: Boolean, row: GitChangeRow): String =
    (if (staged) "staged:" else "changes:") + when (row) {
        is GitChangeRow.Folder -> "folder:${row.path}"
        is GitChangeRow.File -> "file:${row.change.path}"
    }

/**
 * A change row's sheet (docs/workspaces-and-files.md), titled with it: for a file, Open file
 * (unless it is deleted), Stage or Unstage, and the discard that fits — Discard changes, Delete file,
 * Restore file; for a folder, Stage or Unstage and Discard changes, for all it holds. Each discard is
 * destructive, so last, and confirmed before anything is lost; only what can be thrown away — nothing
 * staged, no conflict — offers one. [changes] are the row's section's.
 */
@Composable
internal fun GitChangeSheet(row: GitChangeRow, staged: Boolean, changes: List<GitChange>, workTreePath: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val gateway = LocalICodeGateway.current
    val discard = { path: String -> gateway.dispatch(ICodeIntent.RequestDiscardChanges(path)) }
    when (row) {
        is GitChangeRow.Folder -> IcodeActionSheet(
            title = row.name,
            actions = listOfNotNull(
                stageAction(staged) { gateway.dispatch(stageIntent(staged, row.path)) },
                IcodeMenuAction("Discard changes", Icons.Outlined.DeleteOutline, { discard(row.path) }, destructive = true).takeIf { GitDiscard.of(changes, row.path) != null },
            ),
            onClose = onClose,
            modifier = modifier,
            detail = "${row.path} · ${countOf(row.changes, "change")}",
            leading = { SheetIcon(row.name.substringAfterLast('/'), folder = true, row.path) },
        )
        is GitChangeRow.File -> {
            val change = row.change
            IcodeActionSheet(
                title = change.name,
                actions = listOfNotNull(
                    IcodeMenuAction("Open file", Icons.Outlined.Description, { gateway.dispatch(openFileIntent(workTreePath, change)) }).takeUnless { change.status == GitStatusCodes.DELETED },
                    stageAction(change.staged) { gateway.dispatch(stageIntent(change.staged, change.path)) },
                    IcodeMenuAction(discardLabel(change), Icons.Outlined.DeleteOutline, { discard(change.path) }, destructive = true).takeIf { change.discardable },
                ),
                onClose = onClose,
                modifier = modifier,
                detail = change.path,
                leading = { SheetIcon(change.name, folder = false, change.path) },
            )
        }
    }
}

@Composable
private fun ChangeFolderRow(folder: GitChangeRow.Folder, staged: Boolean, onToggle: () -> Unit, onToggleStage: () -> Unit, onHold: () -> Unit, staging: Boolean) {
    IcodeListRow(
        rowShape = RectangleShape,
        compact = true,
        indent = IcodeSpacing.IndentStep * folder.depth,
        onClick = onToggle,
        onLongClick = onHold,
        leading = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Icon(
                    if (folder.expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = if (folder.expanded) "Collapse ${folder.name}" else "Expand ${folder.name}",
                    tint = IcodeColors.TextSecondary,
                    modifier = Modifier.size(IcodeSizes.IconSm),
                )
                // A folder Source Control joined with its one child (`src/main`) is named by its last.
                FileTypeIcon(folder.name.substringAfterLast('/'), folder = true, open = folder.expanded, parent = parentName(folder.path))
            }
        },
        content = { Text(folder.name, Modifier.weight(1f), style = IcodeType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailing = {
            MonoLine(folder.changes.toString(), color = IcodeColors.TextMuted, style = IcodeType.MonoSmall)
            StageButton(staged, folder.name, onToggleStage, staging)
        },
    )
}

@Composable
private fun ChangeFileRow(file: GitChangeRow.File, onOpen: () -> Unit, onToggleStage: () -> Unit, onHold: () -> Unit, staging: Boolean) {
    val change = file.change
    val tone = gitStatusTone(change.status).tint()
    val deleted = change.status == GitStatusCodes.DELETED
    IcodeListRow(
        rowShape = RectangleShape,
        compact = true,
        indent = IcodeSpacing.IndentStep * file.depth,
        onClick = onOpen,
        onLongClick = onHold,
        leading = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Spacer(Modifier.width(IcodeSizes.IconSm))
                FileTypeIcon(change.name, folder = false, parent = parentName(change.path))
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
            StageButton(change.staged, change.name, onToggleStage, staging)
        },
    )
}

private fun stageAction(staged: Boolean, onClick: () -> Unit) =
    IcodeMenuAction(if (staged) "Unstage" else "Stage", if (staged) Icons.Outlined.Remove else Icons.Outlined.Add, onClick)

/** + or −: [path] — a file, or all a folder holds — staged, or unstaged when it is [staged]. */
private fun stageIntent(staged: Boolean, path: String): ICodeIntent = if (staged) ICodeIntent.UnstageFile(path) else ICodeIntent.StageFile(path)

private fun openFileIntent(workTreePath: String, change: GitChange) = ICodeIntent.OpenFile("$workTreePath/${change.path}")

/** What discarding does to this change, as its menu says it. */
private fun discardLabel(change: GitChange): String = when (change.status) {
    GitStatusCodes.UNTRACKED -> "Delete file"
    GitStatusCodes.DELETED -> "Restore file"
    else -> "Discard changes"
}

/** + stages, − unstages: a file, or all a folder holds. */
@Composable
private fun StageButton(staged: Boolean, name: String, onClick: () -> Unit, busy: Boolean) {
    IcodeIconButton(
        if (staged) Icons.Outlined.Remove else Icons.Outlined.Add,
        contentDescription = if (staged) "Unstage $name" else "Stage $name",
        onClick = onClick,
        busy = busy,
        tint = IcodeColors.Accent,
        iconSize = IcodeSizes.IconMd,
    )
}


/**
 * Selecting a change opens the surface that explains it: the conflict workflow,
 * the confirmation that restores a deleted file, the file itself when it is new
 * to Git — there is nothing to compare it with, so VS Code opens it too — or the
 * diff. Staging stays on the trailing action so a review tap can never stage by
 * accident. The diff is requested before navigating so the diff surface renders
 * Git's own patch.
 */
private fun openChange(gateway: ICodeGateway, actions: NavigationActions, change: GitChange, workTreePath: String) {
    when {
        change.status == GitStatusCodes.UNTRACKED -> gateway.dispatch(openFileIntent(workTreePath, change))
        change.status == GitStatusCodes.CONFLICTED -> {
            gateway.dispatch(ICodeIntent.OpenConflict(change.path))
            actions.navigate(Route.ConflictResolution)
        }
        // Opening a deletion asks whether to restore the file.
        change.status == GitStatusCodes.DELETED && change.discardable -> gateway.dispatch(ICodeIntent.RequestDiscardChanges(change.path))
        else -> {
            gateway.dispatch(ICodeIntent.OpenDiff(change.path, change.staged))
            actions.navigate(Route.DiffView)
        }
    }
}
