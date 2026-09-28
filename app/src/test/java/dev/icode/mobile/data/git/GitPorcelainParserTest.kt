package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitStatusCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are literal output captured from `git status --porcelain=v2
 * --branch -z` (git 2.53) against a repository built with a staged edit, a
 * further unstaged edit of the same file, a rename with spaces in both names,
 * a deletion and an untracked file with a space.
 */
class GitPorcelainParserTest {

    private val nul = "\u0000"

    private val realStatus = listOf(
        "# branch.oid 7d466e4c47d969352f2bc7f569d10ef8b72d2170",
        "# branch.head main",
        "1 .D N... 100644 100644 000000 587be6b4c3f93f93c489c0111bba5596147a26cb 587be6b4c3f93f93c489c0111bba5596147a26cb deleted.txt",
        "1 MM N... 100644 100644 100644 f384549cbeb481e437091320de6d1f2e15e11b4a 6addb9b7c753aa12cdc6c0f22d7c7fe4b43ec8eb kept.txt",
        "2 R. N... 100644 100644 100644 fbbee861521bd5355538b096fa3998541cd33909 fbbee861521bd5355538b096fa3998541cd33909 R100 new name.txt",
        "old name.txt",
        "? untracked file.txt",
        "",
    ).joinToString(nul)

    @Test
    fun `reads the branch header`() {
        val status = GitPorcelainParser.parse(realStatus)
        assertEquals("main", status.header.branch)
        assertEquals("7d466e4c47d969352f2bc7f569d10ef8b72d2170", status.header.oid)
        assertTrue(status.header.hasCommits)
        assertFalse(status.header.detached)
    }

    @Test
    fun `a staged and re-edited file appears in both groups`() {
        val kept = GitPorcelainParser.parse(realStatus).changes.filter { it.path == "kept.txt" }
        assertEquals(2, kept.size)
        assertEquals(setOf(true, false), kept.map { it.staged }.toSet())
        assertTrue(kept.all { it.status == GitStatusCodes.MODIFIED })
    }

    @Test
    fun `a worktree deletion is unstaged`() {
        val deleted = GitPorcelainParser.parse(realStatus).changes.single { it.path == "deleted.txt" }
        assertEquals(GitStatusCodes.DELETED, deleted.status)
        assertFalse(deleted.staged)
    }

    @Test
    fun `a rename keeps both names and stays staged`() {
        val renamed = GitPorcelainParser.parse(realStatus).changes.single { it.path == "new name.txt" }
        assertTrue(renamed.staged)
        // The original path travels in the display name.
        assertEquals("old name.txt → new name.txt", renamed.name)
    }

    @Test
    fun `the record after a rename is consumed, not parsed as a change`() {
        val paths = GitPorcelainParser.parse(realStatus).changes.map { it.path }
        assertFalse("old name.txt" in paths)
    }

    @Test
    fun `untracked paths with spaces survive`() {
        val untracked = GitPorcelainParser.parse(realStatus).changes.single { it.status == GitStatusCodes.UNTRACKED }
        assertEquals("untracked file.txt", untracked.path)
        assertEquals("untracked file.txt", untracked.name)
    }

    @Test
    fun `ahead and behind come from the branch ab header`() {
        val output = listOf(
            "# branch.oid abc123",
            "# branch.head feature",
            "# branch.upstream origin/feature",
            "# branch.ab +3 -2",
            "",
        ).joinToString(nul)
        val header = GitPorcelainParser.parse(output).header
        assertEquals("origin/feature", header.upstream)
        assertEquals(3, header.ahead)
        assertEquals(2, header.behind)
    }

    @Test
    fun `a detached head is not mistaken for a branch`() {
        val output = "# branch.oid abc123${nul}# branch.head (detached)$nul"
        assertTrue(GitPorcelainParser.parse(output).header.detached)
    }

    @Test
    fun `an unborn repository reports no commits`() {
        val output = "# branch.oid (initial)${nul}# branch.head main$nul"
        assertFalse(GitPorcelainParser.parse(output).header.hasCommits)
    }

    @Test
    fun `conflicts parse and sort first`() {
        val output = listOf(
            "# branch.head main",
            "1 M. N... 100644 100644 100644 aaa bbb staged.txt",
            "u UU N... 100644 100644 100644 100644 aaa bbb ccc merged.txt",
            "",
        ).joinToString(nul)
        val changes = GitPorcelainParser.parse(output).changes
        assertEquals(GitStatusCodes.CONFLICTED, changes.first().status)
        assertEquals("merged.txt", changes.first().path)
    }

    @Test
    fun `a path containing a newline is not split`() {
        // NUL termination is exactly what makes this safe.
        val weird = "we\nird.txt"
        val output = "# branch.head main${nul}? $weird$nul"
        assertEquals(weird, GitPorcelainParser.parse(output).changes.single().path)
    }

    @Test
    fun `ignored paths are not reported as changes`() {
        val output = "# branch.head main${nul}! build/output.bin$nul"
        assertTrue(GitPorcelainParser.parse(output).changes.isEmpty())
    }

    @Test
    fun `empty output yields nothing`() {
        assertTrue(GitPorcelainParser.parse("").changes.isEmpty())
    }

    @Test
    fun `an untracked folder is named by its last segment`() {
        val changes = GitPorcelainParser.parse(listOf("? build/", "? src/generated/", "").joinToString(nul)).changes
        assertEquals(listOf("build/", "generated/"), changes.map { it.name })
        assertEquals(listOf("build/", "src/generated/"), changes.map { it.path })
    }
}
