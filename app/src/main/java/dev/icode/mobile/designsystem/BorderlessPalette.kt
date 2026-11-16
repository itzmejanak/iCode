package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.domain.AccentColorRules
import java.util.concurrent.ConcurrentHashMap

/**
 * This palette for cards drawn without their outline (docs/design-and-navigation.md): a card and its
 * page at least [CARD_STEP] : 1 apart; every other surface its own step from what it sits on; and
 * every colour drawn on a surface that moved reading at least as well as before, or as well as WCAG
 * AA asks. A tone moves only in lightness, its hue and chroma kept ([Oklch.readable]). The accent is
 * never moved: it is the theme's, or the user's.
 */
internal fun IcodePalette.borderless(): IcodePalette = borderlessPalettes.getOrPut(this) { derived() }

private fun IcodePalette.derived(): IcodePalette {
    // Light's cards are white already: its page moves down, and the editor's deeper page with it.
    val pageMoves = kind == PaletteKind.Light
    // Each tone keeps to the side of its neighbour it was on: a variant's raised tone can move past its
    // track (Rosé Pine Dawn), and the track must then follow it, not turn back (docs/design-and-navigation.md).
    val page = if (pageMoves) apart(background, surface, CARD_STEP, side(background, surface)) else background
    val deep = if (pageMoves) apart(backgroundDeep, page, contrast(backgroundDeep, background), side(backgroundDeep, background)) else backgroundDeep
    val card = if (pageMoves) surface else apart(surface, page, CARD_STEP, side(surface, background))
    val elevated = apart(
        apart(surfaceElevated, card, contrast(surfaceElevated, surface), side(surfaceElevated, surface)),
        page, contrast(surfaceElevated, background), side(surfaceElevated, background),
    )
    val track = apart(
        apart(surfaceTrack, elevated, contrast(surfaceTrack, surfaceElevated), side(surfaceTrack, surfaceElevated)),
        card, contrast(surfaceTrack, surface), side(surfaceTrack, surface),
    )
    val moved = listOf(background to page, backgroundDeep to deep, surface to card, surfaceElevated to elevated)
    // Drawn on those surfaces: as readable as it was on each, up to what WCAG AA asks of it.
    fun kept(color: Color, enough: Double, on: List<Pair<Color, Color>> = moved) = on.fold(color) { kept, (before, after) ->
        apart(kept, after, minOf(contrast(color, before), enough))
    }
    return copy(
        background = page, backgroundDeep = deep, surface = card, surfaceElevated = elevated, surfaceTrack = track,
        outline = kept(outline, AccentColorRules.MIN_CONTRAST),
        amber = kept(amber, TEXT_CONTRAST), green = kept(green, TEXT_CONTRAST), blue = kept(blue, TEXT_CONTRAST),
        error = kept(error, TEXT_CONTRAST),
        textPrimary = kept(textPrimary, TEXT_CONTRAST), textSecondary = kept(textSecondary, TEXT_CONTRAST),
        textMuted = kept(textMuted, TEXT_CONTRAST),
        // The terminal's colours are drawn on its background alone.
        terminalAnsi = terminalAnsi.map { kept(it, TEXT_CONTRAST, on = listOf(backgroundDeep to deep)) },
    )
}

/** [color] moved away from [from] — [darker], by default as it already lies — until they are [minimum] : 1 apart. */
private fun apart(color: Color, from: Color, minimum: Double, darker: Boolean = color.luminance() < from.luminance()): Color =
    Oklch.readable(color.toArgb() and 0xFFFFFF, listOf(from), minimum, darker)

/** Whether [tone] lies darker than [neighbour] in the palette as it was made. */
private fun side(tone: Color, neighbour: Color): Boolean = tone.luminance() < neighbour.luminance()

private fun contrast(first: Color, second: Color): Double = AccentColorRules.contrastRatio(first.toArgb(), second.toArgb())

private val borderlessPalettes = ConcurrentHashMap<IcodePalette, IcodePalette>()

/**
 * A card and its page with no line between them, at least this far apart: iOS keeps its grouped
 * cells 1.116 : 1 from their page, Material 3's dark scheme a container 1.141 : 1 from its page. In
 * steps of 0.01 lightness each theme lands just past it (docs/design-and-navigation.md); the owner
 * chose it from mockups of the three (2026-10-19).
 */
private const val CARD_STEP = 1.12

/** WCAG AA for text; the outline, a control's edge, asks [AccentColorRules.MIN_CONTRAST]. */
private const val TEXT_CONTRAST = 4.5
