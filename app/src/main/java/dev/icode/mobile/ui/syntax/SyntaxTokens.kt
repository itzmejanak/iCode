package dev.icode.mobile.ui.syntax

import dev.icode.mobile.designsystem.SyntaxRole
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.grammar.IStateStack

/**
 * A run of code drawn one way (docs/editor-and-language-services.md): characters [start] to [end] of the
 * text, as [style] says. A role, not a colour: the colour is the palette's when drawn, so a palette
 * change recolours without tokenizing again.
 */
internal data class SyntaxSpan(val start: Int, val end: Int, val style: SyntaxStyle)

/**
 * [text]'s spans by [grammar], line by line with each line's state carried to the next, through tm4e's
 * public API: each token's scopes, resolved by [roles]. Plain text with no font style gets no span, and
 * neighbours drawn alike are one span. [grammar] is the read-only thread's own (SyntaxEngine's `syntax-tokens`),
 * a [LimitedGrammar], so the line limits apply.
 */
internal fun syntaxSpans(grammar: IGrammar, text: String, roles: SyntaxRoles = SyntaxRoles.shipped): List<SyntaxSpan> {
    val spans = mutableListOf<SyntaxSpan>()
    var state: IStateStack? = null
    var offset = 0
    text.split('\n').forEach { line ->
        val result = grammar.tokenizeLine(line, state, SyntaxLimits.LINE_TIME)
        state = result.ruleStack
        result.tokens.forEach { token ->
            val start = offset + token.startIndex
            val end = offset + minOf(token.endIndex, line.length)
            val style = roles.style(token.scopes)
            val last = spans.lastOrNull()
            when {
                end <= start || style == PLAIN -> Unit
                last != null && last.end == start && last.style == style -> spans[spans.lastIndex] = last.copy(end = end)
                else -> spans += SyntaxSpan(start, end, style)
            }
        }
        offset += line.length + 1
    }
    return spans
}

private val PLAIN = SyntaxStyle(SyntaxRole.Plain)

/**
 * The read-only views' tokens, least recently used dropped first once the texts held pass [maxChars]:
 * a chat scrolled past the same block, or a diff opened twice, is tokenized once. Keyed by the
 * language and the text (its hash, then its characters), so two texts never share spans. A text past
 * [SyntaxLimits.BLOCK_CHARS] is not coloured. One thread only: SyntaxEngine's `syntax-tokens`.
 */
internal class SyntaxCache(private val maxChars: Int = MAX_CHARS) {
    private data class Key(val language: String, val text: String)

    private val entries = LinkedHashMap<Key, List<SyntaxSpan>>(INITIAL, LOAD, true)
    private var chars = 0L

    /** [text]'s spans as [language], from the cache or by [tokenize]; null when it cannot be coloured, not kept. */
    fun tokens(language: String, text: String, tokenize: () -> List<SyntaxSpan>?): List<SyntaxSpan>? {
        val key = Key(language, text)
        entries[key]?.let { return it }
        if (!SyntaxLimits.coloursBlock(text)) return null
        val spans = tokenize() ?: return null
        if (text.length <= maxChars) {
            entries[key] = spans
            chars += text.length
            val oldest = entries.entries.iterator()
            while (chars > maxChars && oldest.hasNext()) {
                chars -= oldest.next().key.text.length
                oldest.remove()
            }
        }
        return spans
    }

    private companion object {
        /** 2 MB of text held as UTF-16: well past what a screen of chat or diffs shows. */
        const val MAX_CHARS = 1_000_000
        const val INITIAL = 64
        const val LOAD = 0.75f
    }
}
