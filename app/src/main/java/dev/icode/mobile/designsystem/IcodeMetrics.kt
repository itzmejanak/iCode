package dev.icode.mobile.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

internal val LocalIcodeCompactLayout = staticCompositionLocalOf { true }

/**
 * Spacing scale from `docs/layout.md` §4.5 (`space.1` … `space.6`); from `space.2` up, each step
 * is 4 dp wider while Appearance › Compact Layout is off.
 *
 * Every page keeps one rhythm (§4.5): its content [Gutter] from the screen's edges, a group's label
 * and items [ItemGap] apart, its groups [SectionGap] apart.
 */
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
    /**
     * A bar's edge beside an icon button: the button's touch target is wider than its icon by
     * [IconButtonInset] each side, so the icon lines up with the page's [Gutter], as Material's top
     * app bar has it.
     */
    val IconButtonGutter @Composable get() = Gutter - IconButtonInset
    val IconButtonInset = (IcodeSizes.TouchTarget - IcodeSizes.IconXl) / 2
    /** A list row's text in from the row's edge, where its selection is drawn ([IcodeListRow]). */
    val RowInset @Composable get() = Space2
    /** Beside a list of rows: their text then lines up with the page's [Gutter], their selection a [RowInset] wider. */
    val RowGutter @Composable get() = Gutter - RowInset
    val ItemGap @Composable get() = Space2
    val SectionGap @Composable get() = Space5
    /** Above a section's label among items [ItemGap] apart — a list's, a sheet's — making its gap a [SectionGap]. */
    val SectionBreak @Composable get() = SectionGap - ItemGap
    val CardPadding @Composable get() = Space3

    /** Tree indentation step (explorer rows). */
    val IndentStep = 18.dp
}

/** Shape tokens shared by every surface. */
object IcodeShapes {
    val Small = RoundedCornerShape(4.dp)
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
    /** The × that takes a picture off, in a [Tile]'s corner. */
    val TileRemove = 28.dp
    /** The assistant's bubble over other apps (docs/phone-agent-plan.md §5.7), its ring, and its panel's width at most. */
    val Bubble = 52.dp
    val BubbleRing = 3.dp
    val BubblePanel = 380.dp
    /** A grid of files (docs/explorer-gaps-plan.md §3.12): each tile at least this wide, its picture this big. */
    val GridTileMin = 104.dp
    val GridTilePicture = 56.dp
    val TouchTarget = 48.dp
    /**
     * An icon action's ripple in a card's top corner ([SectionHeader]): it spreads into the card's
     * padding, the narrowest of which it fits, and touch still finds it a [TouchTarget] wide.
     */
    val CornerAction = 40.dp
    /** A tree row's leading slot, its expand arrow or nothing: the rail beneath the row runs down its middle. */
    val Disclosure = 24.dp
    /** How far above the screen's bottom the floating + (56 dp) and the margin around it reach: what sits above it clears it by this. */
    val FabClearance = 88.dp
    val MenuWidth = 200.dp
    /** An option panel's tile ([IcodeOptionTile]): wide enough for "Detailed" in caption type, and its pill. */
    val OptionTile = 54.dp
    val DialogMaxWidth = 400.dp
    val DotMd = 12.dp
    /** A commit graph's lane (History sheet), its dot and the lines between them. */
    val GraphLane = 14.dp
    val GraphDot = 8.dp
    val GraphLine = 2.dp
    val TopBar = 56.dp
    val TopBarWithSubtitle = 64.dp
    val TabStrip = 38.dp
    val TerminalTabBar = 40.dp


    /** Offscreen-sized input that only exists to receive keystrokes. */
    val HiddenInput = 1.dp
    val BottomBar = 48.dp
    /** A choice's button in a bottom bar — the branch, the repository — and a compact action's height. */
    val BarChoice = 32.dp

    /** A licence's text on the License Agreement, at most: then it scrolls, the Accept below it in sight. */
    val LicenceText = 320.dp
    val LineNumberGutter = 32.dp
    val Swatch = 28.dp

    /** The accent picker: its HSV wheel, brightness bar and preset chips. */
    val ColorWheel = 200.dp
    val ColorBar = 28.dp
    val ColorChip = 32.dp
    val ContentMaxWidth = 600.dp

    /** How much the screen under a sheet is blurred (Android 12 and later; below, it is only dimmed). */
    val SheetBackdropBlur = 6.dp

    /**
     * The watermark's canvas: the themed icon's 108-unit square, whose silhouette stands in its
     * middle 44 units — at this size the logo itself is about 160 dp tall.
     */
    val Watermark = 400.dp

    /** The runtime install's ring ([ProgressRing]): about half a phone's width, inside the logo behind it. */
    val ProgressRing = 176.dp
    val RingStroke = 6.dp

    /** Round status marks: empty states, dialogs and health banners. */
    val MarkSm = 36.dp
    val MarkMd = 42.dp
    val MarkLg = 64.dp
    val MarkIconSm = 18.dp
    val MarkIconMd = 30.dp
    val MarkIconLg = 36.dp
}
