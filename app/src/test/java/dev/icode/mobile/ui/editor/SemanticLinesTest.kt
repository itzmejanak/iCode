package dev.icode.mobile.ui.editor

import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.domain.SemanticToken
import dev.icode.mobile.domain.SemanticTokens
import dev.icode.mobile.ui.syntax.SyntaxStyle
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Spans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A server's tokens as runs over the editor's lines, kept through edits and laid over the grammar's spans (S7). */
class SemanticLinesTest {
    private val variable = SyntaxStyle(SyntaxRole.Variable)
    private val attribute = SyntaxStyle(SyntaxRole.Attribute)

    private val text = "def area(shape):\r\n    return shape.width\n"
    private val answer = SemanticTokens(
        text,
        listOf(
            SemanticToken(0, 9, 5, "parameter", setOf("declaration")),
            SemanticToken(1, 11, 5, "parameter", emptySet()),
            SemanticToken(1, 17, 5, "property", emptySet()),
            // A type no rule colours: no run.
            SemanticToken(1, 4, 6, "label", emptySet()),
        ),
    )

    @Test
    fun `parameters and properties become runs of their roles on the lines whose text is unchanged`() {
        val lines = SemanticAnswer.of(answer).over(listOf("def area(shape):", "    return shape.width")::getOrNull)
        assertEquals(listOf(SemanticRun(9, 14, variable)), lines.runs(0))
        assertEquals(listOf(SemanticRun(11, 16, variable), SemanticRun(17, 22, attribute)), lines.runs(1))
        // The second line was edited after the server answered: only the first keeps its runs.
        val edited = SemanticAnswer.of(answer).over(listOf("def area(shape):", "    return shape.height")::getOrNull)
        assertEquals(listOf(SemanticRun(9, 14, variable)), edited.runs(0))
        assertNull(edited.runs(1))
    }

    @Test
    fun `an edit takes the runs of the lines it touched and moves the ones after it`() {
        val lines = SemanticLines(mapOf(0 to listOf(SemanticRun(0, 1, variable)), 2 to listOf(SemanticRun(1, 2, attribute)), 5 to listOf(SemanticRun(2, 3, variable))))
        val typed = lines.inserted(2, 2)
        assertNull(typed.runs(2))
        assertEquals(lines.runs(5), typed.runs(5))
        val twoLines = lines.inserted(1, 3)
        assertEquals(lines.runs(0), twoLines.runs(0))
        assertEquals(lines.runs(2), twoLines.runs(4))
        assertEquals(lines.runs(5), twoLines.runs(7))
        val joined = lines.deleted(2, 4)
        assertNull(joined.runs(2))
        assertEquals(lines.runs(5), joined.runs(3))
        // A line without runs, typed in: nothing changes.
        assertSame(lines, lines.inserted(3, 3))
    }

    @Test
    fun `runs lay over the grammar's spans in pieces`() {
        val runs = listOf(SemanticRun(4, 9, variable), SemanticRun(12, 14, attribute))
        assertEquals(
            listOf(
                SemanticSegment(0, 0, -1), SemanticSegment(4, 1, 0), SemanticSegment(9, 1, -1),
                SemanticSegment(10, 2, -1), SemanticSegment(12, 2, 1), SemanticSegment(14, 2, -1),
            ),
            semanticSegments(intArrayOf(0, 4, 10), runs),
        )
    }

    @Test
    fun `the editor draws the runs' roles over the grammar's colours, and lines without runs as they are`() {
        val grammar = 300
        val base = ListSpans(listOf(
            listOf(SpanFactory.obtainNoExt(0, TextStyle.makeStyle(grammar)), SpanFactory.obtainNoExt(8, TextStyle.makeStyle(grammar + 1, 0, true, false, false))),
            listOf(SpanFactory.obtainNoExt(0, TextStyle.makeStyle(grammar))),
        ))
        val lines = SemanticLines(mapOf(0 to listOf(SemanticRun(9, 14, variable), SemanticRun(15, 16, attribute))))
        val styles = SemanticStyles(Styles(base)) { lines }
        val reader = styles.spans.read()
        reader.moveToLine(0)
        val drawn = (0 until reader.spanCount).map { reader.getSpanAt(it).column to reader.getSpanAt(it).style }
        assertEquals(
            listOf(
                0 to TextStyle.makeStyle(grammar),
                8 to TextStyle.makeStyle(grammar + 1, 0, true, false, false),
                // The parameter in the role's colour; the grammar's bold stays.
                9 to TextStyle.makeStyle(semanticColorId(SyntaxRole.Variable), 0, true, false, false),
                14 to TextStyle.makeStyle(grammar + 1, 0, true, false, false),
                15 to TextStyle.makeStyle(semanticColorId(SyntaxRole.Attribute), 0, true, false, false),
                16 to TextStyle.makeStyle(grammar + 1, 0, true, false, false),
            ),
            drawn,
        )
        reader.moveToLine(1)
        assertSame(base.lines[1][0], reader.getSpanAt(0))
        assertSame(base.lines[1], reader.getSpansOnLine(1))
        assertTrue(semanticColorId(SyntaxRole.entries.last()) < 255)
    }

    /** The grammar's spans, a list per line. */
    private class ListSpans(val lines: List<List<Span>>) : Spans {
        override fun adjustOnInsert(start: CharPosition, end: CharPosition) = Unit
        override fun adjustOnDelete(start: CharPosition, end: CharPosition) = Unit
        override fun supportsModify() = false
        override fun modify(): Spans.Modifier = throw UnsupportedOperationException()
        override fun getLineCount() = lines.size
        override fun read(): Spans.Reader = object : Spans.Reader {
            var line = -1
            override fun moveToLine(line: Int) { this.line = line }
            override fun getSpanCount() = lines[line].size
            override fun getSpanAt(index: Int) = lines[line][index]
            override fun getSpansOnLine(line: Int) = lines[line]
        }
    }
}
