package dev.icode.mobile.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.ui.syntax.DEFAULT_COLOR_ID
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Every colour the editor draws besides the code, from the palette: its
 * background and gutter, the current line, the caret, selection and handles,
 * matched brackets, find matches, indent guides, the scroll bar and its line
 * bubble, the selection tool bar, problem underlines, the completion list
 * (EditorCompletionWindow) and a problem's tooltip, and the minimap. The ids
 * Sora keeps for features iCode does not show (signatures, inlay hints) are
 * left alone.
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
        // A language server's completions and a problem's tooltip (docs/lsp-plan.md §7), as the sheets are drawn.
        EditorColorScheme.COMPLETION_WND_BACKGROUND to surfaceElevated,
        EditorColorScheme.COMPLETION_WND_CORNER to outline,
        EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY to textPrimary,
        EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY to textSecondary,
        EditorColorScheme.COMPLETION_WND_TEXT_MATCHED to accent,
        // The chosen row as a menu's: a quiet lift of the text's own colour, so its accent letters still stand out.
        EditorColorScheme.COMPLETION_WND_ITEM_CURRENT to textPrimary.copy(alpha = 0.1f),
        EditorColorScheme.DIAGNOSTIC_TOOLTIP_BACKGROUND to surfaceElevated,
        EditorColorScheme.DIAGNOSTIC_TOOLTIP_BRIEF_MSG to textPrimary,
        EditorColorScheme.DIAGNOSTIC_TOOLTIP_DETAILED_MSG to textSecondary,
        EditorColorScheme.DIAGNOSTIC_TOOLTIP_ACTION to accent,
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
internal class PaletteColorScheme(registry: ThemeRegistry, val palette: IcodePalette) :
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
