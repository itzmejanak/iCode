package dev.icode.mobile.ui.screens.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.DialogScaffold
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/**
 * Confirms discarding one unstaged change.
 *
 * An edit returns to its staged or committed version and a new file or folder
 * is deleted — neither is recoverable, so the path, what will happen to it and
 * its irreversibility are all stated before the destructive button.
 */
@Composable
fun DeleteConfirmationScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val change = state.pendingDiscard
    val dismiss = {
        gateway.dispatch(WorkbenchIntent.CancelDiscardChanges)
        actions.back()
    }
    DialogScaffold(
        title = change?.name ?: "Discard changes",
        subtitle = state.activeWorkspace,
        onBack = dismiss,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4),
        ) {
            IconBadge(
                icon = Icons.Outlined.WarningAmber,
                tint = IcodeColors.Error,
                size = IcodeSizes.MarkMd,
                background = Color.Transparent,
                iconSize = IcodeSizes.MarkIconLg,
                shape = IcodeShapes.Pill,
            )
            Text(
                if (change == null) "Nothing to discard" else headline(change),
                style = IcodeType.PageTitle.copy(color = IcodeColors.Error),
                textAlign = TextAlign.Center,
            )
            if (change == null) {
                Text(
                    "This change is no longer in the working tree.",
                    style = IcodeType.Body,
                    color = IcodeColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                SecondaryButton("Close", dismiss, Modifier.fillMaxWidth())
                return@DialogScaffold
            }
            Text(
                "Are you sure you want to discard the changes in this file?",
                style = IcodeType.Body,
                textAlign = TextAlign.Center,
            )
            CodeSurface(
                modifier = Modifier.fillMaxWidth().clip(IcodeShapes.Small),
                padding = PaddingValues(IcodeSpacing.Space3),
            ) {
                MonoLine(change.path, color = IcodeColors.Accent)
            }
            Text(
                consequence(change),
                style = IcodeType.Body,
                color = IcodeColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            DangerButton(
                when {
                    change.status != GitStatusCodes.UNTRACKED -> "DISCARD CHANGES"
                    change.isFolder -> "DELETE FOLDER"
                    else -> "DELETE FILE"
                },
                {
                    gateway.dispatch(WorkbenchIntent.ConfirmDiscardChanges)
                    actions.back()
                },
                Modifier.fillMaxWidth(),
                filled = true,
            )
            SecondaryButton("Cancel", dismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Git reports an untracked folder with its trailing slash. */
private val GitChange.isFolder: Boolean get() = path.endsWith('/')

private fun headline(change: GitChange): String = when {
    change.status == GitStatusCodes.UNTRACKED && change.isFolder -> "Delete Folder"
    change.status == GitStatusCodes.UNTRACKED -> "Delete File"
    change.status == GitStatusCodes.DELETED -> "Restore File"
    else -> "Discard Changes"
}

/** Says exactly what Git will do, because each status discards differently. */
private fun consequence(change: GitChange): String = when {
    change.status == GitStatusCodes.UNTRACKED && change.isFolder ->
        "This folder is not tracked by Git, so there is no copy to restore it from. It will be deleted with everything in it that Git does not ignore."
    change.status == GitStatusCodes.UNTRACKED ->
        "This file is not tracked by Git, so there is no copy to restore it from. It will be deleted."
    change.status == GitStatusCodes.DELETED ->
        "The file will be restored from its staged or last committed version, undoing the deletion."
    else ->
        "The file returns to its staged or last committed version. Edits you have not staged cannot be recovered."
}
