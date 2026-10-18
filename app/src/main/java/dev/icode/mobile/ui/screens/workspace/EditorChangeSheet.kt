package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.GitDiffHunk
import dev.icode.mobile.domain.LineChangeKind
import dev.icode.mobile.domain.kind
import dev.icode.mobile.ui.GitDiffLineRow

/**
 * One change of the open file against Git's index (docs/editor-plan.md §3.5): its lines, removed
 * then added, and Revert, Stage, and the way to the changes before and after it.
 */
@Composable
internal fun EditorChangeSheet(
    changes: List<GitDiffHunk>,
    index: Int,
    onRevert: (GitDiffHunk) -> Unit,
    onStage: (GitDiffHunk) -> Unit,
    onShow: (index: Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change = changes[index]
    IcodeBottomSheet(
        title = "Change ${index + 1} of ${changes.size}",
        onClose = onClose,
        modifier = modifier,
        footer = {
            TwoColumnActions(
                left = { SecondaryButton("Revert", { onRevert(change) }, Modifier.fillMaxWidth()) },
                right = { PrimaryButton("Stage", { onStage(change) }, Modifier.fillMaxWidth()) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IcodeTextButton("‹ Prev", { onShow(index - 1) }, enabled = index > 0)
                IcodeTextButton("Next ›", { onShow(index + 1) }, enabled = index < changes.lastIndex)
            }
        },
    ) {
        MonoLine(where(change), color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall)
        change.lines.forEach { GitDiffLineRow(it) }
    }
}

/** "Modified · lines 13–15", "Added · line 5", "Deleted · after line 7": the editor's line numbers. */
private fun where(change: GitDiffHunk): String {
    val last = change.newStart + change.newCount - 1
    val lines = if (change.newCount == 1) "line ${change.newStart}" else "lines ${change.newStart}–$last"
    return when (change.kind) {
        LineChangeKind.Added -> "Added · $lines"
        LineChangeKind.Modified -> "Modified · $lines"
        LineChangeKind.Deleted -> if (change.newStart == 0) "Deleted · at the start" else "Deleted · after line ${change.newStart}"
    }
}
