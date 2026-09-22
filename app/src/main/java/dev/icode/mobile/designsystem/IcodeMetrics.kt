package dev.icode.mobile.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Spacing scale from `docs/layout.md` §4.5 (`space.1` … `space.6`).
 *
 * [Gutter] is the compact-screen page gutter. The layout document names 20 dp
 * for gutters; the implemented frames consistently use 16 dp, so 16 dp is the
 * adopted standard until Figma says otherwise (recorded deviation).
 */
object IcodeSpacing {
    val None = 0.dp
    val Micro = 2.dp
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 20.dp
    val Space6 = 24.dp

    val Gutter = Space4
    val CardPadding = Space3

    /** Tree indentation step (explorer rows). */
    val IndentStep = 18.dp
}

/** Shape tokens shared by every surface. */
object IcodeShapes {
    val Small = RoundedCornerShape(4.dp)
    val Chip = RoundedCornerShape(3.dp)
    val Pill = RoundedCornerShape(50)
    val Track = RoundedCornerShape(2.dp)
    val Sheet = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
}

/** Size tokens for icons, bars and shells. */
object IcodeSizes {
    val IconSm = 16.dp
    val IconMd = 18.dp
    val IconLg = 22.dp
    val IconXl = 24.dp
    val BadgeSm = 32.dp
    val BadgeMd = 36.dp
    val Tile = 74.dp
    val TouchTarget = 48.dp
    val DotMd = 12.dp
    val TopBar = 56.dp
    val TopBarWithSubtitle = 64.dp
    val TabStrip = 38.dp
    val BottomBar = 48.dp
    val LineNumberGutter = 32.dp
    val Swatch = 28.dp
    val ContentMaxWidth = 600.dp

    /** Round status marks: empty states, dialogs and health banners. */
    val MarkSm = 36.dp
    val MarkMd = 42.dp
    val MarkLg = 64.dp
    val MarkIconSm = 18.dp
    val MarkIconMd = 30.dp
    val MarkIconLg = 36.dp
}
