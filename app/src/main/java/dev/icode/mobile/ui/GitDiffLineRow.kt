package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DiffKind
import dev.icode.mobile.designsystem.DiffRow
import dev.icode.mobile.domain.GitDiffLine
import dev.icode.mobile.domain.GitDiffLineKind

/** One line of a Git diff, as the diff screen and the editor's change sheet draw it. */
@Composable
internal fun GitDiffLineRow(line: GitDiffLine, modifier: Modifier = Modifier) = DiffRow(
    numbers = line.gutter,
    content = line.text,
    kind = when (line.kind) {
        GitDiffLineKind.Added -> DiffKind.Added
        GitDiffLineKind.Removed -> DiffKind.Removed
        GitDiffLineKind.Context -> DiffKind.Context
    },
    modifier = modifier,
)
