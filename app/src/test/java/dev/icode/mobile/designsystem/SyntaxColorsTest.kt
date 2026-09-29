package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.domain.AccentColorRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxColorsTest {
    private val palettes = listOf(IcodePalette.Dark, IcodePalette.Light, IcodePalette.Amoled)

    /** Roles whose tokens must look different from plain text; the rest carry only a font style. */
    private val coloured = SyntaxRole.entries - setOf(
        SyntaxRole.Plain, SyntaxRole.Variable, SyntaxRole.Strong, SyntaxRole.Emphasis, SyntaxRole.Strikethrough,
    )

    @Test fun `every role reads at 4 point 5 to 1 on the background and on the current line, on every theme`() {
        palettes.forEach { palette ->
            SyntaxRole.entries.forEach { role ->
                val colour = palette.syntaxColor(role).toArgb()
                listOf(palette.backgroundDeep, palette.surface).forEach { behind ->
                    val ratio = AccentColorRules.contrastRatio(colour, behind.toArgb())
                    assertTrue("${palette.kind} $role %.2f".format(ratio), ratio >= 4.5)
                }
            }
        }
    }

    @Test fun `code is coloured, and the main kinds of token differ from each other`() {
        val main = listOf(
            SyntaxRole.Comment, SyntaxRole.Keyword, SyntaxRole.Control, SyntaxRole.String, SyntaxRole.Number,
            SyntaxRole.Function, SyntaxRole.Type, SyntaxRole.Attribute, SyntaxRole.Regex, SyntaxRole.Invalid,
        )
        palettes.forEach { palette ->
            coloured.forEach { role ->
                assertNotEquals("${palette.kind} $role", palette.textPrimary, palette.syntaxColor(role))
            }
            assertEquals("${palette.kind}", main.size, main.map { palette.syntaxColor(it) }.toSet().size)
        }
    }

    @Test fun `the accent is the user's, the code colours are the theme's`() {
        val violet = IcodePalette.Dark.withAccent(0xFFB67CFF.toInt())
        SyntaxRole.entries.forEach { role -> assertEquals(IcodePalette.Dark.syntaxColor(role), violet.syntaxColor(role)) }
    }
}
