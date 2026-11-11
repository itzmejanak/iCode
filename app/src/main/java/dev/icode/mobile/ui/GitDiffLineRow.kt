package dev.icode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import dev.icode.mobile.designsystem.DiffKind
import dev.icode.mobile.designsystem.DiffRow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.domain.GitDiffLine
import dev.icode.mobile.domain.GitDiffLineKind

/**
 * One line of a Git diff, as the diff screen and the editor's change sheet draw it: [content] is its text,
 * coloured by the file's language when [coloured] (docs/syntax-highlighting-plan.md S5), its plain parts
 * then in the text's own colour on the kind's background, as the editor draws them; else all in the kind's tint.
 */
@Composable
internal fun GitDiffLineRow(line: GitDiffLine, content: AnnotatedString, coloured: Boolean, modifier: Modifier = Modifier) {
    val kind = when (line.kind) {
        GitDiffLineKind.Added -> DiffKind.Added
        GitDiffLineKind.Removed -> DiffKind.Removed
        GitDiffLineKind.Context -> DiffKind.Context
    }
    DiffRow(
        numbers = line.gutter,
        content = content,
        kind = kind,
        modifier = modifier,
        contentColor = if (coloured) IcodeColors.TextPrimary else kind.tint(),
    )
}
