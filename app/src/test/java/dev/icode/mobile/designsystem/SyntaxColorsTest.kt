package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.domain.AccentColorRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxColorsTest {
    private val palettes = everyPalette

    /** Roles whose tokens must look different from plain text; the rest carry only a font style. */
    private val coloured = SyntaxRole.entries - setOf(
        SyntaxRole.Plain, SyntaxRole.Variable, SyntaxRole.Strong, SyntaxRole.Emphasis, SyntaxRole.Strikethrough,
    )

    @Test fun `every role reads at 4 point 5 to 1 on the editor and on its current line, on every theme`() {
        palettes.forEach { palette ->
            SyntaxRole.entries.forEach { role ->
                val colour = palette.syntaxColor(role).toArgb()
                listOf(palette.backgroundDeep, palette.editorLineHighlight).forEach { behind ->
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
        // A variant's own colours are its theme's: it draws control keywords as keywords, attributes as
        // types, as its editor does (docs/theme-variants-plan.md D5). The six it sets apart stay apart.
        val variantMain = listOf(
            SyntaxRole.Comment, SyntaxRole.Keyword, SyntaxRole.String, SyntaxRole.Number, SyntaxRole.Function, SyntaxRole.Type,
        )
        palettes.forEach { palette ->
            coloured.forEach { role ->
                assertNotEquals("${palette.kind} $role", palette.textPrimary, palette.syntaxColor(role))
            }
            val apart = if (palette.syntax == null) main else variantMain
            assertEquals("${palette.kind}", apart.size, apart.map { palette.syntaxColor(it) }.toSet().size)
        }
    }

    @Test fun `the accent is the user's, the code colours are the theme's`() {
        val violet = IcodePalette.Dark.withAccent(0xFFB67CFF.toInt())
        SyntaxRole.entries.forEach { role -> assertEquals(IcodePalette.Dark.syntaxColor(role), violet.syntaxColor(role)) }
    }

    /** The values E3 shipped, derived by the same rule in Python; deriving them here must not move one. */
    @Test fun `the rule gives exactly the colours it was chosen with`() {
        val expected = mapOf(
            SyntaxRole.Comment to (0x8F9D8F to 0x606D60), SyntaxRole.Keyword to (0xFF9A6B to 0x953B01),
            SyntaxRole.Control to (0xE497E8 to 0x803985), SyntaxRole.String to (0x63D18F to 0x016D3C),
            SyntaxRole.Number to (0xB0ADFF to 0x554BA5), SyntaxRole.Function to (0xDAB33A to 0x705800),
            SyntaxRole.Type to (0x0CD1D1 to 0x016869), SyntaxRole.Attribute to (0x65C1FE to 0x056190),
            SyntaxRole.Regex to (0xFF8FB4 to 0x953058),
        )
        expected.forEach { (role, pair) ->
            assertEquals("Dark $role", pair.first, IcodePalette.Dark.syntaxColor(role).toArgb() and 0xFFFFFF)
            assertEquals("Amoled $role", pair.first, IcodePalette.Amoled.syntaxColor(role).toArgb() and 0xFFFFFF)
            assertEquals("Light $role", pair.second, IcodePalette.Light.syntaxColor(role).toArgb() and 0xFFFFFF)
        }
    }
}
