package dev.icode.mobile.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeSaveDiscardDialog
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.screens.recovery.ExternalChangePrompt

/**
 * What the backend asks the user to decide: switching away from unsaved files,
 * a draft left from the last session, saving a recovered draft over a newer
 * file, a file changed on disk under unsaved edits. Each arrives as an effect,
 * waits here until it is shown, and is answered with an intent.
 */
@Stable
internal class WorkbenchQuestions {
    var workspaceSwitch by mutableStateOf<WorkbenchEffect.ConfirmWorkspaceSwitch?>(null)
    /** One question at a time: restoring several drafts asks about each in turn. */
    var draftRecoveries by mutableStateOf<List<WorkbenchEffect.ConfirmDraftRecovery>>(emptyList())
    var recoveredOverwrite by mutableStateOf<WorkbenchEffect.ConfirmRecoveredOverwrite?>(null)
    var externalChange by mutableStateOf<String?>(null)

    fun ask(effect: WorkbenchEffect.ConfirmWorkspaceSwitch) { workspaceSwitch = effect }

    fun ask(effect: WorkbenchEffect.ConfirmDraftRecovery) {
        draftRecoveries = draftRecoveries.filterNot { it.location == effect.location } + effect
    }

    fun ask(effect: WorkbenchEffect.ConfirmRecoveredOverwrite) { recoveredOverwrite = effect }

    fun ask(effect: WorkbenchEffect.ExternalChangeDetected) { externalChange = effect.location }
}

@Composable
internal fun rememberWorkbenchQuestions(): WorkbenchQuestions = remember { WorkbenchQuestions() }

/** The question waiting, drawn over the current screen. */
@Composable
internal fun WorkbenchQuestionDialogs(
    questions: WorkbenchQuestions,
    snapshot: WorkbenchSnapshot,
    gateway: WorkbenchGateway,
    current: Route,
    navigate: (Route) -> Unit,
) {
    questions.workspaceSwitch?.let { switch ->
        IcodeSaveDiscardDialog(
            title = "Switch workspace?",
            description = "${countOf(switch.dirtyCount, "file has", "files have")} unsaved changes.",
            onSave = {
                gateway.dispatch(WorkbenchIntent.ResolveWorkspaceSwitch(switch.location, switch.openTerminal, saveChanges = true))
                questions.workspaceSwitch = null
            },
            onDiscard = {
                gateway.dispatch(WorkbenchIntent.ResolveWorkspaceSwitch(switch.location, switch.openTerminal, saveChanges = false))
                questions.workspaceSwitch = null
            },
            onDismiss = { questions.workspaceSwitch = null },
        )
    }
    questions.draftRecoveries.firstOrNull()?.let { recovery ->
        val answered = { questions.draftRecoveries = questions.draftRecoveries.drop(1) }
        IcodeConfirmDialog(
            title = "Recover ${recovery.name}?",
            confirmLabel = "Restore draft",
            onConfirm = {
                gateway.dispatch(WorkbenchIntent.RestoreDraft(recovery.location))
                answered()
            },
            onDismiss = answered,
        ) {
            Text(
                if (recovery.sourceChanged)
                    "The file changed outside iCode. Restoring the draft will keep it in the editor, but saving over the newer file needs your confirmation."
                else "Unsaved text from the last session is available. Restore it into the editor or keep the version on disk.",
                style = IcodeType.Body,
            )
            SecondaryButton(
                "Keep disk version · discard draft",
                onClick = {
                    gateway.dispatch(WorkbenchIntent.DiscardDraft(recovery.location))
                    answered()
                },
                modifier = Modifier.fillMaxWidth(),
                color = IcodeColors.Error,
            )
        }
    }
    ExternalChangePrompt(
        asked = questions.externalChange,
        onAskedChange = { questions.externalChange = it },
        snapshot = snapshot,
        gateway = gateway,
        current = current,
        navigate = navigate,
    )
    questions.recoveredOverwrite?.let { overwrite ->
        IcodeConfirmDialog(
            title = "Overwrite newer file?",
            confirmLabel = "Overwrite file",
            destructive = true,
            onConfirm = {
                gateway.dispatch(WorkbenchIntent.ConfirmRecoveredOverwrite(overwrite.location))
                questions.recoveredOverwrite = null
            },
            onDismiss = { questions.recoveredOverwrite = null },
        ) {
            Text(
                "${overwrite.name} changed outside iCode since the recovery draft was made. This saves the recovered text over the current disk version. Cancel to keep both unchanged.",
                style = IcodeType.Body,
            )
        }
    }
}
