package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.kind
import dev.icode.mobile.ui.GitDiffLineRow
import dev.icode.mobile.ui.syntax.diffSources
import dev.icode.mobile.ui.syntax.languageOf
import dev.icode.mobile.ui.syntax.rememberSyntaxLines

/**
 * One change of the open file against Git's index (docs/editor-and-language-services.md): its lines, removed
 * then added, and Revert, Stage, and the way to the changes before and after it. The lines are coloured
 * as [document]'s language, each as its line in the whole file: added ones in [document]'s text, removed
 * ones in [base], the index version (docs/editor-and-language-services.md).
 */
@Composable
internal fun EditorChangeSheet(
    document: OpenDocument,
    base: String,
    changes: List<GitDiffHunk>,
    index: Int,
    onRevert: (GitDiffHunk) -> Unit,
    onStage: (GitDiffHunk) -> Unit,
    onShow: (index: Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change = changes[index]
    val language = languageOf(document.name, document.text)
    val sources = remember(changes, base, document.text) { diffSources(changes, base, document.text) }
    val colouring = rememberSyntaxLines(language, sources.texts)
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
                IcodeTextButton("Prev", { onShow(index - 1) }, enabled = index > 0, icon = Icons.Outlined.ArrowUpward)
                IcodeTextButton("Next", { onShow(index + 1) }, enabled = index < changes.lastIndex, icon = Icons.Outlined.ArrowDownward)
            }
        },
    ) {
        MonoLine(where(change), color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall)
        change.lines.forEachIndexed { at, line -> GitDiffLineRow(line, colouring.line(sources.places[index][at], line.text), coloured = language != null) }
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
