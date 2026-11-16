package dev.icode.mobile.data.runtime

/**
 * A managed process's output as a reader wants it (docs/runtime-and-terminal.md): its
 * terminal escape codes removed, a carriage return rewriting the line as a terminal would — a
 * progress bar is its last state — and only the last [limit] characters of ended lines kept.
 *
 * Offsets count ended lines only, so nothing a reader was given is rewritten under it; the line
 * still being written (a prompt without its newline, a progress bar) comes with every read until
 * it ends. Not thread-safe: its owner guards it.
 */
internal class ProcessOutput(private val limit: Int = LIMIT) {
    /** Ended lines, the oldest dropped past [limit]. */
    private val kept = StringBuilder()

    /** The offset of [kept]'s first character, counting every ended line ever written. */
    private var start = 0L

    /** The line being written. */
    private val line = StringBuilder()

    /** A carriage return not yet followed: a newline after it ends the line, anything else rewrites it. */
    private var carriageReturn = false

    /** An escape sequence cut across two writes. */
    private var pendingEscape = ""

    /** The offset just past the last ended line: where a reader's next look starts. */
    val end: Long get() = start + kept.length

    fun append(text: String) {
        val (clean, rest) = strip(pendingEscape + text)
        pendingEscape = rest
        for (char in clean) {
            if (carriageReturn && char != '\n') line.setLength(0)
            carriageReturn = false
            when (char) {
                '\n' -> endLine()
                '\r' -> carriageReturn = true
                else -> {
                    line.append(char)
                    // A line that never ends is kept in pieces, never without bound.
                    if (line.length >= MAX_LINE) endLine()
                }
            }
        }
        val excess = kept.length - limit
        if (excess > 0) {
            kept.delete(0, excess)
            start += excess
        }
    }

    /**
     * What came from [offset] on — the ended lines, then the line being written — and whether
     * some of it is gone already.
     */
    fun since(offset: Long): Pair<String, Boolean> {
        val from = (offset - start).coerceIn(0, kept.length.toLong()).toInt()
        return (kept.substring(from) + line) to (offset < start)
    }

    /** The last [characters] of the output, from the start of a line where one starts inside them. */
    fun tail(characters: Int): String {
        val all = kept.toString() + line
        if (all.length <= characters) return all
        val cut = all.length - characters
        val lineStart = all.indexOf('\n', cut).takeIf { it in cut until all.length - 1 }?.plus(1) ?: cut
        return all.substring(lineStart)
    }

    private fun endLine() {
        kept.append(line).append('\n')
        line.setLength(0)
    }

    companion object {
        const val LIMIT = 256 * 1024
        private const val MAX_LINE = 16 * 1024

        /** CSI (colours, cursor moves), OSC (titles), and two-character escapes — not `[` and `]`, which open those. */
        private val ESCAPE = Regex("\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|[@-Z\\\\^_])")

        /** [text] without escape sequences, and what is left of one cut at its end, to join the next write. */
        internal fun strip(text: String): Pair<String, String> {
            val last = text.lastIndexOf('\u001B')
            val cut = last >= 0 && text.length - last < MAX_ESCAPE && ESCAPE.find(text, last)?.range?.first != last
            val whole = if (cut) text.substring(0, last) else text
            return ESCAPE.replace(whole, "") to (if (cut) text.substring(last) else "")
        }

        /** An escape longer than this is not one being cut, and is shown as it is. */
        private const val MAX_ESCAPE = 64
    }
}
