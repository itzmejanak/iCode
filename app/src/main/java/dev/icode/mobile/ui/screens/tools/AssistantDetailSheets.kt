package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.rememberSheetPages
import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.AssistantStepRules
import dev.icode.mobile.domain.AssistantStepStatus
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantToolName
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.word
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.copyText

/**
 * What a row of the chat opens (docs/assistant-sheets-plan.md §2.2–§2.4): a run of steps, the
 * reasoning, a compaction's summary, or a message to select from. [entry] is the chat's as it is
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
        is AssistantEntry.Said -> AssistantTextSheet("Select text", null, entry.text, modifier, onClose)
        // An answer is selectable where it is, and copied under its turn.
        is AssistantEntry.Answer -> Unit
    }
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
    IcodeBottomSheet(
        title = AssistantToolName.of(step.call.tool)?.title ?: step.call.tool,
        onClose = onClose,
        modifier = modifier,
        subtitle = step.status.word,
        onBack = onBack,
        footer = approval?.let { asked -> { ApprovalAnswers(asked, workspace, Modifier.fillMaxWidth()) } },
    ) {
        Text(step.call.summary, style = IcodeType.Body)
        command?.let { StepText("Command", it) }
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
        when {
            approval != null -> {
                // A command's whole line is its Command already.
                approval.detail.takeIf { it != command }?.let { StepText("What it will do", it) }
                approval.reach?.let { reach ->
                    StepSection("Reaches") { Text(reach.paths.joinToString("\n"), style = IcodeType.MonoSmall) }
                }
                allowInMeaning(approval.reach, workspace)?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
            }
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

/** A step's [text] under its [label], all of it, selectable, with Copy at its corner. */
@Composable
private fun StepText(label: String, text: String) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    StepSection(label, trailing = { CopyChip { copyText(context, label, text, "$label copied", gateway::dispatch) } }) {
        CodeSurface(Modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space2)) {
            SelectionContainer { Text(text, style = IcodeType.MonoSmall.copy(color = IcodeColors.TextPrimary)) }
        }
    }
}

/** A part of a step's page: its name in small capitals, [trailing] at its end, and what it holds. */
@Composable
private fun StepSection(label: String, trailing: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = IcodeSpacing.Space2), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            SectionLabel(label, Modifier.weight(1f))
            trailing()
        }
        content()
    }
}

/**
 * Text to read and take from (§2.3, §2.4): the reasoning, a compaction's summary, a message. Android's
 * own handles and toolbar select and copy any part of it; Copy All takes it whole.
 */
@Composable
internal fun AssistantTextSheet(title: String, subtitle: String?, text: String, modifier: Modifier, onClose: () -> Unit) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    IcodeBottomSheet(
        title = title,
        onClose = onClose,
        modifier = modifier,
        subtitle = subtitle,
        footer = { SecondaryButton("Copy All", { copyText(context, title, text, "Copied", gateway::dispatch) }, Modifier.fillMaxWidth()) },
    ) {
        SelectionContainer { Text(text, style = IcodeType.Body) }
    }
}
