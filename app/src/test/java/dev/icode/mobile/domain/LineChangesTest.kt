package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LineChangesTest {
    private fun between(before: String?, after: String) = LineChanges.between(before, after)

    @Test fun `a new file adds its lines, a final line break starting none`() {
        assertEquals(LineChanges(2, 0), between(null, "fun main() {\n}\n"))
        assertEquals(LineChanges(2, 0), between(null, "fun main() {\n}"))
        assertEquals(LineChanges(0, 0), between(null, ""))
    }

    @Test fun `a changed line is one removed and one added, as Git counts it`() {
        assertEquals(LineChanges(1, 1), between("a\nb\nc\n", "a\nB\nc\n"))
        assertEquals(LineChanges(0, 0), between("a\nb\n", "a\nb\n"))
        assertEquals(LineChanges(2, 0), between("a\nd\n", "a\nb\nc\nd\n"))
        assertEquals(LineChanges(0, 2), between("a\nb\nc\nd\n", "a\nd\n"))
    }

    @Test fun `lines kept between the changes are not counted, wherever they are`() {
        // Two edits apart, and a line moved: a shortest edit keeps what both share.
        val before = (1..10).joinToString("\n") { "line $it" }
        val after = before.replace("line 2", "line two").replace("line 9", "line nine")
        assertEquals(LineChanges(2, 2), between(before, after))
        assertEquals(LineChanges(1, 1), between("a\nb\nc\nd\n", "b\nc\nd\na\n"))
        assertEquals(LineChanges(3, 2), between("x\ny\n", "a\nb\nc\n"))
    }

    @Test fun `a last line that gains or loses its line break is changed, as Git counts it`() {
        assertEquals(LineChanges(1, 1), between("a\nb", "a\nb\n"))
        assertEquals(LineChanges(1, 1), between("a\nb\n", "a\nb"))
        // The last line gains its line break as a line is added after it: Git says +2 −1.
        assertEquals(LineChanges(2, 1), between("a", "a\nb"))
        assertEquals(LineChanges(0, 0), between("a\nb", "a\nb"))
    }

    @Test fun `line endings do not count as changes, and the label says what changed`() {
        // A file keeps its line endings when written: a model's \n is written as the file's \r\n.
        assertEquals(LineChanges(0, 0), between("a\r\nb\r\n", "a\nb\n"))
        assertEquals(LineChanges(1, 1), between("a\r\nb\r\n", "a\r\nc\r\n"))
        assertEquals("+12 −3", LineChanges(12, 3).label)
        assertEquals("+12", LineChanges(12, 0).label)
        assertEquals("−3", LineChanges(0, 3).label)
        assertEquals("No changes", LineChanges(0, 0).label)
    }

    @Test fun `a whole rewrite of a long file is counted exactly`() {
        val before = (1..3_000).joinToString("\n") { "old $it" }
        val after = (1..2_000).joinToString("\n") { "new $it" }
        assertEquals(LineChanges(2_000, 3_000), between(before, after))
    }
}
