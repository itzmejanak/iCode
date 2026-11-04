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
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeSaveDiscardSheet
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ICodeSnapshot
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
internal class ICodeQuestions {
    var workspaceSwitch by mutableStateOf<ICodeEffect.ConfirmWorkspaceSwitch?>(null)
    /** One question at a time: restoring several drafts asks about each in turn. */
    var draftRecoveries by mutableStateOf<List<ICodeEffect.ConfirmDraftRecovery>>(emptyList())
    var recoveredOverwrite by mutableStateOf<ICodeEffect.ConfirmRecoveredOverwrite?>(null)
    var externalChange by mutableStateOf<String?>(null)

    fun ask(effect: ICodeEffect.ConfirmWorkspaceSwitch) { workspaceSwitch = effect }

    fun ask(effect: ICodeEffect.ConfirmDraftRecovery) {
        draftRecoveries = draftRecoveries.filterNot { it.location == effect.location } + effect
    }

    fun ask(effect: ICodeEffect.ConfirmRecoveredOverwrite) { recoveredOverwrite = effect }

    fun ask(effect: ICodeEffect.ExternalChangeDetected) { externalChange = effect.location }
}

@Composable
internal fun rememberICodeQuestions(): ICodeQuestions = remember { ICodeQuestions() }

/** The question waiting, drawn over the current screen. */
@Composable
internal fun ICodeQuestionSheets(
    questions: ICodeQuestions,
    snapshot: ICodeSnapshot,
    gateway: ICodeGateway,
    current: Route,
    navigate: (Route) -> Unit,
) {
    questions.workspaceSwitch?.let { switch ->
        IcodeSaveDiscardSheet(
            title = "Switch workspace?",
            description = "${countOf(switch.dirtyCount, "file has", "files have")} unsaved changes.",
            onSave = {
                gateway.dispatch(ICodeIntent.ResolveWorkspaceSwitch(switch.location, switch.openTerminal, saveChanges = true))
                questions.workspaceSwitch = null
            },
            onDiscard = {
                gateway.dispatch(ICodeIntent.ResolveWorkspaceSwitch(switch.location, switch.openTerminal, saveChanges = false))
                questions.workspaceSwitch = null
            },
            onDismiss = { questions.workspaceSwitch = null },
        )
    }
    questions.draftRecoveries.firstOrNull()?.let { recovery ->
        val answered = { questions.draftRecoveries = questions.draftRecoveries.drop(1) }
        IcodeConfirmSheet(
            title = "Recover ${recovery.name}?",
            confirmLabel = "Restore draft",
            onConfirm = {
                gateway.dispatch(ICodeIntent.RestoreDraft(recovery.location))
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
                    gateway.dispatch(ICodeIntent.DiscardDraft(recovery.location))
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
        IcodeConfirmSheet(
            title = "Overwrite newer file?",
            confirmLabel = "Overwrite file",
            destructive = true,
            onConfirm = {
                gateway.dispatch(ICodeIntent.ConfirmRecoveredOverwrite(overwrite.location))
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
