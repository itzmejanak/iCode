package dev.icode.mobile.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Spacing scale from `docs/layout.md` §4.5 (`space.1` … `space.6`).
 *
 * [Gutter] is the compact-screen page gutter. The layout document names 20 dp
 * for gutters; the implemented frames consistently use 16 dp, so 16 dp is the
 * adopted standard until Figma says otherwise (recorded deviation).
 */
internal val LocalIcodeCompactLayout = staticCompositionLocalOf { true }

object IcodeSpacing {
    val None = 0.dp
    val Micro = 2.dp
    val Space1 = 4.dp
    val Space2 @Composable get() = if (LocalIcodeCompactLayout.current) 8.dp else 12.dp
    val Space3 @Composable get() = if (LocalIcodeCompactLayout.current) 12.dp else 16.dp
    val Space4 @Composable get() = if (LocalIcodeCompactLayout.current) 16.dp else 20.dp
    val Space5 @Composable get() = if (LocalIcodeCompactLayout.current) 20.dp else 24.dp
    val Space6 @Composable get() = if (LocalIcodeCompactLayout.current) 24.dp else 28.dp

    val Gutter @Composable get() = Space4
    val CardPadding @Composable get() = Space3

    /** Tree indentation step (explorer rows). */
    val IndentStep = 18.dp
}

/** Shape tokens shared by every surface. */
object IcodeShapes {
    val Small = RoundedCornerShape(4.dp)
    val RailContent = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
    val AppIcon = RoundedCornerShape(22)
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
    val MenuWidth = 200.dp
    val DialogMaxWidth = 400.dp
    val DotMd = 12.dp
    val TopBar = 56.dp
    val TopBarWithSubtitle = 64.dp
    val TabStrip = 38.dp
    val TerminalTabBar = 40.dp


    /** Offscreen-sized input that only exists to receive keystrokes. */
    val HiddenInput = 1.dp
    val BottomBar = 48.dp
    val LineNumberGutter = 32.dp
    val Swatch = 28.dp

    /** The accent picker: its HSV wheel, brightness bar and preset chips. */
    val ColorWheel = 200.dp
    val ColorBar = 28.dp
    val ColorChip = 32.dp
    val ContentMaxWidth = 600.dp

    /** Round status marks: empty states, dialogs and health banners. */
    val MarkSm = 36.dp
    val MarkMd = 42.dp
    val MarkLg = 64.dp
    val MarkIconSm = 18.dp
    val MarkIconMd = 30.dp
    val MarkIconLg = 36.dp
}
