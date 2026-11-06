package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeActionSheet
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.rememberSheetPages
import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantStepRules
import dev.icode.mobile.domain.AssistantStepStatus
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantToolName
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.word
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.SheetIcon
import dev.icode.mobile.ui.copyText
import dev.icode.mobile.ui.screens.workspace.itemIcon

/**
 * What a row of the chat opens (docs/assistant-sheets-plan.md §2.2–§2.3): a run of steps, the
 * reasoning, or a compaction's summary. [entry] is the chat's as it is
 * now, so what a step does while its sheet is open shows there. [step] opens a run at that step;
 * [approval] is the question waiting, which its step's page shows and answers; [workspace] is its name.
 */
@Composable
internal fun AssistantEntrySheet(
    entry: AssistantEntry,
    step: String?,
    approval: AssistantApproval?,
    workspace: String,
    modifier: Modifier,
    onClose: () -> Unit,
    onDiff: (GitChange) -> Unit,
) {
    when (entry) {
        is AssistantEntry.Steps -> AssistantStepsSheet(entry, step, approval, workspace, modifier, onClose, onDiff)
        is AssistantEntry.Step -> AssistantStepsSheet(AssistantEntry.Steps(entry.key, listOf(entry)), step, approval, workspace, modifier, onClose, onDiff)
        is AssistantEntry.Reasoning -> AssistantTextSheet("Reasoning", wordCount(entry.text), entry.text, modifier, onClose)
        is AssistantEntry.Compacted -> AssistantTextSheet("Chat compacted", "The model goes on from this summary", entry.summary, modifier, onClose)
        // A message opens its actions' sheet; an answer is selectable where it is, and copied under its turn.
        is AssistantEntry.Said, is AssistantEntry.Answer -> Unit
    }
}

/**
 * What a long press on a row of the chat offers, as a sheet (docs/explorer-gaps-plan.md §3.1): for
 * the user's message, Copy, Edit — its words back in the composer ([onEdit]) — Retry when it is the
 * last and nothing answered it, and Select text as its page (§2.4); for a step about a file, Open in
 * Editor, Show Changes where Git has some, and Copy path.
 */
@Composable
internal fun AssistantActionsSheet(
    entry: AssistantEntry,
    modifier: Modifier,
    onClose: () -> Unit,
    onDiff: (GitChange) -> Unit,
    onEdit: (AssistantEntry.Said) -> Unit,
) {
    when (entry) {
        is AssistantEntry.Said -> MessageSheet(entry, modifier, onClose, onEdit)
        is AssistantEntry.Steps -> entry.steps.singleOrNull()?.let { StepFileSheet(it, modifier, onClose, onDiff) }
        is AssistantEntry.Step -> StepFileSheet(entry, modifier, onClose, onDiff)
        is AssistantEntry.Answer, is AssistantEntry.Reasoning, is AssistantEntry.Compacted -> Unit
    }
}

/** A message's sheet, which it heads: Copy, Edit, Retry when its turn failed, and Select text, its one page. */
@Composable
private fun MessageSheet(entry: AssistantEntry.Said, modifier: Modifier, onClose: () -> Unit, onEdit: (AssistantEntry.Said) -> Unit) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    val failed = AssistantRules.unanswered(LocalICodeSnapshot.current.assistant) == entry.key
    val pages = rememberSheetPages<MessagePage>(entry.key)
    when (pages.current) {
        null -> IcodeActionSheet(
            title = entry.text,
            actions = listOfNotNull(
                IcodeMenuAction("Retry", Icons.Outlined.Refresh, { gateway.dispatch(ICodeIntent.RetryAssistantMessage) }).takeIf { failed },
                IcodeMenuAction("Edit", Icons.Outlined.Edit, { onEdit(entry) }),
                IcodeMenuAction("Copy", Icons.Outlined.ContentCopy, { copyText(context, "Message", entry.text, "Message copied", gateway::dispatch) }),
                IcodeMenuAction("Select text", Icons.Outlined.SelectAll, { pages.open(MessagePage.SelectText) }, opensPage = true),
            ),
            onClose = onClose,
            modifier = modifier,
            detail = wordCount(entry.text),
            leading = { SheetIcon(Icons.Outlined.ChatBubbleOutline, IcodeColors.Accent) },
        )
        MessagePage.SelectText -> AssistantTextSheet("Select text", null, entry.text, modifier, onClose, pages.back)
    }
}

private enum class MessagePage { SelectText }

/** A step's file's sheet, titled with its name and the step: what [fileActions] offers. */
@Composable
private fun StepFileSheet(step: AssistantEntry.Step, modifier: Modifier, onClose: () -> Unit, onDiff: (GitChange) -> Unit) {
    val file = fileActions(step, onDiff) ?: return
    val name = step.call.file.orEmpty().substringAfterLast('/')
    val (icon, tint) = itemIcon(name, folder = false)
    IcodeActionSheet(
        title = name,
        actions = file.all,
        onClose = onClose,
        modifier = modifier,
        detail = step.call.summary,
        leading = { SheetIcon(icon, tint) },
    )
}

/**
 * A run of steps (§2.2): each on a rail, a tap opening its page in the sheet, ← back. A run of one
 * opens at its step, as one opened from the question waiting does.
 */
@Composable
private fun AssistantStepsSheet(
    entry: AssistantEntry.Steps,
    start: String?,
    approval: AssistantApproval?,
    workspace: String,
    modifier: Modifier,
    onClose: () -> Unit,
    onDiff: (GitChange) -> Unit,
) {
    // A run of one is its step's page, with nothing to go back to.
    val pages = rememberSheetPages(entry.key, listOfNotNull(start.takeIf { entry.steps.size > 1 }))
    val step = entry.steps.singleOrNull() ?: entry.steps.firstOrNull { it.key == pages.current }
    if (step != null) {
        StepPage(step, approval?.takeIf { it.call.id == step.call.id }, workspace, modifier, onClose, pages.back, onDiff)
        return
    }
    IcodeBottomSheet(
        title = AssistantStepRules.summary(entry.steps),
        onClose = onClose,
        modifier = modifier,
        subtitle = "${entry.steps.size} steps · ${AssistantStepRules.status(entry.steps).word}",
    ) {
        // Rows touching, so the rail runs unbroken from the first to the last.
        Column {
            entry.steps.forEachIndexed { index, each ->
                val (icon, tint) = statusIcon(each.status)
                AssistantOpenRow(
                    icon = icon,
                    tint = tint,
                    label = each.call.summary,
                    status = each.status.word,
                    onOpen = { pages.open(each.key) },
                    mono = each.call.kind == AssistantToolKind.Command,
                    changes = AssistantStepRules.changes(listOf(each)),
                    rail = AssistantRail.of(index, entry.steps.size),
                )
            }
        }
    }
}

/**
 * One step (§2.2): titled by its tool, with how it went; what it was for; its command or its file;
 * then all it answered — or, while it waits on [approval], all it will do, what it reaches and the
 * answers, as the card's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepPage(
    step: AssistantEntry.Step,
    approval: AssistantApproval?,
    workspace: String,
    modifier: Modifier,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onDiff: (GitChange) -> Unit,
) {
    val command = step.call.command
    val file = fileActions(step, onDiff)
    // The tab its command runs in, while it runs (docs/agent-control-plan.md §6): typed into, it is the sheet.
    val live = LocalICodeSnapshot.current.assistant.turn?.terminal?.takeIf { it.callId == step.call.id && step.status == AssistantStepStatus.Running }
    var terminalFocused by remember(step.key) { mutableStateOf(false) }
    val typing = live != null && terminalFocused && WindowInsets.isImeVisible
    IcodeBottomSheet(
        title = AssistantToolName.of(step.call.tool)?.title ?: step.call.tool,
        onClose = onClose,
        modifier = modifier,
        subtitle = step.status.word,
        onBack = onBack,
        scroll = !typing,
        footer = approval?.let { asked -> { ApprovalAnswers(asked, workspace, Modifier.fillMaxWidth()) } },
    ) {
        if (!typing) {
            Text(step.call.summary, style = IcodeType.Body)
            command?.let { StepText("Command", it) }
        }
        if (file != null) {
            StepSection("File", trailing = {
                AssistantStepRules.changes(listOf(step))?.let { ChangedLines(it) }
                CopyChip(file.copyPath.onClick)
            }) {
                Text(step.call.file.orEmpty(), style = IcodeType.MonoSmall.copy(color = IcodeColors.TextPrimary))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    listOfNotNull(file.open, file.changes).forEach { SecondaryButton(it.label, it.onClick, compact = true) }
                }
            }
        }
        val output = step.output?.takeIf { it.isNotEmpty() }
        val printed = step.printed?.takeIf { it.isNotEmpty() }
        when {
            approval != null -> {
                // A command's whole line is its Command already.
                approval.detail.takeIf { it != command }?.let { StepText("What it will do", it) }
                approval.reach?.let { reach ->
                    StepSection("Reaches") { Text(reach.paths.joinToString("\n"), style = IcodeType.MonoSmall) }
                }
                allowInMeaning(approval, workspace)?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
            }
            live != null -> StepTerminal(
                live.session, command ?: step.call.summary, typing, onFocus = { terminalFocused = it }, onClose,
                if (typing) Modifier.weight(1f) else Modifier,
            )
            output != null && printed != null -> StepReplay(printed, output)
            output != null -> StepText("Output", output)
            else -> Hint(
                when (step.status) {
                    AssistantStepStatus.Running -> "Running…"
                    AssistantStepStatus.Queued, AssistantStepStatus.Asking -> "Not run yet"
                    AssistantStepStatus.Done, AssistantStepStatus.Failed -> "No output"
                },
            )
        }
    }
}

/**
 * Text to read and take from (§2.3, §2.4): the reasoning, a compaction's summary, a message. Android's
 * own handles and toolbar select and copy any part of it; Copy All takes it whole.
 */
@Composable
internal fun AssistantTextSheet(title: String, subtitle: String?, text: String, modifier: Modifier, onClose: () -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    IcodeBottomSheet(
        title = title,
        onClose = onClose,
        modifier = modifier,
        subtitle = subtitle,
        onBack = onBack,
        footer = { SecondaryButton("Copy All", { copyText(context, title, text, "Copied", gateway::dispatch) }, Modifier.fillMaxWidth()) },
    ) {
        SelectionContainer { Text(text, style = IcodeType.Body) }
    }
}
