package dev.icode.mobile.ui.editor

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.designsystem.syntaxColor
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * The editor's TextMate theme for a palette, made from [syntaxScopes] and the
 * palette's syntax colours — there is no theme file to keep in step. The
 * background and current line are the palette's; the caret, selection,
 * current line number and matched brackets follow the accent, set by
 * [CodeEditorSession.applyPalette] over this.
 *
 * The theme's default colour is [DEFAULT_COLOR_SENTINEL], not the palette's
 * text: tm4e numbers the default colour 0, and 0 in a rule means "not set", so
 * a rule drawing plain text — `=` inside `keyword`, `${…}` inside a string —
 * could never win over the broader rule around it. The editor draws colour 0
 * as the palette's text ([DEFAULT_COLOR_ID]).
 */
internal fun editorTheme(palette: IcodePalette): JSONObject {
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
        val settings = JSONObject().put("foreground", hex(palette.syntaxColor(role)))
        fontStyle(role)?.let { settings.put("fontStyle", it) }
        rules.put(JSONObject().put("scope", selectors.flatten().joinToString(", ")).put("settings", settings))
    }
    return JSONObject().put("name", themeName(palette.kind)).put("settings", rules)
}

internal fun themeName(kind: PaletteKind): String = "icode-${kind.name.lowercase()}"

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

/**
 * Every colour the editor draws besides the code, from the palette: its
 * background and gutter, the current line, the caret, selection and handles,
 * matched brackets, find matches, indent guides, the scroll bar and its line
 * bubble, the selection tool bar, problem underlines and the minimap. The ids
 * Sora keeps for features iCode does not show (completion, hover, signatures,
 * inlay hints) are left alone.
 */
internal fun editorColors(palette: IcodePalette): Map<Int, Int> = with(palette) {
    val transparent = Color.Transparent
    mapOf(
        EditorColorScheme.WHOLE_BACKGROUND to backgroundDeep,
        EditorColorScheme.TEXT_NORMAL to textPrimary,
        DEFAULT_COLOR_ID to textPrimary,
        EditorColorScheme.LINE_NUMBER_BACKGROUND to backgroundDeep,
        EditorColorScheme.LINE_NUMBER to textMuted,
        EditorColorScheme.LINE_NUMBER_CURRENT to accent,
        EditorColorScheme.LINE_DIVIDER to outline,
        EditorColorScheme.CURRENT_LINE to editorLineHighlight,
        EditorColorScheme.CURRENT_ROW_BORDER to transparent,
        EditorColorScheme.SELECTION_INSERT to accent,
        EditorColorScheme.SELECTION_HANDLE to accent,
        EditorColorScheme.SELECTED_TEXT_BACKGROUND to accent.copy(alpha = 0.28f),
        EditorColorScheme.SELECTED_TEXT_BORDER to transparent,
        EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND to accent,
        EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND to transparent,
        EditorColorScheme.HIGHLIGHTED_DELIMITERS_BORDER to accent.copy(alpha = 0.6f),
        EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE to transparent,
        EditorColorScheme.MATCHED_TEXT_BACKGROUND to amber.copy(alpha = 0.3f),
        EditorColorScheme.MATCHED_TEXT_BORDER to amber,
        EditorColorScheme.BLOCK_LINE to outline,
        EditorColorScheme.BLOCK_LINE_CURRENT to textMuted,
        EditorColorScheme.SIDE_BLOCK_LINE to outline,
        EditorColorScheme.NON_PRINTABLE_CHAR to textMuted,
        EditorColorScheme.HARD_WRAP_MARKER to outline,
        EditorColorScheme.SCROLL_BAR_THUMB to textMuted.copy(alpha = 0.6f),
        EditorColorScheme.SCROLL_BAR_THUMB_PRESSED to accent,
        EditorColorScheme.SCROLL_BAR_TRACK to transparent,
        EditorColorScheme.LINE_NUMBER_PANEL to surfaceElevated,
        EditorColorScheme.LINE_NUMBER_PANEL_TEXT to textPrimary,
        EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND to surfaceElevated,
        EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR to textPrimary,
        EditorColorScheme.PROBLEM_ERROR to error,
        EditorColorScheme.PROBLEM_WARNING to amber,
        EditorColorScheme.PROBLEM_TYPO to blue,
        // The minimap is part of the editor, as in VS Code: its background, and a quiet view window.
        EditorColorScheme.MINIMAP_BACKGROUND to backgroundDeep,
        EditorColorScheme.MINIMAP_VIEWPORT to textPrimary.copy(alpha = 0.08f),
        EditorColorScheme.MINIMAP_VIEWPORT_BORDER to outline,
    ).mapValues { it.value.toArgb() }
}

/**
 * The editor's colour scheme for a palette. Sora re-applies the TextMate theme —
 * clearing every colour set on the scheme — when the scheme is attached to an
 * editor and when the theme changes, so the palette's colours go on again after
 * each time; set once beforehand, they were silently lost.
 */
internal class PaletteColorScheme(registry: ThemeRegistry, private val palette: IcodePalette) :
    TextMateColorScheme(registry, registry.currentThemeModel) {
    init {
        applyPaletteColors()
    }

    override fun setTheme(themeModel: ThemeModel) {
        super.setTheme(themeModel)
        // The super constructor calls this before `palette` is assigned.
        @Suppress("SENSELESS_COMPARISON")
        if (palette != null) applyPaletteColors()
    }

    private fun applyPaletteColors() = editorColors(palette).forEach { (id, color) -> setColor(id, color) }
}

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
