package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.Color

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
 * The editor's colour for [role] on this palette: text and errors are the
 * palette's own; every other role takes one of the theme's [SyntaxHues]
 * (`IcodeColors.kt`). Every colour is at least 4.5 : 1 on the editor
 * background and on the current line (SyntaxColorsTest).
 */
fun IcodePalette.syntaxColor(role: SyntaxRole): Color {
    val hues = if (kind == PaletteKind.Light) LightSyntaxHues else DarkSyntaxHues
    return when (role) {
        SyntaxRole.Plain, SyntaxRole.Variable, SyntaxRole.Strong, SyntaxRole.Emphasis, SyntaxRole.Strikethrough -> textPrimary
        SyntaxRole.Invalid, SyntaxRole.Deleted -> error
        SyntaxRole.Comment, SyntaxRole.TagPunctuation, SyntaxRole.Quote -> hues.comment
        SyntaxRole.Keyword, SyntaxRole.Tag, SyntaxRole.Heading -> hues.keyword
        SyntaxRole.Control -> hues.control
        SyntaxRole.String, SyntaxRole.Inserted -> hues.string
        SyntaxRole.Number, SyntaxRole.Constant -> hues.number
        SyntaxRole.Function, SyntaxRole.ListMarker, SyntaxRole.Changed -> hues.function
        SyntaxRole.Type, SyntaxRole.Escape -> hues.type
        SyntaxRole.Attribute, SyntaxRole.Link -> hues.attribute
        SyntaxRole.Regex -> hues.regex
    }
}

/** One colour per kind of token, for one theme. */
internal class SyntaxHues(
    val comment: Color,
    val keyword: Color,
    val control: Color,
    val string: Color,
    val number: Color,
    val function: Color,
    val type: Color,
    val attribute: Color,
    val regex: Color,
)
