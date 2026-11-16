package dev.icode.mobile.data.runtime

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/assistant-and-mcp.md: OSC 133 and OSC 7 found wherever a read cuts them; every other byte passed on. */
class TerminalMarksTest {
    private class Seen : TerminalMarks.Listener {
        val output = ByteArrayOutputStream()
        val marks = mutableListOf<TerminalMarks.Mark>()
        override fun output(data: ByteArray, from: Int, count: Int) = output.write(data, from, count)
        override fun mark(mark: TerminalMarks.Mark) {
            marks += mark
        }
    }

    private val stream = "\u001B]0;title\u0007\u001B[32mgreen\u001B[0m \u001B]133;A\u0007$ \u001B]133;B\u001B\\" +
        "ls\r\n\u001B]133;C\u0007a\u001B\u001B]7;file://phone/workspace/my%20app\u0007\u001B]133;D;1\u0007done"
    private val shown = "\u001B]0;title\u0007\u001B[32mgreen\u001B[0m $ ls\r\na\u001Bdone"
    private val expected = listOf(
        TerminalMarks.Mark.PromptStart,
        TerminalMarks.Mark.InputStart,
        TerminalMarks.Mark.OutputStart,
        TerminalMarks.Mark.Directory("/workspace/my app"),
        TerminalMarks.Mark.CommandEnd(1),
    )

    @Test
    fun `the marks are found, and everything else goes on in order`() {
        val seen = Seen()
        val bytes = stream.toByteArray()
        TerminalMarks(seen).feed(bytes, bytes.size)
        assertEquals(expected, seen.marks)
        assertEquals(shown, seen.output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `cut anywhere, it is the same`() {
        val bytes = stream.toByteArray()
        for (cut in 1 until bytes.size) {
            val seen = Seen()
            val marks = TerminalMarks(seen)
            marks.feed(bytes.copyOfRange(0, cut), cut)
            marks.feed(bytes.copyOfRange(cut, bytes.size), bytes.size - cut)
            assertEquals("cut at $cut", expected, seen.marks)
            assertEquals("cut at $cut", shown, seen.output.toString(Charsets.UTF_8.name()))
        }
    }

    @Test
    fun `a mark's words`() {
        assertEquals(TerminalMarks.Mark.CommandEnd(null), TerminalMarks.markOf("133;D"))
        assertEquals(TerminalMarks.Mark.CommandEnd(130), TerminalMarks.markOf("133;D;130;aid=1"))
        assertEquals(TerminalMarks.Mark.Directory("/root"), TerminalMarks.markOf("7;file:///root"))
        assertEquals(null, TerminalMarks.markOf("133;Z"))
        assertEquals(null, TerminalMarks.markOf("1337;SetMark"))
    }
}
