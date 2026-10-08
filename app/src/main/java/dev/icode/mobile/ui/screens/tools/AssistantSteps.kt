package dev.icode.mobile.ui.screens.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Difference
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LocalCodeTextScale
import dev.icode.mobile.designsystem.scaledBy
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantStepStatus
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.LineChanges
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import java.io.File

/**
 * A step: what it did, how it went — a change to a file by how many lines — and, a tap away, all it
 * answered, a command's line above its output. A long press on a step about a file offers it: open
 * it, see its changes, copy its path.
 */
@Composable
internal fun AssistantStepRow(step: AssistantEntry.Step, onDiff: (GitChange) -> Unit) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    val snapshot = LocalICodeSnapshot.current
    val (icon, tint) = when (step.status) {
        AssistantStepStatus.Running -> Icons.Outlined.Sync to IcodeColors.Accent
        AssistantStepStatus.Queued -> Icons.Outlined.Schedule to IcodeColors.TextMuted
        AssistantStepStatus.Asking -> Icons.AutoMirrored.Outlined.HelpOutline to IcodeColors.Amber
        AssistantStepStatus.Done -> Icons.Outlined.Check to IcodeColors.Green
        AssistantStepStatus.Failed -> Icons.Outlined.ErrorOutline to IcodeColors.Error
    }
    val file = step.call.file?.takeIf { it != "." }
    val location = file?.let { relative -> snapshot.assistant.workspace?.let { File(it, relative).path } }
    val change = location?.let { AssistantRules.gitChangeOf(it, snapshot.gitRepository, snapshot.gitChanges) }
    val actions = if (file == null || location == null) emptyList() else listOfNotNull(
        IcodeMenuAction("Open in Editor", Icons.Outlined.Description, { gateway.dispatch(ICodeIntent.OpenFile(location)) }),
        change?.let { IcodeMenuAction("Show Changes", Icons.Outlined.Difference, { onDiff(it) }) },
        IcodeMenuAction("Copy Path", Icons.Outlined.ContentCopy, { copyText(context, "Path", file, "Path copied") }),
    )
    Disclosure(
        key = step.key,
        icon = icon,
        tint = tint,
        label = step.call.summary,
        detail = step.output,
        heading = step.call.command?.let { "$ $it" },
        status = STATUS_WORDS.getValue(step.status),
        mono = step.call.kind == AssistantToolKind.Command,
        changes = step.changes?.takeIf { step.status == AssistantStepStatus.Done },
        actions = actions,
        actionsLabel = file?.let { "Options for $it" },
    )
}

/**
 * A line of the chat with more under it: a step and its output, or a compaction and its summary.
 * A tap opens it, and a tap on what opened closes it again; Copy takes all it shows. A long press
 * offers [actions] where there are some.
 */
@Composable
internal fun Disclosure(
    key: String,
    icon: ImageVector,
    tint: Color,
    label: String,
    detail: String?,
    status: String? = null,
    mono: Boolean = false,
    divided: Boolean = false,
    /** Shown first when open, above [detail]: a command's line. */
    heading: String? = null,
    changes: LineChanges? = null,
    actions: List<IcodeMenuAction> = emptyList(),
    actionsLabel: String? = null,
) {
    val scale = LocalCodeTextScale.current
    val context = LocalContext.current
    var open by rememberSaveable(key) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val opens = detail != null || heading != null
    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        if (divided) Divider()
        if (open && opens) {
            // Expanded view: replaces the summary row entirely to avoid duplication
            CodeSurface(Modifier.fillMaxWidth().clip(IcodeShapes.Small).clickable(onClickLabel = "Hide") { open = false }) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    Icon(icon, contentDescription = status, tint = tint, modifier = Modifier.size(IcodeSizes.IconSm).padding(top = IcodeSpacing.Space1))
                    Column(Modifier.weight(1f)) {
                        Text(
                            heading ?: label, // Use heading if available, otherwise fall back to label
                            style = IcodeType.MonoSmall.scaledBy(scale).copy(color = IcodeColors.TextPrimary),
                        )
                        detail?.let { Text(it, modifier = Modifier.padding(top = IcodeSpacing.Space1), style = IcodeType.MonoSmall.scaledBy(scale)) }
                    }
                    CopyChip { copyText(context, "Output", listOfNotNull(heading, detail).joinToString("\n\n"), "Copied") }
                }
            }
        } else {
            // Collapsed view: the summary row
            Box {
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            enabled = opens || actions.isNotEmpty(),
                            onClickLabel = if (open) "Hide" else "Show",
                            onLongClickLabel = actionsLabel,
                            onLongClick = if (actions.isEmpty()) null else ({ menu = true }),
                            onClick = { if (opens) open = !open },
                        )
                        .padding(vertical = IcodeSpacing.Space1),
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
                    changes?.let { ChangedLines(it) }
                    if (opens) {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = IcodeColors.TextMuted,
                            modifier = Modifier.size(IcodeSizes.IconSm),
                        )
                    }
                }
                IcodeActionMenu(expanded = menu, onDismiss = { menu = false }, actions = actions)
            }
        }
        if (divided) Divider()
    }
}

/** "+12 −3": what a change added, in green, and removed, in red. */
@Composable
private fun ChangedLines(changes: LineChanges) {
    val scale = LocalCodeTextScale.current
    Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        if (changes.added > 0) Text("+${changes.added}", style = IcodeType.MonoSmall.scaledBy(scale).copy(color = IcodeColors.Green))
        if (changes.removed > 0) Text("−${changes.removed}", style = IcodeType.MonoSmall.scaledBy(scale).copy(color = IcodeColors.Error))
        if (changes.added == 0 && changes.removed == 0) Text(changes.label, style = IcodeType.Caption.scaledBy(scale).copy(color = IcodeColors.TextMuted))
    }
}

/** Copy, at the corner of what it copies. */
@Composable
private fun CopyChip(onCopy: () -> Unit) {
    Row(
        Modifier.minimumInteractiveComponentSize().clip(IcodeShapes.Small).clickable(onClickLabel = "Copy", onClick = onCopy)
            .padding(horizontal = IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro),
    ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
        Text("Copy", style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
    }
}

/** [text] to the clipboard; below Android 13, which says so itself, a toast says [said]. */
internal fun copyText(context: Context, label: String, text: String, said: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
}

private val STATUS_WORDS = mapOf(
    AssistantStepStatus.Running to "Running",
    AssistantStepStatus.Queued to "Next",
    AssistantStepStatus.Asking to "Waiting for your answer",
    AssistantStepStatus.Done to "Done",
    AssistantStepStatus.Failed to "Failed",
)
