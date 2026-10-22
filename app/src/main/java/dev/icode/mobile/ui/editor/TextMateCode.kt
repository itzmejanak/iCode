package dev.icode.mobile.ui.editor

import androidx.compose.ui.graphics.Color
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import java.time.Duration
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.EncodedTokenAttributes
import org.eclipse.tm4e.core.internal.theme.FontStyle

/** A run of code in one colour and style, as the editor draws it; [color] null for the text's own. */
internal data class CodeRun(val start: Int, val end: Int, val color: Color?, val bold: Boolean, val italic: Boolean)

/**
 * [code] coloured as the editor colours it (docs/markdown-rendering-plan.md §3.3): by the grammar
 * [scope] names and the palette's theme, through the tm4e the editor's own analyzer uses. Off the main
 * thread, once [TextMateAssets] has the grammars loaded; null when [scope] has no grammar.
 */
internal fun textMateRuns(scope: String, code: String): List<CodeRun>? {
    val grammar = GrammarRegistry.getInstance().findGrammar(scope) ?: return null
    val theme = ThemeRegistry.getInstance().currentThemeModel?.theme ?: return null
    val runs = mutableListOf<CodeRun>()
    var state: IStateStack? = null
    var offset = 0
    code.split('\n').forEach { line ->
        val result = grammar.tokenizeLine2(line, state, LINE_TIME)
        state = result.ruleStack
        val tokens = result.tokens
        for (at in tokens.indices step 2) {
            val start = tokens[at]
            val end = tokens.getOrNull(at + 2) ?: line.length
            if (end <= start) continue
            val metadata = tokens[at + 1]
            val style = EncodedTokenAttributes.getFontStyle(metadata)
            runs += CodeRun(
                offset + start,
                offset + end,
                theme.getColor(EncodedTokenAttributes.getForeground(metadata))?.let(::themeColor),
                bold = style and FontStyle.Bold != 0,
                italic = style and FontStyle.Italic != 0,
            )
        }
        offset += line.length + 1
    }
    return runs
}

/** A theme colour, `#RRGGBB` or `#RRGGBBAA`; null for the default, [DEFAULT_COLOR_SENTINEL], drawn in the text's colour. */
private fun themeColor(hex: String): Color? {
    if (hex.equals(DEFAULT_COLOR_SENTINEL, ignoreCase = true) || !hex.startsWith("#")) return null
    val digits = hex.drop(1).toLongOrNull(16) ?: return null
    val rgba = when (hex.length) {
        7 -> (digits shl 8) or 0xFF
        9 -> digits
        else -> return null
    }
    return Color(red = (rgba shr 24 and 0xFF).toInt(), green = (rgba shr 16 and 0xFF).toInt(), blue = (rgba shr 8 and 0xFF).toInt(), alpha = (rgba and 0xFF).toInt())
}

/** A line's tokenizing gives up after this, as the editor's analyzer's (Sora's TextMateAnalyzer). */
private val LINE_TIME = Duration.ofSeconds(2)
