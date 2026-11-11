package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.BottomBarEntry
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeLoadingLine
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PinchToScaleText
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.GitDiffLineRow
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.syntax.diffSources
import dev.icode.mobile.ui.syntax.languageOf
import dev.icode.mobile.ui.syntax.rememberSyntaxLines
import kotlinx.coroutines.launch

/**
 * Inline diff for one file.
 *
 * The content comes from Git's own patch output, never from an editor
 * snapshot, so what is shown is what Git would commit (US-GIT-003).
 */
@Composable
fun DiffViewScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    // Until the diff asked for is read, the last one is not shown as if it were it.
    val diff = state.activeDiff.takeUnless { state.isBusy(BusyAction.OpenDiff) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var hunkIndex by rememberSaveable(diff?.path) { mutableIntStateOf(0) }
    // Coloured as the file's language, each hunk's sides tokenized whole (docs/syntax-highlighting-plan.md S5).
    val language = diff?.let { languageOf(it.path.substringAfterLast('/'), firstLineOf(it)) }
    val sources = remember(diff) { diff?.let { diffSources(it.hunks) } }
    val colouring = rememberSyntaxLines(language, sources?.texts.orEmpty())

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = diff?.path?.substringAfterLast('/') ?: "Diff",
            subtitle = diff?.path,
            onBack = actions.back,
            actions = {
                // Reaching this page already says the file changed; only the
                // staged side, which the same file can also have, needs saying.
                if (diff?.staged == true) StatusLabel("STAGED", IcodeColors.Green)
            },
        )
        when {
            diff == null && state.isBusy(BusyAction.OpenDiff) -> IcodeLoadingLine("Reading the changes…", Modifier.padding(IcodeSpacing.Gutter))
            diff == null -> EmptyState(
                title = "No file selected",
                description = "Choose a changed file in Source Control to see its diff.",
                modifier = Modifier.padding(IcodeSpacing.Space6),
            )
            diff.binary -> EmptyState(
                title = "Binary file",
                description = "Git does not produce a textual diff for this file.",
                modifier = Modifier.padding(IcodeSpacing.Space6),
            )
            diff.hunks.isEmpty() -> EmptyState(
                title = "No changes",
                description = if (diff.staged) {
                    "Nothing is staged for this file."
                } else {
                    "This file has no unstaged changes."
                },
                modifier = Modifier.padding(IcodeSpacing.Space6),
            )
            else -> {
                PinchToScaleText(Modifier.weight(1f)) {
                    LazyColumn(state = listState) {
                        diff.hunks.forEachIndexed { hunkAt, hunk ->
                            item(key = "header-${hunk.header}-${hunk.newStart}") {
                                MonoLine(
                                    text = hunk.header,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(IcodeColors.Surface)
                                        .padding(IcodeSpacing.Space3 + IcodeSpacing.Micro),
                                    color = IcodeColors.TextSecondary,
                                )
                            }
                            items(hunk.lines.size) { lineIndex ->
                                val line = hunk.lines[lineIndex]
                                GitDiffLineRow(line, colouring.line(sources?.places?.get(hunkAt)?.get(lineIndex), line.text), coloured = language != null)
                            }
                        }
                    }
                }
                // The summary and the way between changes: the bottom bar, as the editor's (owner's design).
                IcodeBottomBar(
                    items = listOf(
                        BottomBarItem(
                            "+${diff.addedLines} −${diff.removedLines} · ${countOf(diff.hunks.size, "hunk")}",
                        ),
                    ),
                    trailing = {
                        BottomBarEntry(
                            BottomBarItem(
                                "Prev",
                                onClick = {
                                    hunkIndex = (hunkIndex - 1).coerceAtLeast(0)
                                    scope.launch { listState.scrollToItem(itemIndexOfHunk(diff, hunkIndex)) }
                                },
                                enabled = hunkIndex > 0,
                                description = "Previous change",
                                icon = Icons.Outlined.ArrowUpward,
                            ),
                        )
                        BottomBarEntry(
                            BottomBarItem(
                                "Next",
                                onClick = {
                                    hunkIndex = (hunkIndex + 1).coerceAtMost(diff.hunks.lastIndex)
                                    scope.launch { listState.scrollToItem(itemIndexOfHunk(diff, hunkIndex)) }
                                },
                                enabled = hunkIndex < diff.hunks.lastIndex,
                                description = "Next change",
                                icon = Icons.Outlined.ArrowDownward,
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** The file's first line when the diff shows it: what names a script with no extension. */
private fun firstLineOf(diff: GitFileDiff): String =
    diff.hunks.firstOrNull()?.lines?.firstOrNull { it.newLine == 1 }?.text.orEmpty()

/** Header rows are list items too, so a hunk's first row sits after its header. */
private fun itemIndexOfHunk(diff: GitFileDiff, hunkIndex: Int): Int {
    var index = 0
    for (position in 0 until hunkIndex.coerceAtMost(diff.hunks.lastIndex)) {
        index += 1 + diff.hunks[position].lines.size
    }
    return index
}
