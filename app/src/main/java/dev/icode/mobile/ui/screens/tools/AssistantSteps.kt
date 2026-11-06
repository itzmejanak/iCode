package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LocalCodeTextScale
import dev.icode.mobile.designsystem.scaledBy
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantStepRules
import dev.icode.mobile.domain.AssistantStepStatus
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.LineChanges
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.domain.word
import dev.icode.mobile.ui.FileItemActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import java.io.File

/**
 * A run of steps (docs/assistant-sheets-plan.md §2.1): how it went, what it did — one step by its
 * summary, more by what each tool did — what it changed, and › to its sheet. A long press on one
 * step about a file [onHold]s it, for the file's sheet: open it, see its changes, copy its path.
 */
@Composable
internal fun AssistantStepsRow(entry: AssistantEntry.Steps, onOpen: () -> Unit, onHold: () -> Unit) {
    val status = AssistantStepRules.status(entry.steps)
    val (icon, tint) = statusIcon(status)
    val single = entry.steps.singleOrNull()
    AssistantOpenRow(
        icon = icon,
        tint = tint,
        label = AssistantStepRules.summary(entry.steps),
        status = status.word,
        onOpen = onOpen,
        mono = single?.call?.kind == AssistantToolKind.Command,
        changes = AssistantStepRules.changes(entry.steps),
        onHold = onHold.takeIf { single?.let(::fileOf) != null },
        holdLabel = single?.call?.file?.let { "Options for $it" },
    )
}

/**
 * A line of the chat with more behind it — a run of steps, the reasoning, a compaction — that
 * [onOpen]s it in a sheet (§2): its icon, what it is, [detail] after it, what it changed, and ›.
 * Without [onOpen] it is only the line, as reasoning still being written is. A long press goes to
 * [onHold] where there is one, saying [holdLabel]. On a sheet's [rail], a line joins its icon to its neighbours'.
 */
@Composable
internal fun AssistantOpenRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    status: String?,
    onOpen: (() -> Unit)?,
    mono: Boolean = false,
    detail: String? = null,
    changes: LineChanges? = null,
    rail: AssistantRail? = null,
    onHold: (() -> Unit)? = null,
    holdLabel: String? = null,
) {
    val scale = LocalCodeTextScale.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = IcodeSizes.TouchTarget).height(IntrinsicSize.Min).clip(IcodeShapes.Small)
            .combinedClickable(
                enabled = onOpen != null || onHold != null,
                onClickLabel = "Show",
                onLongClickLabel = holdLabel,
                onLongClick = onHold,
                onClick = { onOpen?.invoke() },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            RailLine(rail?.before == true)
            Icon(icon, contentDescription = status, tint = tint, modifier = Modifier.size(IcodeSizes.IconSm))
            RailLine(rail?.after == true)
        }
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = IcodeSpacing.Space1),
            style = (if (mono) IcodeType.MonoSmall else IcodeType.Caption).scaledBy(scale).copy(color = IcodeColors.TextSecondary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        detail?.let { Text(it, style = IcodeType.Caption.scaledBy(scale).copy(color = IcodeColors.TextMuted), maxLines = 1) }
        changes?.let { ChangedLines(it) }
        if (onOpen != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
        }
    }
}

/** Where a step sits on its run's sheet (§2.2): a line runs up to the step before it, and down to the one after. */
internal enum class AssistantRail(val before: Boolean, val after: Boolean) {
    First(false, true),
    Middle(true, true),
    Last(true, false),
    ;

    companion object {
        /** The place of the step at [index] of [count]; none for a step alone. */
        fun of(index: Int, count: Int): AssistantRail? = when {
            count < 2 -> null
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }
    }
}

/** Half a rail, above or below a step's icon, filling the row's height so the next row's meets it. */
@Composable
private fun ColumnScope.RailLine(shown: Boolean) {
    Box(Modifier.weight(1f).width(IcodeSpacing.Micro).then(if (shown) Modifier.background(IcodeColors.Outline) else Modifier))
}

/** What a step's file offers: open it, see its changes where Git has some, copy the path the runtime names it by. */
internal class StepFileActions(val open: IcodeMenuAction, val changes: IcodeMenuAction?, val copyPath: IcodeMenuAction) {
    /** As the file's sheet lists them. */
    val all: List<IcodeMenuAction> get() = listOfNotNull(open, changes, copyPath)
}

/** [step]'s file and what it offers, for the file's sheet and its step's page; null for a step without one. */
@Composable
internal fun fileActions(step: AssistantEntry.Step, onDiff: (GitChange) -> Unit): StepFileActions? {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    val snapshot = LocalICodeSnapshot.current
    val root = snapshot.assistant.workspace ?: return null
    val file = fileOf(step) ?: return null
    val location = File(root, file).path
    val change = AssistantRules.gitChangeOf(location, snapshot.gitRepository, snapshot.gitChanges)
    return StepFileActions(
        open = IcodeMenuAction("Open in Editor", Icons.Outlined.Description, { gateway.dispatch(ICodeIntent.OpenFile(location)) }),
        changes = change?.let { IcodeMenuAction("Show Changes", Icons.Outlined.Difference, { onDiff(it) }) },
        // As every explorer's: the path the runtime names it by.
        copyPath = FileItemActions.copyPath(context, WorkspaceRuntimePath.within(root, WorkspaceRuntimePath.of(AssistantPlace.name(root)), location), gateway::dispatch),
    )
}

/** [step]'s file, from the workspace's top; none for a step about none, or about the workspace itself. */
private fun fileOf(step: AssistantEntry.Step): String? = step.call.file?.takeIf { it != "." }

/** How a step went, as its icon and colour say it. */
@Composable
internal fun statusIcon(status: AssistantStepStatus): Pair<ImageVector, Color> = when (status) {
    AssistantStepStatus.Running -> Icons.Outlined.Sync to IcodeColors.Accent
    AssistantStepStatus.Queued -> Icons.Outlined.Schedule to IcodeColors.TextMuted
    AssistantStepStatus.Asking -> Icons.AutoMirrored.Outlined.HelpOutline to IcodeColors.Amber
    AssistantStepStatus.Done -> Icons.Outlined.Check to IcodeColors.Green
    AssistantStepStatus.Failed -> Icons.Outlined.ErrorOutline to IcodeColors.Error
}

/** "+12 −3": what a change added, in green, and removed, in red. */
@Composable
internal fun ChangedLines(changes: LineChanges) {
    val scale = LocalCodeTextScale.current
    Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        if (changes.added > 0) Text("+${changes.added}", style = IcodeType.MonoSmall.scaledBy(scale).copy(color = IcodeColors.Green))
        if (changes.removed > 0) Text("−${changes.removed}", style = IcodeType.MonoSmall.scaledBy(scale).copy(color = IcodeColors.Error))
        if (changes.added == 0 && changes.removed == 0) Text(changes.label, style = IcodeType.Caption.scaledBy(scale).copy(color = IcodeColors.TextMuted))
    }
}

/** Copy, at the corner of what it copies. */
@Composable
internal fun CopyChip(onCopy: () -> Unit) = CornerChip("Copy", Icons.Outlined.ContentCopy, onCopy)

/** A small action at the corner of what it acts on: Copy, Open in Terminal. */
@Composable
internal fun CornerChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.minimumInteractiveComponentSize().clip(IcodeShapes.Small).clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro),
    ) {
        Icon(icon, contentDescription = null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
        Text(label, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
    }
}
