package dev.icode.mobile.domain.markdown

/**
 * An answer's `\(…\)` and `\[…\]` as the parser reads math, `$…$` and `$$…$$`
 * (docs/markdown-rendering-plan.md §3.2). Models write both; the parser reads only dollars, and
 * Markdown's escapes would turn `\(` into `(` before math could see it.
 *
 * Fed an answer as it streams, it returns what is settled, and what it returned never changes, so the
 * parser's streaming file can take it as it comes. A character the next ones decide — a backslash, a
 * run of backticks — waits for them, or for [finish].
 *
 * - Outside code fences and code spans only.
 * - `\(` anywhere, closed by `\)`. Spaces just inside go: `\( x \)` is `$x$`, as `$ x $` is not math.
 * - `\[` only at a line's start, after its indentation and quote markers: mid-line it is far more often
 *   an escaped bracket, `a\[0\]`. Closed by `\]`.
 * - A blank line ends one left open.
 */
internal class MathDelimiters {
    private enum class Math { None, Inline, Display }

    private val waiting = StringBuilder()
    private val line = StringBuilder()
    /** The run that opened the fence the text is in: "```", "~~~~". */
    private var fence: String? = null
    /** The length of the backtick run that opened the code span the text is in; 0 outside one. */
    private var codeSpan = 0
    private var math = Math.None
    /** Only indentation and quote markers so far on this line. */
    private var lineStart = true
    /** Spaces in inline math held back: they go if `\)` follows. */
    private var spaces = 0
    /** Just after `\(`: its spaces go. */
    private var opened = false

    fun feed(chunk: CharSequence): String {
        waiting.append(chunk)
        val out = StringBuilder()
        var at = 0
        while (at < waiting.length) {
            val used = step(at, out, final = false)
            if (used == 0) break
            at += used
        }
        waiting.delete(0, at)
        return out.toString()
    }

    /** What is still waiting: the answer is whole. */
    fun finish(): String {
        val out = StringBuilder()
        var at = 0
        while (at < waiting.length) at += step(at, out, final = true)
        waiting.clear()
        releaseSpaces(out)
        return out.toString()
    }

    /** The characters at [at] taken into [out]; 0 when what they are waits on more text. */
    private fun step(at: Int, out: StringBuilder, final: Boolean): Int {
        val c = waiting[at]
        if (c == '\n') {
            releaseSpaces(out)
            out.append(c)
            endLine()
            return 1
        }
        if (fence != null) return take(c.toString(), out)
        if (c == '`' || (c == '~' && lineStart)) {
            var end = at
            while (end < waiting.length && waiting[end] == c) end++
            if (end == waiting.length && !final) return 0
            val run = end - at
            // A fence is known at its line's end; a run that may open one is no code span.
            if (c == '`' && !(lineStart && run >= 3)) codeSpan = if (codeSpan == 0) run else if (codeSpan == run) 0 else codeSpan
            releaseSpaces(out)
            return take(waiting.substring(at, end), out)
        }
        if (codeSpan > 0) return take(c.toString(), out)
        if (c == '\\') {
            if (at + 1 == waiting.length) return if (final) take("\\", out) else 0
            val pair = waiting.substring(at, at + 2)
            val dollars = when {
                pair == "\\(" && math == Math.None -> "$".also { math = Math.Inline; opened = true; releaseSpaces(out) }
                pair == "\\)" && math == Math.Inline -> "$".also { math = Math.None; spaces = 0 }
                pair == "\\[" && math == Math.None && lineStart -> "$$".also { math = Math.Display }
                pair == "\\]" && math == Math.Display -> "$$".also { math = Math.None }
                else -> null
            }
            if (dollars == null) {
                releaseSpaces(out)
                opened = false
                return take(pair, out)
            }
            out.append(dollars)
            line.append(pair)
            lineStart = false
            return 2
        }
        if (c == ' ' && math == Math.Inline) {
            if (!opened) spaces++
            line.append(c)
            return 1
        }
        releaseSpaces(out)
        opened = false
        return take(c.toString(), out)
    }

    /** [text] as it is. */
    private fun take(text: String, out: StringBuilder): Int {
        out.append(text)
        line.append(text)
        if (text.any { it != ' ' && it != '\t' && it != '>' }) lineStart = false
        return text.length
    }

    private fun releaseSpaces(out: StringBuilder) {
        repeat(spaces) { out.append(' ') }
        spaces = 0
    }

    private fun endLine() {
        val content = line.trimStart(' ', '\t', '>').trimEnd()
        val open = fence
        if (open != null) {
            if (content.length >= open.length && content.all { it == open[0] }) fence = null
        } else {
            val mark = content.firstOrNull()
            val run = content.takeWhile { it == mark }.length
            if ((mark == '`' || mark == '~') && run >= 3 && !(mark == '`' && content.drop(run).contains('`'))) {
                fence = content.substring(0, run)
                codeSpan = 0
            }
        }
        if (line.isBlank()) {
            codeSpan = 0
            math = Math.None
        }
        line.clear()
        lineStart = true
        opened = false
    }
}
