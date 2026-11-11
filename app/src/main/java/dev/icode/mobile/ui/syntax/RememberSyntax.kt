package dev.icode.mobile.ui.syntax

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.LocalIcodePalette
import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.designsystem.syntaxColor

/**
 * [text] coloured as catalog language [language] (docs/syntax-highlighting-plan.md D2, D5), the one way
 * every read-only view colours code: tokenized on the read-only thread ([SyntaxEngine.tokens]) once per
 * language and text, and coloured from the current palette when composed, so a palette change recolours
 * without tokenizing again (A3). Plain while its tokens are made, and for a null [language], one whose
 * grammar failed, or a text past [SyntaxLimits.BLOCK_CHARS].
 */
@Composable
internal fun rememberSyntax(language: String?, text: String): AnnotatedString {
    val context = LocalContext.current
    val palette = LocalIcodePalette.current
    // The spans with the text they are for: until a new text's come, the old ones do not apply to it.
    val made by produceState<Pair<String, List<SyntaxSpan>>?>(null, language, text) {
        value = language?.let { SyntaxEngine.tokens(context, it, text) }?.let { text to it }
    }
    val spans = made?.takeIf { it.first == text }?.second.orEmpty()
    return remember(text, spans, palette) { syntaxString(text, spans, palette) }
}

/** [text] with [spans] drawn in [palette]'s colours; Plain in the text's own colour. */
internal fun syntaxString(text: String, spans: List<SyntaxSpan>, palette: IcodePalette): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { span ->
        val style = span.style
        val decorations = listOfNotNull(TextDecoration.Underline.takeIf { style.underline }, TextDecoration.LineThrough.takeIf { style.strikethrough })
        addStyle(
            SpanStyle(
                color = if (style.role == SyntaxRole.Plain) Color.Unspecified else palette.syntaxColor(style.role),
                fontWeight = if (style.bold) FontWeight.Bold else null,
                fontStyle = if (style.italic) FontStyle.Italic else null,
                textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
            ),
            span.start,
            span.end,
        )
    }
}
