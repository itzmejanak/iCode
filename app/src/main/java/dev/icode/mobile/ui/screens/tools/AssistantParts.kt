package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.ThreeColumnActions
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.AssistantStepStatus
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.ui.editor.MarkdownText

/** One row of the chat: what the user wrote, an answer, or a step with what came of it. */
@Composable
internal fun AssistantEntryRow(entry: AssistantEntry) {
    when (entry) {
        is AssistantEntry.Said -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            IcodeCard(Modifier.fillMaxWidth(SAID_WIDTH), elevated = true) {
                Text(entry.text, style = IcodeType.Body)
            }
        }
        is AssistantEntry.Answer -> MarkdownText(entry.text, IcodeType.Body.fontSize.value)
        is AssistantEntry.Step -> AssistantStepRow(entry)
    }
}

/** A step: what it did, how it went, and its output a tap away. */
@Composable
private fun AssistantStepRow(step: AssistantEntry.Step) {
    var open by rememberSaveable(step.key) { mutableStateOf(false) }
    val output = step.output
    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = output != null) { open = !open }.padding(vertical = IcodeSpacing.Space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            val (icon, tint, said) = when (step.status) {
                AssistantStepStatus.Running -> Triple(Icons.Outlined.Sync, IcodeColors.Accent, "Running")
                AssistantStepStatus.Queued -> Triple(Icons.Outlined.Schedule, IcodeColors.TextMuted, "Next")
                AssistantStepStatus.Asking -> Triple(Icons.AutoMirrored.Outlined.HelpOutline, IcodeColors.Amber, "Waiting for your answer")
                AssistantStepStatus.Done -> Triple(Icons.Outlined.Check, IcodeColors.Green, "Done")
                AssistantStepStatus.Failed -> Triple(Icons.Outlined.ErrorOutline, IcodeColors.Error, "Failed")
            }
            Icon(icon, contentDescription = said, tint = tint, modifier = Modifier.size(IcodeSizes.IconSm))
            Text(
                step.call.summary,
                modifier = Modifier.weight(1f),
                style = (if (step.call.kind == AssistantToolKind.Command) IcodeType.MonoSmall else IcodeType.Caption)
                    .copy(color = IcodeColors.TextSecondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (output != null) {
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (open) "Hide output" else "Show output",
                    tint = IcodeColors.TextMuted,
                    modifier = Modifier.size(IcodeSizes.IconSm),
                )
            }
        }
        if (open && output != null) {
            CodeSurface(Modifier.fillMaxWidth()) {
                Text(output.take(SHOWN_OUTPUT), style = IcodeType.MonoSmall)
            }
        }
    }
}

/** A change or a command waiting for the user: what it will do, then Deny, Allow All or Allow. */
@Composable
internal fun AssistantApprovalCard(approval: AssistantApproval, modifier: Modifier, onAnswer: (AssistantAnswer) -> Unit) {
    val command = approval.call.kind == AssistantToolKind.Command
    NoticeCard(
        IcodeTone.Warning,
        modifier.fillMaxWidth(),
        title = if (command) "Run this command?" else "Make this change?",
        icon = if (command) Icons.Outlined.Terminal else Icons.Outlined.EditNote,
    ) {
        CodeSurface(Modifier.fillMaxWidth()) {
            Text(approval.detail, style = IcodeType.MonoSmall, maxLines = DETAIL_LINES, overflow = TextOverflow.Ellipsis)
        }
        Text("Allow All: this chat's changes and commands stop asking.", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
        ThreeColumnActions(
            first = { SecondaryButton("Deny", { onAnswer(AssistantAnswer.Deny) }, Modifier.fillMaxWidth()) },
            second = { SecondaryButton("Allow All", { onAnswer(AssistantAnswer.AllowAll) }, Modifier.fillMaxWidth()) },
            third = { PrimaryButton("Allow", { onAnswer(AssistantAnswer.Allow) }, Modifier.fillMaxWidth()) },
        )
    }
}

/** No provider yet: what the assistant does, what it needs, and that what it reads is sent. */
@Composable
internal fun AssistantSetUp(modifier: Modifier, onSetUp: () -> Unit) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space6),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            title = "Set up the assistant",
            description = "It reads and changes this workspace's files and runs commands in its runtime, " +
                "asking before each change. It needs a model provider: OpenAI, Anthropic, Google, OpenRouter, " +
                "Ollama, or any OpenAI-compatible server.",
            icon = Icons.Outlined.AutoAwesome,
        )
        Text(
            "What it reads — files, search results, command output — is sent to the provider you choose.",
            style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
        )
        PrimaryButton("Set Up Assistant", onSetUp, Modifier.fillMaxWidth())
    }
}

/** A chat with nothing in it yet: what to ask. */
@Composable
internal fun AssistantIntro(workspaceName: String) {
    EmptyState(
        title = "Ask about $workspaceName",
        description = "“Explain how the app starts”, “Fix the failing test”, “Add a README”. Changes and commands ask first.",
        icon = Icons.Outlined.AutoAwesome,
        modifier = Modifier.padding(top = IcodeSpacing.Space6),
    )
}

/** A user's message takes most of the width, at the end, as chats show it. */
private const val SAID_WIDTH = 0.85f

/** An output shown on the page: the model was given at most a few times this. */
private const val SHOWN_OUTPUT = 8_000

private const val DETAIL_LINES = 14
