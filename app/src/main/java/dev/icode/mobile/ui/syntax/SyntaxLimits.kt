package dev.icode.mobile.ui.syntax

import java.time.Duration

/**
 * When colouring stops (docs/editor-and-language-services.md), every measured limit and its reason in
 * one place.
 */
object SyntaxLimits {
    /** A line is coloured up to here; the rest of it is plain. Cut before tokenizing: tm4e checks its time only between scans. */
    const val LINE_CHARS = 10_000

    /** A line's tokenizing stops after this, and the line stays plain past where it got to. */
    val LINE_TIME: Duration = Duration.ofMillis(100)

    /** A file larger than this, in UTF-8 bytes, opens plain. */
    const val FILE_BYTES = 2 * 1024 * 1024

    /** A file of more lines than this opens plain. */
    const val FILE_LINES = 50_000

    /** A read-only block (a Markdown fence, a diff) longer than this, in characters, is shown plain. */
    const val BLOCK_CHARS = 200_000

    /**
     * Whether a file of [text] is coloured: no more than [FILE_BYTES] in UTF-8 and [FILE_LINES] lines, a
     * line ending at each `\n`, `\r\n` or lone `\r` as the editor counts them. Stops reading once over.
     */
    fun colours(text: CharSequence): Boolean {
        var bytes = 0L
        var lines = 1
        var index = 0
        while (index < text.length) {
            val char = text[index]
            bytes += when {
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                // Each half of a surrogate pair: 4 bytes for the pair.
                char.isSurrogate() -> 2
                else -> 3
            }
            if (char == '\n' || (char == '\r' && text.getOrNull(index + 1) != '\n')) lines++
            if (bytes > FILE_BYTES || lines > FILE_LINES) return false
            index++
        }
        return true
    }

    /** Whether a read-only block of [text] is coloured. */
    fun coloursBlock(text: CharSequence): Boolean = text.length <= BLOCK_CHARS

    /** How much of [line] is tokenized: all of it up to [LINE_CHARS], never splitting a surrogate pair. */
    fun cut(line: String): Int {
        if (line.length <= LINE_CHARS) return line.length
        return if (line[LINE_CHARS - 1].isHighSurrogate()) LINE_CHARS - 1 else LINE_CHARS
    }
}
