package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Line classification for inline and conflict diffs. */
enum class DiffKind { Context, Separator, Added, Removed, ConflictCurrent, ConflictIncoming }

/** Row background for a diff kind. */
fun DiffKind.surface(): Color = when (this) {
    DiffKind.Context -> IcodeColors.Background
    DiffKind.Separator -> IcodeColors.Outline
    DiffKind.Added -> IcodeColors.DiffAddedSurface
    DiffKind.Removed -> IcodeColors.DiffRemovedSurface
    DiffKind.ConflictCurrent -> IcodeColors.ConflictCurrentSurface
    DiffKind.ConflictIncoming -> IcodeColors.ConflictIncomingSurface
}

/** Content tint for a diff kind. */
fun DiffKind.tint(): Color = when (this) {
    DiffKind.Context -> IcodeColors.TextPrimary
    DiffKind.Separator -> IcodeColors.TextMuted
    DiffKind.Added -> IcodeColors.Green
    DiffKind.Removed -> IcodeColors.Error
    DiffKind.ConflictCurrent -> IcodeColors.Error
    DiffKind.ConflictIncoming -> IcodeColors.Blue
}

/** Diff line with dual line numbers and content tinted by kind. */
@Composable
fun DiffRow(
    numbers: String,
    content: String,
    kind: DiffKind,
    modifier: Modifier = Modifier,
    contentColor: Color = kind.tint(),
) {
    Row(modifier = modifier.fillMaxWidth().background(kind.surface()).padding(vertical = 3.dp)) {
        MonoLine(
            text = numbers,
            modifier = Modifier.width(56.dp).padding(horizontal = IcodeSpacing.Space1),
            color = IcodeColors.TextSecondary,
            style = IcodeType.MonoSmall,
        )
        MonoLine(content, color = contentColor)
    }
}

/** Full-width band marking a hunk header or a conflict side. */
@Composable
fun DiffBand(text: String, kind: DiffKind, modifier: Modifier = Modifier) {
    MonoLine(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .background(kind.surface())
            .padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space1),
        color = kind.tint(),
    )
}
