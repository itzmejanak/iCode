package dev.icode.mobile.ui.editor

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.designsystem.syntaxColor
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * The editor's TextMate theme for a palette, made from [syntaxScopes] and the
 * palette's syntax colours — there is no theme file to keep in step. The
 * background, text and current line are the palette's; the caret, selection,
 * current line number and matched brackets follow the accent, set by
 * [CodeEditorSession.applyPalette] over this.
 */
internal fun editorTheme(palette: IcodePalette): JSONObject {
    val rules = JSONArray().put(
        JSONObject().put(
            "settings",
            JSONObject()
                .put("background", hex(palette.backgroundDeep))
                .put("foreground", hex(palette.textPrimary))
                .put("lineHighlight", hex(palette.surface)),
        ),
    )
    syntaxScopes.groupBy({ it.first }, { it.second }).forEach { (role, selectors) ->
        val settings = JSONObject().put("foreground", hex(palette.syntaxColor(role)))
        fontStyle(role)?.let { settings.put("fontStyle", it) }
        rules.put(JSONObject().put("scope", selectors.flatten().joinToString(", ")).put("settings", settings))
    }
    return JSONObject().put("name", themeName(palette.kind)).put("settings", rules)
}

internal fun themeName(kind: PaletteKind): String = "icode-${kind.name.lowercase()}"

private fun fontStyle(role: SyntaxRole): String? = listOfNotNull(
    "bold".takeIf { role.bold },
    "italic".takeIf { role.italic },
    "underline".takeIf { role.underline },
    "strikethrough".takeIf { role.strikethrough },
).joinToString(" ").ifEmpty { null }

private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)

/** The shipped grammars, loaded once, and each palette kind's theme, built the first time it is shown. */
internal object TextMateAssets {
    @Volatile private var grammarsLoaded = false

    @Synchronized fun setTheme(context: Context, palette: IcodePalette) {
        val registry = ThemeRegistry.getInstance()
        val name = themeName(palette.kind)
        if (registry.findThemeByThemeName(name) == null) {
            val source = IThemeSource.fromString(IThemeSource.ContentType.JSON, editorTheme(palette).toString())
            registry.loadTheme(ThemeModel(source, name).apply { isDark = palette.kind != PaletteKind.Light }, true)
        }
        check(registry.setTheme(name)) { "TextMate theme $name is unavailable" }
        // Grammars resolve their colours against a current theme, so they come after one.
        if (!grammarsLoaded) {
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.assets))
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            grammarsLoaded = true
        }
    }
}
