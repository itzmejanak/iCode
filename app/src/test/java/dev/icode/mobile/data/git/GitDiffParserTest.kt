package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitDiffLineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are literal `git diff` output (git 2.53) for a deletion, a modified
 * file, and a new file whose name contains a space — the case where Git appends
 * a tab to the `+++` header.
 */
class GitDiffParserTest {

    private val unstagedPatch = """
        diff --git a/deleted.txt b/deleted.txt
        deleted file mode 100644
        index 587be6b..0000000
        --- a/deleted.txt
        +++ /dev/null
        @@ -1 +0,0 @@
        -x
        diff --git a/kept.txt b/kept.txt
        index 6addb9b..28edfb3 100644
        --- a/kept.txt
        +++ b/kept.txt
        @@ -1,4 +1,4 @@
         one
         TWO
         three
        -four
        +FOUR
    """.trimIndent()

    @Test
    fun `parses every file in one patch`() {
        val diffs = GitDiffParser.parse(unstagedPatch)
        assertEquals(listOf("deleted.txt", "kept.txt"), diffs.map { it.path })
    }

    @Test
    fun `a deletion against dev null keeps its original path`() {
        val deleted = GitDiffParser.parse(unstagedPatch).first { it.path == "deleted.txt" }
        assertEquals(1, deleted.removedLines)
        assertEquals(0, deleted.addedLines)
    }

    @Test
    fun `an omitted hunk count means one line`() {
        val hunk = GitDiffParser.parse(unstagedPatch).first { it.path == "deleted.txt" }.hunks.single()
        assertEquals(1, hunk.oldStart)
        assertEquals(1, hunk.oldCount)
        assertEquals(0, hunk.newStart)
        assertEquals(0, hunk.newCount)
    }

    @Test
    fun `line numbers advance independently on each side`() {
        val hunk = GitDiffParser.parse(unstagedPatch).first { it.path == "kept.txt" }.hunks.single()
        val removed = hunk.lines.single { it.kind == GitDiffLineKind.Removed }
        val added = hunk.lines.single { it.kind == GitDiffLineKind.Added }
        assertEquals("four", removed.text)
        assertEquals(4, removed.oldLine)
        assertNull(removed.newLine)
        assertEquals("FOUR", added.text)
        assertEquals(4, added.newLine)
        assertNull(added.oldLine)
    }

    @Test
    fun `context lines carry both numbers`() {
        val hunk = GitDiffParser.parse(unstagedPatch).first { it.path == "kept.txt" }.hunks.single()
        val first = hunk.lines.first()
        assertEquals(GitDiffLineKind.Context, first.kind)
        assertEquals("one", first.text)
        assertEquals(1, first.oldLine)
        assertEquals(1, first.newLine)
        assertEquals("1 1", first.gutter)
    }

    @Test
    fun `gutter labels match the diff frame`() {
        val hunk = GitDiffParser.parse(unstagedPatch).first { it.path == "kept.txt" }.hunks.single()
        assertEquals("4  -", hunk.lines.single { it.kind == GitDiffLineKind.Removed }.gutter)
        assertEquals("4  +", hunk.lines.single { it.kind == GitDiffLineKind.Added }.gutter)
    }

    @Test
    fun `a path containing a space is read correctly`() {
        // Git appends a tab to the +++ header when the path has spaces.
        val patch = "diff --git a/new name.txt b/new name.txt\n" +
            "new file mode 100644\n" +
            "index 0000000..fbbee86\n" +
            "--- /dev/null\n" +
            "+++ b/new name.txt\t\n" +
            "@@ -0,0 +1,2 @@\n" +
            "+alpha\n" +
            "+beta\n"
        val diff = GitDiffParser.parse(patch).single()
        assertEquals("new name.txt", diff.path)
        assertEquals(2, diff.addedLines)
    }

    @Test
    fun `a binary file is flagged rather than shown as text`() {
        val patch = "diff --git a/logo.png b/logo.png\n" +
            "index 1111111..2222222 100644\n" +
            "Binary files a/logo.png and b/logo.png differ\n"
        val diff = GitDiffParser.parse(patch).single()
        assertTrue(diff.binary)
        assertTrue(diff.hunks.isEmpty())
    }

    @Test
    fun `a rename is reported with both paths`() {
        val patch = "diff --git a/old.txt b/new.txt\n" +
            "similarity index 100%\n" +
            "rename from old.txt\n" +
            "rename to new.txt\n"
        val diff = GitDiffParser.parse(patch).single()
        assertEquals("new.txt", diff.path)
        assertEquals("old.txt", diff.oldPath)
    }

    @Test
    fun `no newline markers are not treated as content`() {
        val patch = "diff --git a/a.txt b/a.txt\n" +
            "index 111..222 100644\n" +
            "--- a/a.txt\n" +
            "+++ b/a.txt\n" +
            "@@ -1 +1 @@\n" +
            "-old\n" +
            "\\ No newline at end of file\n" +
            "+new\n" +
            "\\ No newline at end of file\n"
        val hunk = GitDiffParser.parse(patch).single().hunks.single()
        assertEquals(2, hunk.lines.size)
        assertTrue(hunk.lines.none { it.text.startsWith("No newline") })
    }

    @Test
    fun `multiple hunks are kept separate`() {
        val patch = "diff --git a/a.txt b/a.txt\n" +
            "--- a/a.txt\n" +
            "+++ b/a.txt\n" +
            "@@ -1,2 +1,2 @@\n" +
            " one\n" +
            "-two\n" +
            "+TWO\n" +
            "@@ -10,2 +10,2 @@\n" +
            " ten\n" +
            "-eleven\n" +
            "+ELEVEN\n"
        val diff = GitDiffParser.parse(patch).single()
        assertEquals(2, diff.hunks.size)
        assertEquals(10, diff.hunks[1].oldStart)
        assertEquals(2, diff.addedLines)
        assertEquals(2, diff.removedLines)
    }

    @Test
    fun `an empty context line is a single space, not an empty line`() {
        // Verified against real git: an empty context line is written as " ".
        val patch = "diff --git a/f.txt b/f.txt\n" +
            "--- a/f.txt\n" +
            "+++ b/f.txt\n" +
            "@@ -1,3 +1,3 @@\n" +
            " one\n" +
            " \n" +
            "-three\n" +
            "+THREE\n"
        val hunk = GitDiffParser.parse(patch).single().hunks.single()
        assertEquals(4, hunk.lines.size)
        val blank = hunk.lines[1]
        assertEquals(GitDiffLineKind.Context, blank.kind)
        assertEquals("", blank.text)
        assertEquals(2, blank.oldLine)
        assertEquals(2, blank.newLine)
    }

    @Test
    fun `an empty patch yields nothing`() {
        assertTrue(GitDiffParser.parse("").isEmpty())
        assertFalse(GitDiffParser.parse(unstagedPatch).first().isEmpty)
    }

    @Test
    fun `the staged flag travels with the diff`() {
        val diffs = GitDiffParser.parse(unstagedPatch, staged = true)
        assertTrue(diffs.all { it.staged })
    }
}
