package dev.icode.mobile.ui.editor

import dev.icode.mobile.domain.SemanticTokens
import dev.icode.mobile.ui.syntax.SemanticRoles
import dev.icode.mobile.ui.syntax.SyntaxStyle

/** Columns [start] to [end] of a line, drawn as a language server's token says ([style]). */
internal data class SemanticRun(val start: Int, val end: Int, val style: SyntaxStyle)

/**
 * A server's answer as runs, by line, each line with its text as the server had it
 * (docs/editor-and-language-services.md). Made off the main thread; tokens whose type no rule colours
 * ([SemanticRoles]) leave the grammar's colours, so they have no run.
 */
internal class SemanticAnswer(private val lines: Map<Int, Pair<String, List<SemanticRun>>>) {
    /** The runs of each line whose text is still [lineOf]'s: a line edited since keeps its grammar's colours. */
    fun over(lineOf: (Int) -> String?): SemanticLines =
        SemanticLines(lines.filter { (line, entry) -> lineOf(line) == entry.first }.mapValues { it.value.second })

    companion object {
        fun of(answer: SemanticTokens, roles: SemanticRoles = SemanticRoles.shipped): SemanticAnswer {
            val starts = lineStarts(answer.text)
            val lines = HashMap<Int, Pair<String, MutableList<SemanticRun>>>()
            for (token in answer.tokens) {
                if (token.line >= starts.size) continue
                val style = roles.style(token.type, token.modifiers) ?: continue
                val entry = lines.getOrPut(token.line) { lineText(answer.text, starts, token.line) to mutableListOf() }
                val end = minOf(token.column + token.length, entry.first.length)
                if (token.column < end) entry.second += SemanticRun(token.column, end, style)
            }
            return SemanticAnswer(lines)
        }

        /** Where each line of [text] starts, its lines ended by `\n`, `\r\n` or `\r` as LSP counts them. */
        private fun lineStarts(text: String): IntArray {
            val starts = ArrayList<Int>().apply { add(0) }
            var index = 0
            while (index < text.length) {
                val char = text[index++]
                if (char == '\r' && index < text.length && text[index] == '\n') index++
                if (char == '\n' || char == '\r') starts += index
            }
            return starts.toIntArray()
        }

        private fun lineText(text: String, starts: IntArray, line: Int): String {
            val end = if (line + 1 < starts.size) starts[line + 1] else text.length
            return text.substring(starts[line], end).trimEnd('\n', '\r')
        }
    }
}

/**
 * The runs drawn over the editor's text now, by line. An edit takes the runs of the lines it touched
 * and moves those after it with their text, until the next answer.
 */
internal class SemanticLines(private val lines: Map<Int, List<SemanticRun>>) {
    val isEmpty: Boolean get() = lines.isEmpty()

    fun runs(line: Int): List<SemanticRun>? = lines[line]

    /** Text from [startLine] to [endLine] was inserted: [startLine] changed, the lines after it moved down. */
    fun inserted(startLine: Int, endLine: Int): SemanticLines = edited(startLine, startLine, endLine - startLine)

    /** Text from [startLine] to [endLine] was deleted: those lines changed or went, the lines after moved up. */
    fun deleted(startLine: Int, endLine: Int): SemanticLines = edited(startLine, endLine, startLine - endLine)

    private fun edited(from: Int, to: Int, moved: Int): SemanticLines {
        if (moved == 0 && from !in lines) return this
        val next = HashMap<Int, List<SemanticRun>>(lines.size)
        lines.forEach { (line, runs) ->
            when {
                line < from -> next[line] = runs
                line > to -> next[line + moved] = runs
            }
        }
        return SemanticLines(next)
    }
}

/** A piece of a drawn line from [column]: the grammar's span [base] (-1 for none), under the run [run] (-1 for none). */
internal data class SemanticSegment(val column: Int, val base: Int, val run: Int)

/**
 * A line's pieces with [runs] laid over the grammar's spans, which start at [baseColumns]: a new piece
 * wherever either changes, and none where neither does.
 */
internal fun semanticSegments(baseColumns: IntArray, runs: List<SemanticRun>): List<SemanticSegment> {
    val columns = (baseColumns.asSequence() + runs.asSequence().flatMap { sequenceOf(it.start, it.end) }).distinct().sorted().toList()
    val segments = ArrayList<SemanticSegment>(columns.size)
    var base = -1
    var run = 0
    for (column in columns) {
        while (base + 1 < baseColumns.size && baseColumns[base + 1] <= column) base++
        while (run < runs.size && runs[run].end <= column) run++
        val over = if (run < runs.size && runs[run].start <= column) run else -1
        val last = segments.lastOrNull()
        if (last == null || last.base != base || last.run != over) segments += SemanticSegment(column, base, over)
    }
    return segments
}
