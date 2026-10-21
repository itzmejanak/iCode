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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.copyText
import dev.icode.mobile.ui.editor.MarkdownText
import java.io.File

/**
 * One row of the chat — what the user wrote, an answer, a step, a compaction — at the pinched size.
 * An answer that ends its turn ([copyable]) can be copied.
 */
@Composable
internal fun AssistantEntryRow(entry: AssistantEntry, copyable: Boolean, onDiff: (GitChange) -> Unit) {
    val scale = LocalCodeTextScale.current
    when (entry) {
        is AssistantEntry.Said -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            IcodeCard(Modifier.fillMaxWidth(SAID_WIDTH), elevated = true) {
                SaidFiles(entry.attachments)
                if (entry.text.isNotBlank()) Text(entry.text, style = IcodeType.Body.scaledBy(scale))
            }
        }
        is AssistantEntry.Answer -> Column {
            MarkdownText(entry.text, IcodeType.Body.fontSize.value * scale)
            if (copyable) CopyAnswer(entry.text)
        }
        is AssistantEntry.Reasoning -> AssistantReasoningRow(entry)
        is AssistantEntry.Step -> AssistantStepRow(entry, onDiff)
        is AssistantEntry.Compacted -> Disclosure(
            key = entry.key,
            icon = Icons.Outlined.Compress,
            tint = IcodeColors.TextMuted,
            label = "Chat compacted — the model goes on from this summary",
            detail = entry.summary,
            divided = true,
        )
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


