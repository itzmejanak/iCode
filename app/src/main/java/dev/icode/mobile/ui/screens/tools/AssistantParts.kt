package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LocalCodeTextScale
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.scaledBy
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.markdown.MarkdownPlace
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.AnswererName
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.copyText
import dev.icode.mobile.ui.markdown.MarkdownAnswer
import java.io.File

/**
 * One row of the chat — what the user wrote, who answers, an answer, a run of steps, the reasoning, a compaction —
 * at the pinched size. An answer that ends its turn ([copyable]) can be copied, and played: the chat
 * is read, not heard, so an answer there is read aloud only from its ▶. The turn's last one
 * may still be [streaming]. A row with more behind it [onOpen]s it in a sheet
 * (docs/assistant-and-mcp.md); a long press on a message, or on a step about a file,
 * [onHold]s it, for its actions' sheet ([AssistantActionsSheet]).
 */
@Composable
internal fun AssistantEntryRow(entry: AssistantEntry, copyable: Boolean, streaming: Boolean, onOpen: () -> Unit, onHold: () -> Unit) {
    when (entry) {
        is AssistantEntry.Said -> SaidRow(entry, onHold)
        is AssistantEntry.Answer -> Column {
            // A reply of steps alone has only what it cost.
            if (entry.text.isNotBlank()) MarkdownAnswer(entry.text, streaming, MarkdownPlace.ofAnswer(LocalICodeSnapshot.current.assistant.workspace))
            if (copyable && entry.text.isNotBlank()) {
                Row {
                    CopyAnswer(entry.text)
                    PlayAnswer(entry.key, entry.text)
                }
            }
            entry.cost?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
        }
        is AssistantEntry.Answerer -> AnswererName(entry.by)
        is AssistantEntry.Reasoning -> AssistantReasoningRow(entry, onOpen)
        is AssistantEntry.Steps -> AssistantStepsRow(entry, onOpen, onHold)
        is AssistantEntry.Step -> AssistantStepsRow(AssistantEntry.Steps(entry.key, listOf(entry)), onOpen, onHold)
        is AssistantEntry.Compacted -> Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Divider()
            AssistantOpenRow(
                icon = Icons.Outlined.Compress,
                tint = IcodeColors.TextMuted,
                label = "Chat compacted — the model goes on from this summary",
                status = null,
                onOpen = onOpen,
            )
            Divider()
        }
    }
}

/**
 * What the user wrote, at the end. Its files ride a tab rising from the card's top end (the owner's
 * design, 2026-11-04), small, so the words lead: a photo as itself, another file as its kind, each
 * named for TalkBack; a tap shows a photo as it was sent and opens a file of the workspace as it is
 * now. Files sent alone stay in the card. A long press, on one with words, opens its sheet.
 */
@Composable
private fun SaidRow(entry: AssistantEntry.Said, onHold: () -> Unit) {
    val scale = LocalCodeTextScale.current
    val written = entry.text.isNotBlank()
    val files = entry.attachments
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        IcodeCard(
            Modifier.fillMaxWidth(SAID_WIDTH),
            elevated = true,
            onLongClick = onHold.takeIf { written },
            onLongClickLabel = "Options for this message",
            tab = if (written && files.isNotEmpty()) ({ SaidFiles(files) }) else null,
        ) {
            if (written) {
                Text(entry.text, style = IcodeType.Body.scaledBy(scale))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) { SaidFiles(files) }
            }
        }
    }
}

/** A message's files, each a small tile: a photo as itself, another file as its kind. */
@Composable
private fun SaidFiles(files: List<AssistantAttachment>) {
    val workspace = LocalICodeSnapshot.current.assistant.workspace
    files.forEach { file ->
        AssistantThumbnail(
            file.file.orEmpty(),
            file.kind,
            Modifier.size(IcodeSizes.TabTile).semantics { contentDescription = file.name }.tappable(openerOf(file, workspace)),
        )
    }
}

/** [attachmentOpener] for a file sent from [workspace], the chat's; a file of it is named by its path there. */
@Composable
private fun openerOf(file: AssistantAttachment, workspace: String?): (() -> Unit)? =
    attachmentOpener(file.name, file.kind, file.file, workspace?.takeIf { file.inWorkspace }?.let { File(it, file.name).path })

/** Copy, under an answer: its Markdown as written. */
@Composable
private fun CopyAnswer(text: String) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    AnswerAction(Icons.Outlined.ContentCopy, "Copy", "Copy the answer") {
        copyText(context, "Answer", text, "Answer copied", gateway::dispatch)
    }
}

/** ▶ after Copy: the answer read aloud, as the bubble's are; ■ stops it while it is read. */
@Composable
private fun PlayAnswer(key: String, text: String) {
    val gateway = LocalICodeGateway.current
    val reading = LocalICodeSnapshot.current.voice.reading == key
    if (reading) {
        AnswerAction(Icons.Outlined.Stop, "Stop", "Stop reading the answer") { gateway.dispatch(VoiceIntent.Silence) }
    } else {
        AnswerAction(Icons.Outlined.PlayArrow, "Play", "Read the answer aloud") { gateway.dispatch(VoiceIntent.Read(key, text)) }
    }
}

/** One of an answer's actions: its icon and word, small and muted, a touch target to tap. */
@Composable
private fun AnswerAction(icon: ImageVector, label: String, clickLabel: String, onClick: () -> Unit) {
    Row(
        Modifier.minimumInteractiveComponentSize().clip(IcodeShapes.Small).clickable(onClickLabel = clickLabel, onClick = onClick)
            .padding(horizontal = IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        Icon(icon, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
        Text(label, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
    }
}

/** No provider yet: what the assistant does, what it needs, and that what it reads is sent. */
@Composable
internal fun AssistantSetUp(modifier: Modifier, onSetUp: () -> Unit) {
    IcodePageColumn(modifier) {
        EmptyState(
            title = "Set up the assistant",
            description = "It reads and changes this workspace's files and runs commands in its runtime, " +
                "asking before each change. It needs a model provider: OpenAI, Anthropic, Google, OpenRouter, " +
                "Ollama, or any OpenAI-compatible server.",
            mark = { IcodeBrandMark(Modifier.size(IcodeSizes.MarkLg)) },
        )
        IcodeSection {
            Text(
                "What it reads — files, search results, command output — is sent to the provider you choose.",
                style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
            )
            PrimaryButton("Set Up Assistant", onSetUp, Modifier.fillMaxWidth())
        }
    }
}

/** A user's message takes most of the width, at the end, as chats show it. */
private const val SAID_WIDTH = 0.85f


