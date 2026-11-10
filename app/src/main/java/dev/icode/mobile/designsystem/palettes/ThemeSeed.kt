package dev.icode.mobile.designsystem.palettes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.Oklch
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.designsystem.SyntaxSeeds
import dev.icode.mobile.designsystem.borderless
import dev.icode.mobile.domain.AccentColorRules

/**
 * A ported theme as its project draws it (docs/theme-variants-plan.md D4): its own tones, text,
 * accent, status and code colours, and, where its project publishes them, its terminal's sixteen.
 * Everything else is derived at its family's own steps, measured on iCode's palette of that family,
 * so a variant behaves as its family does with Border Lines on or off.
 */
internal data class ThemeSeed(
    val id: String,
    val name: String,
    /** One of the well-known editor themes, listed first in the sheet. */
    val classic: Boolean,
    val page: Color,
    val card: Color,
    val raised: Color,
    /** The editor and terminal; null on Light, where it is derived from the page (D4, D5). */
    val editor: Color?,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val green: Color,
    val amber: Color,
    val error: Color,
    val blue: Color,
    val syntax: SyntaxSeeds,
    /** The project's own terminal colours, 0–7 then bright 8–15; null: derived from the status colours. */
    val ansi: List<Color>? = null,
)

/**
 * This seed drawn as [kind]. A given colour is kept unless it is under its floor, the same floors
 * iCode's own palettes meet: then only its lightness moves until it is not. A colour not given is
 * derived at the step the family's own palette keeps between those two tones.
 */
internal fun ThemeSeed.palette(kind: PaletteKind): IcodePalette {
    val family = familyOf(kind)
    val light = kind == PaletteKind.Light
    val deep = editor ?: step(page, page, ratio(family.backgroundDeep, family.background), darker = light)
    // The editor's current line, as IcodePalette draws it: plain code and errors are read there too.
    val line = lerp(deep, card, 0.5f)
    fun floor(color: Color, behind: List<Color>, minimum: Double) = Oklch.readable(color.rgb, behind, minimum, darker = light)
    val onSurfaces = listOf(page, card)
    val onAll = listOf(page, card, raised, deep, line)
    val text = floor(text, onAll, TEXT_CONTRAST)
    val secondary = floor(secondary, onAll, TEXT_CONTRAST)
    val accent = floor(accent, onSurfaces + deep, AccentColorRules.MIN_CONTRAST)
    val green = floor(green, onSurfaces, TEXT_CONTRAST)
    val amber = floor(amber, onSurfaces, TEXT_CONTRAST)
    val blue = floor(blue, onSurfaces, TEXT_CONTRAST)
    val error = floor(error, onSurfaces + deep + line, TEXT_CONTRAST)
    val muted = step(secondary, page, minOf(ratio(family.textMuted, family.background), ratio(secondary, page)), darker = light)
    // A tint keeps its family's strength and direction on the editor, in this seed's own hue.
    fun tint(status: Color, familyTint: Color) = Oklch.stepFrom(
        Oklch.lch(familyTint.rgb)[1], Oklch.lch(status.rgb)[2], deep,
        ratio(familyTint, family.backgroundDeep), darker = familyTint.luminance() < family.backgroundDeep.luminance(),
    )
    val removed = tint(error, family.diffRemovedSurface)
    val terminal = (ansi ?: derivedAnsi(light, text, secondary, muted, raised, error, green, amber, blue))
        // Black and bright black are the terminal's own tones; every colour drawn as text reads on it.
        .mapIndexed { index, color -> if (index == 0 || index == 8) color else floor(color, listOf(deep), TEXT_CONTRAST) }
    val drawn = IcodePalette(
        kind = kind,
        background = page, backgroundDeep = deep,
        surface = card, surfaceElevated = raised,
        surfaceTrack = step(raised, raised, ratio(family.surfaceTrack, family.surfaceElevated), darker = light),
        outline = step(card, page, ratio(family.outline, family.background), darker = light),
        accent = accent, amber = amber, green = green, blue = blue, error = error,
        textPrimary = text, textSecondary = secondary, textMuted = muted,
        onAccent = Color(AccentColorRules.onColor(accent.toArgb())),
        diffAddedSurface = tint(green, family.diffAddedSurface), diffRemovedSurface = removed,
        conflictCurrentSurface = removed, conflictIncomingSurface = tint(blue, family.conflictIncomingSurface),
        terminalAnsi = terminal,
        syntax = syntax,
    )
    // Without border lines the tones move and the accent does not (border-lines-plan §3): it must read on those too.
    val apart = drawn.borderless()
    val kept = floor(accent, listOf(apart.background, apart.surface, apart.backgroundDeep), AccentColorRules.MIN_CONTRAST)
    return if (kept == accent) drawn else drawn.copy(accent = kept, onAccent = Color(AccentColorRules.onColor(kept.toArgb())))
}

/**
 * This dark seed on true black (D3): the page and editor black, each surface its own hue as far
 * from black as iCode AMOLED's own, and every colour drawn on them its own, checked on black.
 */
internal fun ThemeSeed.onBlack(): IcodePalette {
    val family = IcodePalette.Amoled
    fun near(color: Color, tone: Color) = step(color, Color.Black, ratio(tone, family.background), darker = false)
    return copy(
        page = Color.Black, editor = Color.Black,
        card = near(card, family.surface), raised = near(raised, family.surfaceElevated),
    ).palette(PaletteKind.Amoled)
}

/** The sixteen from the seed's own colours, as iCode's own lists are made: Light's "white" a grey. */
private fun derivedAnsi(
    light: Boolean, text: Color, secondary: Color, muted: Color, raised: Color,
    error: Color, green: Color, amber: Color, blue: Color,
): List<Color> {
    val (blueL, blueC) = Oklch.lch(blue.rgb)
    val cyan = Oklch.color(blueL, blueC, CYAN_HUE)
    val magenta = Oklch.color(blueL, blueC, MAGENTA_HUE)
    val colours = listOf(error, green, amber, blue, magenta, cyan)
    val bright = colours.map(::brighter)
    return if (light) {
        listOf(text) + colours + listOf(muted, secondary) + bright + brighter(muted)
    } else {
        listOf(raised) + colours + listOf(secondary, muted) + bright + text
    }
}

private fun brighter(color: Color): Color = Oklch.lch(color.rgb).let { (l, c, h) -> Oklch.color((l + BRIGHT_STEP).coerceAtMost(1.0), c, h) }

private fun step(color: Color, from: Color, minimum: Double, darker: Boolean): Color =
    Oklch.lch(color.rgb).let { (_, chroma, hue) -> Oklch.stepFrom(chroma, hue, from, minimum, darker) }

private fun familyOf(kind: PaletteKind): IcodePalette = when (kind) {
    PaletteKind.Dark -> IcodePalette.Dark
    PaletteKind.Light -> IcodePalette.Light
    PaletteKind.Amoled -> IcodePalette.Amoled
}

private fun ratio(first: Color, second: Color): Double = AccentColorRules.contrastRatio(first.toArgb(), second.toArgb())

private val Color.rgb: Int get() = toArgb() and 0xFFFFFF

/** The six code colours, as the seed lists give them: keyword, type, string, number, comment, function. */
internal fun syntax(keyword: Long, type: Long, string: Long, number: Long, comment: Long, function: Long) =
    SyntaxSeeds(Color(keyword), Color(type), Color(string), Color(number), Color(comment), Color(function))

/** A project's sixteen terminal colours, given as `0xRRGGBB`. */
internal fun ansi(vararg rgb: Int): List<Color> {
    require(rgb.size == 16)
    return rgb.map { Color(0xFF000000.toInt() or it) }
}

/** WCAG AA for text: text, status colours, terminal colours. */
private const val TEXT_CONTRAST = 4.5
private const val CYAN_HUE = 200.0
private const val MAGENTA_HUE = 320.0
private const val BRIGHT_STEP = 0.08
