package dev.icode.mobile.ui.editor

import dev.icode.mobile.ui.syntax.SyntaxStyle
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Spans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * The grammar's [raw] styles as the editor draws them with a language server's runs over them
 * (docs/syntax-highlighting-plan.md D8). Sora 0.24 draws only what its analyzer sets, so the editor is
 * given these in its place: everything is [raw]'s but the spans, which read [raw]'s and lay [lines]'
 * runs over them. The analyzer goes on writing [raw]'s spans as it always does. Main thread.
 */
internal class SemanticStyles(val raw: Styles, lines: () -> SemanticLines?) : Styles(SemanticSpans(raw.spans, lines)) {
    init {
        sync()
    }

    /** [raw]'s blocks and line styles, which the analyzer may have replaced since. */
    fun sync() {
        lineStyles = raw.lineStyles
        styleTypeCount = raw.styleTypeCount
        blocks = raw.blocks
        suppressSwitch = raw.suppressSwitch
        indentCountMode = raw.indentCountMode
    }
}

/** [base]'s spans, each line with [lines]' runs laid over it; a line without runs is [base]'s own. */
internal class SemanticSpans(val base: Spans, private val lines: () -> SemanticLines?) : Spans {
    /** Each drawn line's spans, kept while its lines and its grammar spans stay the same. */
    private val drawn = HashMap<Int, Pair<Long, List<Span>>>()
    private var drawnFor: SemanticLines? = null

    override fun adjustOnInsert(start: CharPosition, end: CharPosition) = base.adjustOnInsert(start, end)

    override fun adjustOnDelete(start: CharPosition, end: CharPosition) = base.adjustOnDelete(start, end)

    override fun read(): Spans.Reader = Reader(base.read())

    override fun supportsModify(): Boolean = base.supportsModify()

    override fun modify(): Spans.Modifier = base.modify()

    override fun getLineCount(): Int = base.lineCount

    /** [line]'s spans with its runs over [spans]; null when it has no runs. */
    @Synchronized
    fun overlaid(line: Int, spans: List<Span>): List<Span>? {
        val current = lines() ?: return null
        val runs = current.runs(line) ?: return null
        if (drawnFor !== current) {
            drawn.clear()
            drawnFor = current
        }
        val key = spans.fold(spans.size.toLong()) { hash, span -> hash * 31 + span.column * 17 + span.style }
        drawn[line]?.takeIf { it.first == key }?.let { return it.second }
        val grammar = spans.ifEmpty { listOf(SpanFactory.obtainNoExt(0, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))) }
        val columns = IntArray(grammar.size) { grammar[it].column }
        val result = semanticSegments(columns, runs).map { segment ->
            val under = grammar[segment.base.coerceAtLeast(0)]
            under.copy().apply {
                column = segment.column
                if (segment.run >= 0) style = styled(under.style, runs[segment.run].style)
            }
        }
        drawn[line] = key to result
        return result
    }

    private inner class Reader(private val reader: Spans.Reader) : Spans.Reader {
        private var line: List<Span>? = null

        override fun moveToLine(line: Int) {
            reader.moveToLine(line)
            this.line = if (line < 0) null else overlaid(line, List(reader.spanCount) { reader.getSpanAt(it) })
        }

        override fun getSpanCount(): Int = line?.size ?: reader.spanCount

        override fun getSpanAt(index: Int): Span = line?.get(index) ?: reader.getSpanAt(index)

        override fun getSpansOnLine(line: Int): List<Span> = reader.getSpansOnLine(line).let { spans -> overlaid(line, spans) ?: spans }
    }

    companion object {
        /** [grammar]'s style with the run's colour and font style over it; its background kept. */
        fun styled(grammar: Long, run: SyntaxStyle): Long = TextStyle.makeStyle(
            semanticColorId(run.role),
            TextStyle.getBackgroundColorId(grammar),
            run.bold || TextStyle.isBold(grammar),
            run.italic || TextStyle.isItalics(grammar),
            run.strikethrough || TextStyle.isStrikeThrough(grammar),
            TextStyle.isNoCompletion(grammar),
        )
    }
}
