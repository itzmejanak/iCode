package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.ConcurrentHashMap

/** What a piece of code is, as the editor colours it; styled roles also carry their font style. */
enum class SyntaxRole(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
) {
    Plain, Variable, Comment, Keyword, Control, String, Number, Constant, Function, Type, Attribute,
    Tag, TagPunctuation, Escape, Regex, Invalid,
    Heading(bold = true), Strong(bold = true), Emphasis(italic = true), Link(underline = true),
    Strikethrough(strikethrough = true), ListMarker, Quote, Inserted, Deleted, Changed,
}

/**
 * The editor's colour for [role] on this palette. Text and errors are the
 * palette's own; each other kind of token has one hue — keyword 45°, control
 * 325°, string 155°, number 285°, function 90°, type 195°, attribute 240°,
 * regex 0° — drawn by [hueColor], so a theme's colours are equally readable
 * and differ only in what they mean; comments a quiet grey. Every colour is at
 * least 4.5 : 1 on the editor background and on the current line: where a
 * theme's editor is drawn on other tones (Light without border lines) and one
 * would not be, only its lightness moves until it is (SyntaxColorsTest).
 */
fun IcodePalette.syntaxColor(role: SyntaxRole): Color = when (role) {
    SyntaxRole.Plain, SyntaxRole.Variable, SyntaxRole.Strong, SyntaxRole.Emphasis, SyntaxRole.Strikethrough -> textPrimary
    SyntaxRole.Invalid, SyntaxRole.Deleted -> error
    SyntaxRole.Comment, SyntaxRole.TagPunctuation, SyntaxRole.Quote ->
        onEditor(if (kind == PaletteKind.Light) Oklch.color(0.52, COMMENT_CHROMA, COMMENT_HUE) else Oklch.color(0.68, COMMENT_CHROMA, COMMENT_HUE))
    SyntaxRole.Keyword, SyntaxRole.Tag, SyntaxRole.Heading -> onEditor(hueColor(45.0))
    SyntaxRole.Control -> onEditor(hueColor(325.0))
    SyntaxRole.String, SyntaxRole.Inserted -> onEditor(hueColor(155.0))
    SyntaxRole.Number, SyntaxRole.Constant -> onEditor(hueColor(285.0))
    SyntaxRole.Function, SyntaxRole.ListMarker, SyntaxRole.Changed -> onEditor(hueColor(90.0))
    SyntaxRole.Type, SyntaxRole.Escape -> onEditor(hueColor(195.0))
    SyntaxRole.Attribute, SyntaxRole.Link -> onEditor(hueColor(240.0))
    SyntaxRole.Regex -> onEditor(hueColor(0.0))
}

/** [color] as it is where it reads 4.5 : 1 on the editor and its current line; else its lightness moved until it does. */
private fun IcodePalette.onEditor(color: Color): Color =
    Oklch.readable(color.toArgb() and 0xFFFFFF, listOf(backgroundDeep, editorLineHighlight), CODE_CONTRAST, darker = kind == PaletteKind.Light)

/**
 * [hue] (OKLCH degrees) as this theme draws a hue on its own background:
 * lightness 0.78 on the dark themes and 0.47 on Light, with as much chroma,
 * up to 0.14, as sRGB allows: the one rule for the syntax colours' hues.
 */
fun IcodePalette.hueColor(hue: Double): Color = hueColors.getOrPut(kind to hue) {
    if (kind == PaletteKind.Light) Oklch.color(0.47, HUE_CHROMA, hue) else Oklch.color(0.78, HUE_CHROMA, hue)
}

private val hueColors = ConcurrentHashMap<Pair<PaletteKind, Double>, Color>()
private const val HUE_CHROMA = 0.14
private const val COMMENT_CHROMA = 0.025
private const val COMMENT_HUE = 145.0

/** WCAG AA for text. */
private const val CODE_CONTRAST = 4.5
