package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
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
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.copyText
import dev.icode.mobile.domain.markdown.MarkdownPlace
import dev.icode.mobile.ui.markdown.MarkdownAnswer
import java.io.File

/**
 * One row of the chat — what the user wrote, an answer, a run of steps, the reasoning, a compaction —
 * at the pinched size. An answer that ends its turn ([copyable]) can be copied; the turn's last one
 * may still be [streaming]. A row with more behind it [onOpen]s it in a sheet
 * (docs/assistant-sheets-plan.md §2); a message offers Copy and Select text on a long press (§2.4).
 */
@Composable
internal fun AssistantEntryRow(entry: AssistantEntry, copyable: Boolean, streaming: Boolean, onOpen: () -> Unit, onDiff: (GitChange) -> Unit) {
    when (entry) {
        is AssistantEntry.Said -> SaidRow(entry, onOpen)
        is AssistantEntry.Answer -> Column {
            MarkdownAnswer(entry.text, streaming, MarkdownPlace.ofAnswer(LocalICodeSnapshot.current.assistant.workspace))
            if (copyable) CopyAnswer(entry.text)
        }
        is AssistantEntry.Reasoning -> AssistantReasoningRow(entry, onOpen)
        is AssistantEntry.Steps -> AssistantStepsRow(entry, onOpen, onDiff)
        is AssistantEntry.Step -> AssistantStepsRow(AssistantEntry.Steps(entry.key, listOf(entry)), onOpen, onDiff)
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

/** What the user wrote, at the end: a long press offers Copy, and Select text in a sheet. */
@Composable
private fun SaidRow(entry: AssistantEntry.Said, onSelect: () -> Unit) {
    val scale = LocalCodeTextScale.current
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    var menu by remember { mutableStateOf(false) }
    val written = entry.text.isNotBlank()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(Modifier.fillMaxWidth(SAID_WIDTH)) {
            IcodeCard(
                Modifier.fillMaxWidth(),
                elevated = true,
                onLongClick = if (written) ({ menu = true }) else null,
                onLongClickLabel = "Options for this message",
            ) {
                SaidFiles(entry.attachments)
                if (written) Text(entry.text, style = IcodeType.Body.scaledBy(scale))
            }
            IcodeActionMenu(
                expanded = menu,
                onDismiss = { menu = false },
                actions = listOf(
                    IcodeMenuAction("Copy", Icons.Outlined.ContentCopy, { copyText(context, "Message", entry.text, "Message copied", gateway::dispatch) }),
                    IcodeMenuAction("Select text", Icons.Outlined.SelectAll, onSelect),
                ),
            )
        }
    }
}

/**
 * What went with a message: its photos drawn small, its other files by name and kind. A tap shows a
 * photo as it was sent, and opens a file of the workspace as it is now.
 */
@Composable
private fun SaidFiles(files: List<AssistantAttachment>) {
    val scale = LocalCodeTextScale.current
    val workspace = LocalICodeSnapshot.current.assistant.workspace
    val photos = files.filter { it.kind == AssistantAttachmentKind.Image }.mapNotNull { photo -> photo.file?.let { photo to it } }
    if (photos.isNotEmpty()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(bottom = IcodeSpacing.Space2),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        ) {
            photos.forEach { (photo, file) ->
                val open = openerOf(photo, workspace)
                AssistantThumbnail(file, AssistantAttachmentKind.Image, Modifier.size(IcodeSizes.Tile).semantics { contentDescription = photo.name }.tappable(open))
            }
        }
    }
    files.filter { it.kind != AssistantAttachmentKind.Image || it.file == null }.forEach { file ->
        Row(
            Modifier.padding(bottom = IcodeSpacing.Space1).tappable(openerOf(file, workspace)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        ) {
            Icon(file.kind.icon, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
            Text(file.name, style = IcodeType.MonoSmall.scaledBy(scale), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
    Row(
        Modifier.minimumInteractiveComponentSize().clip(IcodeShapes.Small).clickable(onClickLabel = "Copy the answer") {
            copyText(context, "Answer", text, "Answer copied", gateway::dispatch)
        }.padding(horizontal = IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
        Text("Copy", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
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


