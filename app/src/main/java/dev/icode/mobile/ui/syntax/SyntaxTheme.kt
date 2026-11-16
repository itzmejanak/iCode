package dev.icode.mobile.ui.syntax

import androidx.annotation.MainThread
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.designsystem.syntaxColor
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * Each palette's TextMate theme (docs/editor-and-language-services.md), built from [syntaxScopes] the
 * first time it is asked for and kept in Sora's theme registry. Main thread.
 */
internal object SyntaxTheme {
    /** [palette]'s theme, built and registered the first time, without making it current. */
    @MainThread fun of(palette: IcodePalette): ThemeModel {
        val registry = ThemeRegistry.getInstance()
        val name = themeName(palette)
        val found: ThemeModel? = registry.findThemeByThemeName(name)
        if (found != null) return found
        val source = IThemeSource.fromString(IThemeSource.ContentType.JSON, editorTheme(palette).toString())
        val model = ThemeModel(source, name).apply { isDark = palette.kind != PaletteKind.Light }
        registry.loadTheme(model, false)
        return model
    }

    /** Makes [palette]'s theme the one every editor draws with; only when it is not already. */
    @MainThread fun use(palette: IcodePalette) {
        val registry = ThemeRegistry.getInstance()
        val model = of(palette)
        if (registry.currentThemeModel?.name == model.name) return
        check(registry.setTheme(model.name)) { "TextMate theme ${model.name} is unavailable" }
    }
}

/**
 * The editor's colour scheme for [palette]: its code in the current TextMate theme ([SyntaxTheme.use]
 * first), and the editor's other [colours] (Sora's colour ids to ARGB) over it. Sora applies the theme
 * again, clearing every colour set on the scheme, when the scheme is attached to an editor and when the
 * theme changes, so [colours] go on again after each time; set once beforehand, they were silently lost.
 * Main thread.
 */
internal class PaletteColorScheme(val palette: IcodePalette, private val colours: Map<Int, Int>) :
    TextMateColorScheme(ThemeRegistry.getInstance(), ThemeRegistry.getInstance().currentThemeModel) {
    init {
        applyColours()
    }

    override fun setTheme(themeModel: ThemeModel) {
        super.setTheme(themeModel)
        // The super constructor calls this before `colours` is assigned.
        @Suppress("SENSELESS_COMPARISON")
        if (colours != null) applyColours()
    }

    private fun applyColours() = colours.forEach { (id, color) -> setColor(id, color) }
}

/**
 * The editor's TextMate theme for a palette, made from [syntaxScopes] and the
 * palette's syntax colours: there is no theme file to keep in step. The
 * background and current line are the palette's; the caret, selection,
 * current line number and matched brackets follow the accent, set by
 * `CodeEditorSession.applyPalette` over this.
 *
 * The theme's default colour is [DEFAULT_COLOR_SENTINEL], not the palette's
 * text: tm4e numbers the default colour 0, and 0 in a rule means "not set", so
 * a rule drawing plain text (`=` inside `keyword`, `${…}` inside a string)
 * could never win over the broader rule around it. The editor draws colour 0
 * as the palette's text ([DEFAULT_COLOR_ID]). Each role's colour is [colour]'s, the palette's own
 * but in tests.
 */
internal fun editorTheme(palette: IcodePalette, colour: (SyntaxRole) -> Color = palette::syntaxColor): JSONObject {
    val rules = JSONArray().put(
        JSONObject().put(
            "settings",
            JSONObject()
                .put("background", hex(palette.backgroundDeep))
                .put("foreground", DEFAULT_COLOR_SENTINEL)
                .put("lineHighlight", hex(palette.editorLineHighlight)),
        ),
    )
    syntaxScopes.groupBy({ it.first }, { it.second }).forEach { (role, selectors) ->
        val settings = JSONObject().put("foreground", hex(colour(role)))
        fontStyle(role)?.let { settings.put("fontStyle", it) }
        rules.put(JSONObject().put("scope", selectors.flatten().joinToString(", ")).put("settings", settings))
    }
    return JSONObject().put("name", themeName(palette)).put("settings", rules)
}

/**
 * [palette]'s TextMate theme by every colour it holds (the editor, its current line and each role's),
 * so two palettes share a cached theme only when they draw code alike: a variant's own colours, or the
 * same theme on other tones (without border lines), are themes of their own (docs/design-and-navigation.md).
 */
internal fun themeName(palette: IcodePalette): String {
    val colours = listOf(palette.backgroundDeep, palette.editorLineHighlight) + SyntaxRole.entries.map { palette.syntaxColor(it) }
    return "icode-${palette.kind.name.lowercase()}-" + Sha256.hex(colours.joinToString(",") { hex(it) }).take(NAME_DIGITS)
}

/** 64 bits of the digest name a theme: far past any count of palettes the app draws. */
private const val NAME_DIGITS = 16

/** A colour no role draws with (tested), standing for "the text colour" in the theme. */
internal const val DEFAULT_COLOR_SENTINEL = "#010101"

/** The editor's colour id for tm4e's colour 0: Sora maps tm4e colour `n` to id `255 + n`. */
internal const val DEFAULT_COLOR_ID = 255

private fun fontStyle(role: SyntaxRole): String? = listOfNotNull(
    "bold".takeIf { role.bold },
    "italic".takeIf { role.italic },
    "underline".takeIf { role.underline },
    "strikethrough".takeIf { role.strikethrough },
).joinToString(" ").ifEmpty { null }

private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)
