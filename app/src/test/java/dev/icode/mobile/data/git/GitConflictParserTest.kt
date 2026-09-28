package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitConflictChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are the literal contents git leaves in a conflicted file, captured
 * from a real merge (git 2.53) in both the default and diff3 styles.
 */
class GitConflictParserTest {

    private val conflicted = listOf(
        "line one",
        "<<<<<<< HEAD",
        "CURRENT",
        "=======",
        "INCOMING",
        ">>>>>>> feature",
        "line three",
    ).joinToString("\n")

    private val diff3Conflicted = listOf(
        "line one",
        "<<<<<<< HEAD",
        "CURRENT",
        "||||||| a34b7e2",
        "shared",
        "=======",
        "INCOMING",
        ">>>>>>> feature",
        "line three",
    ).joinToString("\n")

    @Test
    fun `finds one conflict with both sides`() {
        val file = GitConflictParser.parse("f.txt", conflicted)
        val section = file.sections.single()
        assertEquals(listOf("CURRENT"), section.current)
        assertEquals(listOf("INCOMING"), section.incoming)
    }

    @Test
    fun `carries the branch labels git wrote`() {
        val section = GitConflictParser.parse("f.txt", conflicted).sections.single()
        assertEquals("HEAD", section.currentLabel)
        assertEquals("feature", section.incomingLabel)
    }

    @Test
    fun `accepting current keeps only our side and the surrounding lines`() {
        val file = GitConflictParser.parse("f.txt", conflicted)
        assertEquals(
            "line one\nCURRENT\nline three",
            GitConflictParser.resolve(file, file.sections.single(), GitConflictChoice.Current),
        )
    }

    @Test
    fun `accepting incoming keeps only their side`() {
        val file = GitConflictParser.parse("f.txt", conflicted)
        assertEquals(
            "line one\nINCOMING\nline three",
            GitConflictParser.resolve(file, file.sections.single(), GitConflictChoice.Incoming),
        )
    }

    @Test
    fun `accepting both keeps current then incoming`() {
        val file = GitConflictParser.parse("f.txt", conflicted)
        assertEquals(
            "line one\nCURRENT\nINCOMING\nline three",
            GitConflictParser.resolve(file, file.sections.single(), GitConflictChoice.Both),
        )
    }

    @Test
    fun `no marker survives a resolution`() {
        val file = GitConflictParser.parse("f.txt", conflicted)
        GitConflictChoice.entries.forEach { choice ->
            val resolved = GitConflictParser.resolve(file, file.sections.single(), choice)
            assertFalse(resolved.contains("<<<<<<<"))
            assertFalse(resolved.contains("======="))
            assertFalse(resolved.contains(">>>>>>>"))
        }
    }

    @Test
    fun `diff3 base block is context, not a choosable side`() {
        val section = GitConflictParser.parse("f.txt", diff3Conflicted).sections.single()
        assertEquals(listOf("CURRENT"), section.current)
        assertEquals(listOf("INCOMING"), section.incoming)
        // The merge base must not leak into either side.
        assertFalse("shared" in section.current)
        assertFalse("shared" in section.incoming)
    }

    @Test
    fun `diff3 resolution drops the base block too`() {
        val file = GitConflictParser.parse("f.txt", diff3Conflicted)
        val resolved = GitConflictParser.resolve(file, file.sections.single(), GitConflictChoice.Current)
        assertEquals("line one\nCURRENT\nline three", resolved)
        assertFalse(resolved.contains("|||||||"))
    }

    @Test
    fun `several conflicts in one file are found and resolved one at a time`() {
        val text = listOf(
            "top",
            "<<<<<<< HEAD", "a1", "=======", "b1", ">>>>>>> other",
            "middle",
            "<<<<<<< HEAD", "a2", "=======", "b2", ">>>>>>> other",
            "bottom",
        ).joinToString("\n")
        val file = GitConflictParser.parse("f.txt", text)
        assertEquals(2, file.sections.size)
        // Each block is its own decision: the other keeps its markers.
        assertEquals(
            "top\na1\nmiddle\n<<<<<<< HEAD\na2\n=======\nb2\n>>>>>>> other\nbottom",
            GitConflictParser.resolve(file, file.sections[0], GitConflictChoice.Current),
        )
        assertEquals(
            "top\n<<<<<<< HEAD\na1\n=======\nb1\n>>>>>>> other\nmiddle\nb2\nbottom",
            GitConflictParser.resolve(file, file.sections[1], GitConflictChoice.Incoming),
        )
    }

    @Test
    fun `a multi-line side keeps its order`() {
        val text = listOf(
            "<<<<<<< HEAD", "one", "two", "=======", "three", ">>>>>>> other",
        ).joinToString("\n")
        val file = GitConflictParser.parse("f.txt", text)
        assertEquals(listOf("one", "two"), file.sections.single().current)
        assertEquals("one\ntwo\nthree", GitConflictParser.resolve(file, file.sections.single(), GitConflictChoice.Both))
    }

    @Test
    fun `an empty side is preserved as empty, not dropped`() {
        val text = listOf("<<<<<<< HEAD", "=======", "only theirs", ">>>>>>> other").joinToString("\n")
        val file = GitConflictParser.parse("f.txt", text)
        assertTrue(file.sections.single().current.isEmpty())
        assertEquals("", GitConflictParser.resolve(file, file.sections.single(), GitConflictChoice.Current))
    }

    @Test
    fun `a file with no markers reports no conflicts and is unchanged`() {
        val text = "just\nnormal\nlines"
        val file = GitConflictParser.parse("f.txt", text)
        assertTrue(file.isResolved)
        assertTrue(file.sections.isEmpty())
    }

    @Test
    fun `an unterminated marker block is ignored rather than half-resolved`() {
        val text = "start\n<<<<<<< HEAD\nours\nno separator ever"
        val file = GitConflictParser.parse("f.txt", text)
        assertTrue(file.sections.isEmpty())
    }
}
