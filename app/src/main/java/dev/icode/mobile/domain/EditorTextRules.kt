package dev.icode.mobile.domain

/** Replace [start] until [end] of a text with [replacement]. */
data class TextEdit(val start: Int, val end: Int, val replacement: String)

/** How the editor puts text in and takes it out (docs/editor-and-language-services.md), as VS Code does. */
object EditorTextRules {
    /** The line break [ending] writes; a file with none yet takes LF, as Git and Android write it. */
    fun separatorOf(ending: LineEnding): String = when (ending) {
        LineEnding.CrLf -> "\r\n"
        LineEnding.Cr -> "\r"
        LineEnding.Lf, LineEnding.None -> "\n"
    }

    /** [text] with each of its line breaks — LF, CRLF or a lone CR — written as [separator]. */
    fun withLineBreaks(text: String, separator: String): String {
        if ('\r' !in text && (separator == "\n" || '\n' !in text)) return text
        return buildString(text.length) {
            var at = 0
            while (at < text.length) {
                when (val char = text[at]) {
                    '\r' -> {
                        append(separator)
                        if (at + 1 < text.length && text[at + 1] == '\n') at++
                    }
                    '\n' -> append(separator)
                    else -> append(char)
                }
                at++
            }
        }
    }

    /**
     * The smallest edit that turns [old] into [new]: what they start and end with stays, so the
     * caret and the undo history around it stay too; null when they are equal. An edge between a
     * `\r` and a `\n` — splitting a CRLF of either text, or making one — moves to take the `\r`
     * or the `\n` in, as the editor keeps a CRLF one line break.
     */
    fun splice(old: String, new: String): TextEdit? {
        if (old == new) return null
        var start = 0
        val shorter = minOf(old.length, new.length)
        while (start < shorter && old[start] == new[start]) start++
        var suffix = 0
        while (suffix < shorter - start && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        while (start > 0 && old[start - 1] == '\r' && (old.getOrNull(start) == '\n' || new.getOrNull(start) == '\n')) start--
        while (suffix > 0 && old[old.length - suffix] == '\n' &&
            (old.getOrNull(old.length - suffix - 1) == '\r' || new.getOrNull(new.length - suffix - 1) == '\r')
        ) suffix--
        return TextEdit(start, old.length - suffix, new.substring(start, new.length - suffix))
    }

    /**
     * How many characters Backspace takes before the caret, given the line before it. In the
     * line's leading whitespace it goes back to the previous tab stop, as VS Code's
     * `editor.useTabStops` does; elsewhere one character.
     */
    fun deleteLeftCount(beforeCaret: String, tabSize: Int): Int {
        if (beforeCaret.isEmpty() || beforeCaret.any { it != ' ' && it != '\t' }) return minOf(1, beforeCaret.length)
        // Where each character starts, a tab reaching the next tab stop.
        val starts = IntArray(beforeCaret.length)
        var column = 0
        beforeCaret.forEachIndexed { index, char ->
            starts[index] = column
            column = if (char == '\t') column + tabSize - column % tabSize else column + 1
        }
        val stop = (column - 1) / tabSize * tabSize
        var keep = beforeCaret.length
        while (keep > 0 && starts[keep - 1] >= stop) keep--
        return beforeCaret.length - keep
    }
}
