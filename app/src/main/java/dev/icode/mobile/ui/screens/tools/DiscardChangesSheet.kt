package dev.icode.mobile.ui.screens.tools

import androidx.compose.runtime.Composable
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitDiscard
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Confirms a discard — one file's unstaged change, or all a folder holds — in the app's one
 * confirmation sheet, over whatever asked for it. An edit returns to its staged or committed
 * version and a new file is deleted: neither comes back, so the path and what happens to each
 * kind are said before the destructive button.
 */
@Composable
internal fun DiscardChangesSheet(discard: GitDiscard, gateway: ICodeGateway) {
    val single = if (discard.folder) null else discard.changes.single()
    val (title, confirm) = when {
        single == null -> "Discard ${countOf(discard.changes.size, "change")} in ${discard.name}?" to "Discard"
        single.status == GitStatusCodes.UNTRACKED -> "Delete ${discard.name}?" to "Delete"
        single.status == GitStatusCodes.DELETED -> "Restore ${discard.name}?" to "Restore"
        else -> "Discard changes to ${discard.name}?" to "Discard"
    }
    IcodeConfirmSheet(
        title = title,
        message = if (single == null) folderConsequence(discard.changes) else consequence(single),
        confirmLabel = confirm,
        onConfirm = { gateway.dispatch(ICodeIntent.ConfirmDiscardChanges) },
        onDismiss = { gateway.dispatch(ICodeIntent.CancelDiscardChanges) },
        detail = if (discard.folder) "${discard.path}/" else discard.path,
        // The backend holds the question until the discard is done.
        confirmBusy = LocalICodeSnapshot.current.isBusy(BusyAction.Discard),
    )
}

/** Says exactly what Git will do, because each status discards differently. */
private fun consequence(change: GitChange): String = when {
    // Git reports a folder whole — another repository inside this one — with its trailing slash.
    change.status == GitStatusCodes.UNTRACKED && change.path.endsWith('/') ->
        "This folder is not tracked by Git, so there is no copy to restore it from. It is deleted with everything in it that Git does not ignore."
    change.status == GitStatusCodes.UNTRACKED ->
        "This file is not tracked by Git, so there is no copy to restore it from. It is deleted."
    change.status == GitStatusCodes.DELETED ->
        "The file is restored from its staged or last committed version, undoing the deletion."
    else ->
        "The file returns to its staged or last committed version. Edits you have not staged cannot be recovered."
}

/** A folder's: what happens to each kind it holds, and that none of it comes back. */
private fun folderConsequence(changes: List<GitChange>): String {
    val new = changes.count { it.status == GitStatusCodes.UNTRACKED }
    val deleted = changes.count { it.status == GitStatusCodes.DELETED }
    val edited = changes.size - new - deleted
    return listOfNotNull(
        "New files are deleted, as Git has no copy of them: ${countOf(new, "file")}.".takeIf { new > 0 },
        "Edited files return to their staged or last committed version: ${countOf(edited, "file")}.".takeIf { edited > 0 },
        "Deleted files are restored: ${countOf(deleted, "file")}.".takeIf { deleted > 0 },
        "This cannot be undone.",
    ).joinToString(" ")
}
