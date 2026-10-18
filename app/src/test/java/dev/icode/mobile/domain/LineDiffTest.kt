package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class LineDiffTest {
    private fun headers(base: String, text: String) = LineDiff.hunks(base, text).map { it.header }

    @Test fun `changes are numbered as git diff -U0 numbers them`() {
        // What `git diff --no-index -U0` printed for each pair (git 2.43).
        assertEquals(listOf("@@ -2 +2 @@"), headers("a\nb\nc\n", "a\nB\nc\n"))
        assertEquals(listOf("@@ -1,0 +2,2 @@"), headers("a\nd\n", "a\nb\nc\nd\n"))
        assertEquals(listOf("@@ -2,2 +1,0 @@"), headers("a\nb\nc\nd\n", "a\nd\n"))
        assertEquals(listOf("@@ -0,0 +1 @@"), headers("b\n", "a\nb\n"))
        assertEquals(listOf("@@ -1 +0,0 @@"), headers("a\nb\n", "b\n"))
        assertEquals(listOf("@@ -2 +2 @@"), headers("a\nb", "a\nb\n"))
        assertEquals(listOf("@@ -2 +2,2 @@"), headers("a\nb", "a\nb\nc"))
        assertEquals(emptyList<String>(), headers("a\nb\n", "a\nb\n"))
        assertEquals(listOf("@@ -0,0 +1,2 @@"), headers("", "x\ny\n"))
        assertEquals(listOf("@@ -1,2 +0,0 @@"), headers("x\ny\n", ""))
    }

    @Test fun `a change says its lines, removed then added, numbered on each side, and its kind`() {
        val change = LineDiff.hunks("a\nb\nc\nd\n", "a\nB\nC\nX\nd\n").single()
        assertEquals(
            listOf(
                GitDiffLine(GitDiffLineKind.Removed, 2, null, "b"),
                GitDiffLine(GitDiffLineKind.Removed, 3, null, "c"),
                GitDiffLine(GitDiffLineKind.Added, null, 2, "B"),
                GitDiffLine(GitDiffLineKind.Added, null, 3, "C"),
                GitDiffLine(GitDiffLineKind.Added, null, 4, "X"),
            ),
            change.lines,
        )
        assertEquals(LineChangeKind.Modified, change.kind)
        assertEquals(LineChangeKind.Added, LineDiff.hunks("a\n", "a\nb\n").single().kind)
        assertEquals(LineChangeKind.Deleted, LineDiff.hunks("a\nb\n", "a\n").single().kind)
    }

    @Test fun `a line's break is part of it, as Git compares lines`() {
        assertEquals(listOf("a\r\n", "b\n", "c\r", "d"), LineDiff.lines("a\r\nb\nc\rd"))
        assertEquals(listOf("@@ -1 +1 @@"), headers("a\nb\n", "a\r\nb\n"))
        assertEquals(emptyList<String>(), headers("a\r\nb\r\n", "a\r\nb\r\n"))
    }

    @Test fun `every edit found is a shortest one, and the changes turn the base into the text`() {
        val random = Random(7)
        repeat(2_000) {
            val base = randomText(random)
            val text = randomText(random)
            val hunks = LineDiff.hunks(base, text)
            val old = LineDiff.lines(base)
            val new = LineDiff.lines(text)
            assertEquals("$base|$text", old.size + new.size - 2 * longestCommon(old, new), hunks.sumOf { it.oldCount + it.newCount })
            assertEquals("$base|$text", text, applied(base, hunks, text))
        }
    }

    @Test fun `reverting a change puts the base's lines back, and only those`() {
        val base = "one\ntwo\nthree\nfour\nfive\n"
        val text = "zero\none\nTWO\nthree\nfive\nsix"
        val changes = LineDiff.hunks(base, text)
        assertEquals(4, changes.size)
        changes.forEach { change ->
            val reverted = LineDiff.revert(text, base, change)!!
            assertEquals(changes.size - 1, LineDiff.hunks(base, reverted).size)
        }
        var all = text
        while (true) all = LineDiff.revert(all, base, LineDiff.hunks(base, all).firstOrNull() ?: break)!!
        assertEquals(base, all)
        assertEquals("one\nTWO\nthree\nfive\nsix", LineDiff.revert(text, base, changes.first()))
    }

    @Test fun `staging a change writes the base with that change alone`() {
        val base = "one\ntwo\nthree\nfour\n"
        val text = "one\nTWO\nthree\nfour\nfive\n"
        val (first, second) = LineDiff.hunks(base, text)
        val staged = LineDiff.stage(base, text, first)!!
        assertEquals("one\nTWO\nthree\nfour\n", staged)
        assertEquals(listOf(second.header), LineDiff.hunks(staged, text).map { it.header })
        assertEquals("one\ntwo\nthree\nfour\nfive\n", LineDiff.stage(base, text, second))
    }

    @Test fun `a change the text no longer holds is neither reverted nor staged`() {
        val base = "a\nb\n"
        val change = LineDiff.hunks(base, "a\nB\n").single()
        assertNull(LineDiff.revert("a\nC\n", base, change))
        assertNull(LineDiff.stage(base, "a\nC\n", change))
    }

    @Test fun `a tap on a line finds the change marked there, and the count starts at the caret`() {
        // Line 2 modified, a line added after 4, line 7 deleted.
        val changes = LineDiff.hunks("1\n2\n3\n4\n5\n6\n7\n8\n", "1\nTWO\n3\n4\nnew\n5\n6\n8\n")
        assertEquals(listOf(LineChangeKind.Modified, LineChangeKind.Added, LineChangeKind.Deleted), changes.map { it.kind })
        assertEquals(listOf(1, 4, 7), changes.map { it.markedLine })
        assertEquals(0, LineDiff.changeAt(changes, 1))
        assertEquals(1, LineDiff.changeAt(changes, 4))
        assertEquals(2, LineDiff.changeAt(changes, 6))
        assertEquals(2, LineDiff.changeAt(changes, 7))
        assertNull(LineDiff.changeAt(changes, 0))
        assertNull(LineDiff.changeAt(changes, 3))
        assertEquals(0, LineDiff.nextChange(changes, 0))
        assertEquals(1, LineDiff.nextChange(changes, 2))
        assertEquals(2, LineDiff.nextChange(changes, 7))
        assertEquals(0, LineDiff.nextChange(changes, 8))
        assertNull(LineDiff.nextChange(emptyList(), 3))
    }

    @Test fun `the search stops when its checkpoint says so`() {
        val base = (1..2_000).joinToString("\n") { "old $it" }
        val text = (1..2_000).joinToString("\n") { "new $it" }
        var calls = 0
        try {
            LineDiff.hunks(base, text) { if (++calls > 3) throw IllegalStateException("stopped") }
            fail("The search ran to its end")
        } catch (stopped: IllegalStateException) {
            assertEquals("stopped", stopped.message)
        }
        assertTrue(calls > 3)
    }

    /** A few lines from a small alphabet, so lines repeat as code's do; sometimes no final line break. */
    private fun randomText(random: Random): String {
        val lines = List(random.nextInt(0, 12)) { listOf("a", "b", "c", "d", "}", "")[random.nextInt(6)] }
        val text = lines.joinToString("\n")
        return if (lines.isNotEmpty() && random.nextBoolean()) "$text\n" else text
    }

    /** The longest common subsequence's length, by the textbook table. */
    private fun longestCommon(a: List<String>, b: List<String>): Int {
        val table = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            table[i][j] = if (a[i] == b[j]) table[i + 1][j + 1] + 1 else maxOf(table[i + 1][j], table[i][j + 1])
        }
        return table[0][0]
    }

    /**
     * The base's lines outside the changes and the text's lines inside them: the text again only
     * when each change's ranges are right and what they leave out is the same on both sides.
     */
    private fun applied(base: String, hunks: List<GitDiffHunk>, text: String): String {
        val old = LineDiff.lines(base)
        val new = LineDiff.lines(text)
        val out = StringBuilder()
        var at = 0
        hunks.forEach { hunk ->
            val oldFrom = if (hunk.oldCount == 0) hunk.oldStart else hunk.oldStart - 1
            val newFrom = if (hunk.newCount == 0) hunk.newStart else hunk.newStart - 1
            old.subList(at, oldFrom).forEach(out::append)
            new.subList(newFrom, newFrom + hunk.newCount).forEach(out::append)
            at = oldFrom + hunk.oldCount
        }
        old.subList(at, old.size).forEach(out::append)
        return out.toString()
    }
}
