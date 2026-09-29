package dev.icode.mobile.ui.editor

import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.designsystem.syntaxColor
import dev.icode.mobile.domain.AccentColorRules
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorThemeTest {
    private val palettes = listOf(IcodePalette.Dark, IcodePalette.Light, IcodePalette.Amoled)

    @Test fun `each theme is the palette's editor, with one rule per role`() {
        palettes.forEach { palette ->
            val theme = editorTheme(palette)
            val rules = theme.getJSONArray("settings")
            val editor = rules.getJSONObject(0).getJSONObject("settings")
            assertEquals(hex(palette.backgroundDeep.toArgb()), editor.getString("background"))
            assertEquals(DEFAULT_COLOR_SENTINEL, editor.getString("foreground"))
            assertTrue("no role draws with the sentinel", SyntaxRole.entries.none { hex(palette.syntaxColor(it).toArgb()) == DEFAULT_COLOR_SENTINEL })
            assertEquals(hex(palette.editorLineHighlight.toArgb()), editor.getString("lineHighlight"))
            val byColour = (1 until rules.length()).map { rules.getJSONObject(it) }
            assertEquals(SyntaxRole.entries.size, byColour.size)
            SyntaxRole.entries.forEach { role ->
                assertTrue("${palette.kind} $role", byColour.any { rule ->
                    rule.getJSONObject("settings").getString("foreground") == hex(palette.syntaxColor(role).toArgb())
                })
            }
        }
    }

    @Test fun `styled roles carry their style`() {
        val rules = editorTheme(IcodePalette.Dark).getJSONArray("settings")
        val styles = (1 until rules.length()).map { rules.getJSONObject(it) }
            .associate { it.getString("scope") to it.getJSONObject("settings").optString("fontStyle") }
        assertEquals("bold", styles.entries.first { "markup.heading" in it.key.split(", ") }.value)
        assertEquals("italic", styles.entries.first { "markup.italic" in it.key.split(", ") }.value)
        assertEquals("underline", styles.entries.first { "markup.underline" in it.key.split(", ") }.value)
        assertEquals("", styles.entries.first { "comment" in it.key.split(", ") }.value)
    }

    @Test fun `the editor's TextMate engine reads every theme`() {
        palettes.forEach { palette ->
            val json = editorTheme(palette).toString()
            val model = ThemeModel(IThemeSource.fromString(IThemeSource.ContentType.JSON, json), themeName(palette.kind))
            model.load()
            val colours = model.theme.colorMap.map { it.uppercase() }.toSet()
            SyntaxRole.entries.forEach { role ->
                assertTrue("${palette.kind} $role", hex(palette.syntaxColor(role).toArgb()) in colours)
            }
        }
    }

    private fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

    @Suppress("unused") private fun pretty(theme: JSONObject) = theme.toString(2)

    @Test fun `the editor's own colours come from the palette and read`() {
        palettes.forEach { palette ->
            val colors = editorColors(palette)
            assertEquals(palette.backgroundDeep.toArgb(), colors.getValue(EditorColorScheme.WHOLE_BACKGROUND))
            assertEquals(palette.textPrimary.toArgb(), colors.getValue(DEFAULT_COLOR_ID))
            // Gutter and minimap are the editor's own background, not a block beside it.
            assertEquals(palette.backgroundDeep.toArgb(), colors.getValue(EditorColorScheme.LINE_NUMBER_BACKGROUND))
            assertEquals(palette.backgroundDeep.toArgb(), colors.getValue(EditorColorScheme.MINIMAP_BACKGROUND))
            // The current line is neither the editor nor the tab bar around it.
            val line = colors.getValue(EditorColorScheme.CURRENT_LINE)
            assertTrue(line != palette.surface.toArgb() && line != palette.backgroundDeep.toArgb())
            listOf(EditorColorScheme.LINE_NUMBER, EditorColorScheme.LINE_NUMBER_CURRENT).forEach { id ->
                val ratio = AccentColorRules.contrastRatio(colors.getValue(id), palette.backgroundDeep.toArgb())
                assertTrue("${palette.kind} gutter $id %.2f".format(ratio), ratio >= 3.0)
            }
        }
    }
}
