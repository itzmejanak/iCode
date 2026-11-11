package dev.icode.mobile.ui.syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.ui.editor.EditorColours
import dev.icode.mobile.ui.editor.ShippedGrammars
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.internal.grammar.ScopeStack
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.EncodedTokenAttributes
import org.eclipse.tm4e.core.internal.theme.FontStyle
import org.eclipse.tm4e.core.internal.theme.StyleAttributes
import org.eclipse.tm4e.core.internal.theme.Theme
import org.eclipse.tm4e.core.internal.theme.raw.RawThemeReader
import org.eclipse.tm4e.core.registry.IThemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SyntaxRoles] against the editor's own TextMate theme (A4): the theme is [editorTheme] with a colour of
 * its own for each role, so the colour tm4e gives a token names the role the editor draws it in.
 */
class SyntaxRolesTest {
    private val roles = SyntaxRoles.shipped
    private val json = editorTheme(IcodePalette.Dark) { Color(ROLE_COLOURS + it.ordinal) }.toString()
    private val theme = Theme.createFromRawTheme(RawThemeReader.readTheme(IThemeSource.fromString(IThemeSource.ContentType.JSON, json)), null)

    @Test fun `every scope of every shipped grammar takes the role the editor's theme gives it`() {
        val stacks = ShippedGrammars.all.flatMap { grammar -> grammar.scopes.map { listOf(grammar.scope, it) } }
        assertTrue(stacks.size > 2000)
        assertEquals(emptyList<String>(), stacks.mapNotNull(::disagreement))
    }

    @Test fun `selectors with parents win only inside those parents`() {
        val spaced = syntaxScopes.flatMap { it.second }.filter { ' ' in it }
        assertTrue(spaced.isNotEmpty())
        val stacks = spaced.flatMap { selector ->
            val parts = selector.split(' ')
            listOf(
                listOf("source.x") + parts,
                listOf("source.x") + parts.dropLast(1) + "meta.between" + parts.last(),
                listOf("source.x", parts.last()),
                listOf("source.x") + parts.map { "$it.more" },
            )
        }
        assertEquals(emptyList<String>(), stacks.mapNotNull(::disagreement))
    }

    @Test fun `colour and font style resolve apart`() {
        // A link's text in a heading: the link text's colour, the heading's weight.
        val heading = listOf("text.html.markdown", "markup.heading.markdown", "string.other.link.title.markdown")
        assertEquals(SyntaxStyle(SyntaxRole.String, bold = true), roles.style(heading))
        // A style replaces the one around it whole, as in tm4e: a link in bold text is underlined, not bold.
        val link = listOf("text.html.markdown", "markup.bold.markdown", "markup.underline.link.markdown")
        assertEquals(SyntaxStyle(SyntaxRole.Link, underline = true), roles.style(link))
        assertEquals(SyntaxStyle(SyntaxRole.Plain), roles.style(listOf("source.kotlin")))
        assertEquals(null, disagreement(heading))
        assertEquals(null, disagreement(link))
    }

    /** A4: each sample line of each shipped language, every character, as the editor's analyzer path draws it. */
    @Test fun `the editor and tokens() draw every sample line alike`() {
        val registry = ShippedGrammars.registry()
        registry.setTheme(IThemeSource.fromString(IThemeSource.ContentType.JSON, json))
        val wrong = EditorColours.SAMPLES.flatMap { sample ->
            val scope = ShippedGrammars.catalog.byId(checkNotNull(FileTypes.of(sample.file).language) { sample.file })!!.scope
            val grammar = ShippedGrammars.load(registry, scope)
            val drawn = mutableListOf<SyntaxStyle>()
            var state: IStateStack? = null
            sample.lines.forEach { line ->
                val result = grammar.tokenizeLine2(line, state, null)
                state = result.ruleStack
                val tokens = result.tokens
                for (column in line.indices) {
                    val at = (tokens.indices step 2).last { tokens[it] <= column }
                    drawn += styleOf(EncodedTokenAttributes.getForeground(tokens[at + 1]), EncodedTokenAttributes.getFontStyle(tokens[at + 1]), registry.colorMap)
                }
                drawn += SyntaxStyle(SyntaxRole.Plain)
            }
            val text = sample.lines.joinToString("\n")
            val spans = syntaxSpans(grammar, text)
            text.indices.mapNotNull { index ->
                val span = spans.firstOrNull { index >= it.start && index < it.end }?.style ?: SyntaxStyle(SyntaxRole.Plain)
                if (text[index] == '\n' || span == drawn[index]) null else "${sample.file} at $index '${text[index]}': tokens() $span, editor ${drawn[index]}"
            }
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** [scopes] where [roles] and tm4e's theme, folded scope by scope as tm4e does, differ; null if alike. */
    private fun disagreement(scopes: List<String>): String? {
        var foreground = 0
        var fontStyle = FontStyle.NotSet
        scopes.indices.forEach { level ->
            val matched: StyleAttributes? = theme.match(ScopeStack.from(scopes.take(level + 1)))
            if (matched != null) {
                if (matched.foregroundId != 0) foreground = matched.foregroundId
                if (matched.fontStyle != FontStyle.NotSet) fontStyle = matched.fontStyle
            }
        }
        val editor = styleOf(foreground, fontStyle, theme.colorMap)
        val resolved = roles.style(scopes)
        return if (editor == resolved) null else "${scopes.joinToString(" ")}: theme $editor, SyntaxRoles $resolved"
    }

    private fun styleOf(foreground: Int, fontStyle: Int, colours: List<String>): SyntaxStyle {
        val role = colours.getOrNull(foreground)?.let { hex -> SyntaxRole.entries.firstOrNull { hex(it).equals(hex, ignoreCase = true) } }
        val set = fontStyle != FontStyle.NotSet
        return SyntaxStyle(
            role ?: SyntaxRole.Plain,
            bold = set && fontStyle and FontStyle.Bold != 0,
            italic = set && fontStyle and FontStyle.Italic != 0,
            underline = set && fontStyle and FontStyle.Underline != 0,
            strikethrough = set && fontStyle and FontStyle.Strikethrough != 0,
        )
    }

    private fun hex(role: SyntaxRole) = "#%06X".format(Color(ROLE_COLOURS + role.ordinal).toArgb() and 0xFFFFFF)

    private companion object {
        /** Opaque #2030xx, one per role by its ordinal; never the default colour, #010101. */
        const val ROLE_COLOURS = 0xFF203000
    }
}
