package dev.icode.mobile.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeCheckbox
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.files.ExplorerState
import dev.icode.mobile.domain.files.FileConflict
import dev.icode.mobile.domain.files.PasswordAsk

/**
 * What an explorer's operation asks, over whatever screen is open, each a sheet on the one base
 * (docs/explorer-gaps-plan.md §3.6, §3.11): a name already there, which its task waits on, or a 7z's
 * password. The task's own dialog steps aside while the question is open ([TaskDialogs]).
 */
@Composable
internal fun FileAsks(explorer: ExplorerState, gateway: ICodeGateway) {
    val question = explorer.question
    val password = explorer.password
    when {
        question != null -> IcodeSheetOverlay(onClose = { gateway.dispatch(FilesIntent.Answer(question.id, null)) }) {
            QuestionSheet(question, gateway, Modifier.align(Alignment.BottomCenter))
        }
        password != null -> IcodeSheetOverlay(onClose = { gateway.dispatch(FilesIntent.DismissPassword) }) {
            PasswordSheet(password, gateway, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * A name already there (§3.6): the two side by side, and the answers — Replace, putting the one there
 * in Trash, or Merge two folders; Keep both; Skip — the same for the rest when ticked. ✕ stops the
 * operation, which keeps what it did, one step to undo.
 */
@Composable
private fun QuestionSheet(question: FileConflict, gateway: ICodeGateway, modifier: Modifier) {
    var forAll by rememberSaveable(question.id) { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    IcodeBottomSheet(
        title = "“${question.name}” is already there",
        onClose = { gateway.dispatch(FilesIntent.Answer(question.id, null)) },
        modifier = modifier,
        subtitle = "In ${question.into}",
        footer = {
            question.forOthers?.let { label ->
                IcodeListRow(title = label, onClick = { forAll = !forAll }, leading = { IcodeCheckbox(forAll, onCheckedChange = null) })
            }
            question.answers.forEachIndexed { index, answer ->
                val answered = { gateway.dispatch(FilesIntent.Answer(question.id, answer, forAll)) }
                if (index == 0) PrimaryButton(answer.label, answered, Modifier.fillMaxWidth())
                else SecondaryButton(answer.label, answered, Modifier.fillMaxWidth())
            }
        },
    ) {
        DetailRow("Coming", question.coming.said(now))
        DetailRow("There", question.there.said(now))
    }
}

/** A 7z locked by a password (§3.11): asked here, sent with Extract and never kept — not even in saved state. */
@Composable
private fun PasswordSheet(ask: PasswordAsk, gateway: ICodeGateway, modifier: Modifier) {
    var password by remember(ask) { mutableStateOf("") }
    IcodeBottomSheet(
        title = "${ask.archive.substringAfterLast('/')} is locked",
        onClose = { gateway.dispatch(FilesIntent.DismissPassword) },
        modifier = modifier,
        subtitle = "Its password opens it",
        footer = {
            PrimaryButton("Extract", { gateway.dispatch(FilesIntent.Extract(ask.archive, ask.folder, password)) }, Modifier.fillMaxWidth(), enabled = password.isNotEmpty())
        },
    ) {
        LabeledTextField("Password", password, { password = it }, secret = true)
    }
}
