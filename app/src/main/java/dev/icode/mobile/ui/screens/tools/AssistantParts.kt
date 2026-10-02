package dev.icode.mobile.ui.screens.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
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
import dev.icode.mobile.domain.AssistantStepStatus
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.ui.editor.MarkdownText

/**
 * One row of the chat — what the user wrote, an answer, a step, a compaction — at the pinched size.
 * An answer that ends its turn ([copyable]) can be copied.
 */
@Composable
internal fun AssistantEntryRow(entry: AssistantEntry, copyable: Boolean) {
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
        is AssistantEntry.Step -> AssistantStepRow(entry)
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

/** What went with a message: its photos drawn small, its other files by name and kind. */
@Composable
private fun SaidFiles(files: List<AssistantAttachment>) {
    val scale = LocalCodeTextScale.current
    val photos = files.filter { it.kind == AssistantAttachmentKind.Image }.mapNotNull { photo -> photo.file?.let { photo.name to it } }
    if (photos.isNotEmpty()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(bottom = IcodeSpacing.Space2),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        ) {
            photos.forEach { (name, file) ->
                AssistantThumbnail(file, AssistantAttachmentKind.Image, Modifier.size(IcodeSizes.Tile).semantics { contentDescription = name })
            }
        }
    }
    files.filter { it.kind != AssistantAttachmentKind.Image || it.file == null }.forEach { file ->
        Row(
            Modifier.padding(bottom = IcodeSpacing.Space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        ) {
            Icon(file.kind.icon, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
            Text(file.name, style = IcodeType.MonoSmall.scaledBy(scale), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Copy, under an answer: its Markdown as written. Android 13 and later show the copy themselves. */
@Composable
private fun CopyAnswer(text: String) {
    val context = LocalContext.current
    Row(
        Modifier.minimumInteractiveComponentSize().clip(IcodeShapes.Small).clickable(onClickLabel = "Copy the answer") {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Answer", text))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Answer copied", Toast.LENGTH_SHORT).show()
        }.padding(horizontal = IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
        Text("Copy", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
    }
}

/** A step: what it did, how it went, and its output a tap away. */
@Composable
private fun AssistantStepRow(step: AssistantEntry.Step) {
    val (icon, tint) = when (step.status) {
        AssistantStepStatus.Running -> Icons.Outlined.Sync to IcodeColors.Accent
        AssistantStepStatus.Queued -> Icons.Outlined.Schedule to IcodeColors.TextMuted
        AssistantStepStatus.Asking -> Icons.AutoMirrored.Outlined.HelpOutline to IcodeColors.Amber
        AssistantStepStatus.Done -> Icons.Outlined.Check to IcodeColors.Green
        AssistantStepStatus.Failed -> Icons.Outlined.ErrorOutline to IcodeColors.Error
    }
    Disclosure(
        key = step.key,
        icon = icon,
        tint = tint,
        label = step.call.summary,
        detail = step.output?.take(SHOWN_OUTPUT),
        status = STATUS_WORDS.getValue(step.status),
        mono = step.call.kind == AssistantToolKind.Command,
    )
}

/** A line of the chat with more under it: a step and its output, or a compaction and its summary. */
@Composable
private fun Disclosure(
    key: String,
    icon: ImageVector,
    tint: Color,
    label: String,
    detail: String?,
    status: String? = null,
    mono: Boolean = false,
    divided: Boolean = false,
) {
    val scale = LocalCodeTextScale.current
    var open by rememberSaveable(key) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        if (divided) Divider()
        Row(
            Modifier.fillMaxWidth().clickable(enabled = detail != null) { open = !open }.padding(vertical = IcodeSpacing.Space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            Icon(icon, contentDescription = status, tint = tint, modifier = Modifier.size(IcodeSizes.IconSm))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = (if (mono) IcodeType.MonoSmall else IcodeType.Caption).scaledBy(scale).copy(color = IcodeColors.TextSecondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (open) "Hide" else "Show",
                    tint = IcodeColors.TextMuted,
                    modifier = Modifier.size(IcodeSizes.IconSm),
                )
            }
        }
        if (open && detail != null) {
            CodeSurface(Modifier.fillMaxWidth()) {
                Text(detail, style = IcodeType.MonoSmall.scaledBy(scale))
            }
        }
        if (divided) Divider()
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
            mark = { IcodeBrandMark(Modifier.size(IcodeSizes.MarkLg)) },
        )
        Text(
            "What it reads — files, search results, command output — is sent to the provider you choose.",
            style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
        )
        PrimaryButton("Set Up Assistant", onSetUp, Modifier.fillMaxWidth())
    }
}

private val STATUS_WORDS = mapOf(
    AssistantStepStatus.Running to "Running",
    AssistantStepStatus.Queued to "Next",
    AssistantStepStatus.Asking to "Waiting for your answer",
    AssistantStepStatus.Done to "Done",
    AssistantStepStatus.Failed to "Failed",
)

/** A user's message takes most of the width, at the end, as chats show it. */
private const val SAID_WIDTH = 0.85f

/** An output shown on the page: the model was given at most a few times this. */
private const val SHOWN_OUTPUT = 8_000

