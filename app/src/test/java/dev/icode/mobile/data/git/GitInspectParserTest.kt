package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitStatusCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins what the inspect script's sections mean today, before the protocol is
 * made filename-safe (S5.6). The status section reuses the porcelain format
 * captured from real `git` in [GitPorcelainParserTest].
 */
class GitInspectParserTest {

    private val workspace = "/storage/emulated/0/Projects/app"
    private val nul = "\u0000"
    private val unit = "\u001F"

    /** toplevel, status, refs, remotes, user.name, user.email, last commit, markers — in the script's order. */
    private fun output(
        topLevel: String = "/workspace/app\n",
        status: String = "",
        refs: String = "",
        remotes: String = "",
        name: String = "",
        email: String = "",
        lastCommit: String = "",
        markers: String = "",
    ) = listOf(topLevel, status, refs, remotes, name, email, lastCommit, markers).joinToString("\u001E")

    private fun status(vararg records: String) = records.joinToString(nul, postfix = nul)

    @Test
    fun `no toplevel means no repository`() {
        val empty = GitInspectParser.parse(workspace, "")
        assertNull(empty.repository)
        assertTrue(empty.changes.isEmpty())
        assertNull(GitInspectParser.parse(workspace, output(topLevel = "\n")).repository)
    }

    @Test
    fun `a repository at the opened folder is reported at its host path`() {
        val repository = GitInspectParser.parse(workspace, output()).repository!!
        assertEquals(workspace, repository.workTreePath)
    }

    @Test
    fun `a toplevel below the guest workspace is translated to the host`() {
        val repository = GitInspectParser.parse(workspace, output(topLevel = "/workspace/app/inner\n")).repository!!
        assertEquals("$workspace/inner", repository.workTreePath)
    }

    @Test
    fun `a toplevel outside the guest workspace is kept as Git reported it`() {
        // S5.6b mounts the repository Git needs; today such a path cannot be mapped.
        val repository = GitInspectParser.parse(workspace, output(topLevel = "/root/elsewhere\n")).repository!!
        assertEquals("/root/elsewhere", repository.workTreePath)
    }

    @Test
    fun `reads branch, upstream, refs, remote, identity and changes`() {
        val snapshot = GitInspectParser.parse(
            workspace,
            output(
                status = status(
                    "# branch.oid 7d466e4c47d969352f2bc7f569d10ef8b72d2170",
                    "# branch.head main",
                    "# branch.upstream origin/main",
                    "# branch.ab +2 -1",
                    "1 MM N... 100644 100644 100644 f384549cbeb481e437091320de6d1f2e15e11b4a 6addb9b7c753aa12cdc6c0f22d7c7fe4b43ec8eb kept.txt",
                    "? untracked file.txt",
                ),
                refs = "refs/heads/feature\nrefs/heads/main\nrefs/remotes/origin/HEAD\nrefs/remotes/origin/main\nrefs/remotes/upstream/dev\n",
                remotes = "\norigin\nupstream\n",
                name = "Ada Lovelace\n",
                email = " ada@example.com \n",
            ),
        )
        val repository = snapshot.repository!!
        assertEquals("main", repository.branch)
        assertTrue(repository.hasCommits)
        assertEquals("origin/main", repository.upstream)
        assertEquals(2, repository.ahead)
        assertEquals(1, repository.behind)
        assertEquals(listOf("feature", "main"), repository.branches)
        // origin/HEAD is a pointer, not a branch to check out.
        assertEquals(listOf("origin/main", "upstream/dev"), repository.remoteBranches)
        assertEquals("origin", repository.remoteName)
        assertTrue(repository.hasRemote)
        assertEquals("Ada Lovelace", repository.authorName)
        assertEquals("ada@example.com", repository.authorEmail)
        assertEquals(GitInProgress.None, repository.inProgress)
        assertEquals(3, snapshot.changes.size)
        assertEquals(GitStatusCodes.UNTRACKED, snapshot.changes.single { it.path == "untracked file.txt" }.status)
    }

    @Test
    fun `blank identity and no remote read as unset`() {
        val repository = GitInspectParser.parse(workspace, output(name = "\n", email = "  \n", remotes = "\n")).repository!!
        assertNull(repository.authorName)
        assertNull(repository.authorEmail)
        assertNull(repository.remoteName)
        assertFalse(repository.hasRemote)
    }

    @Test
    fun `the last commit is shortened and timed in milliseconds`() {
        val commit = GitInspectParser.parse(
            workspace,
            output(lastCommit = "7d466e4c47d969352f2bc7f569d10ef8b72d2170${unit}Fix: keep spaces here${unit}Ada <ada@example.com>${unit}1727500000\n"),
        ).repository!!.lastCommit!!
        assertEquals("7d466e4c47d9", commit.id)
        assertEquals("Fix: keep spaces here", commit.message)
        assertEquals("Ada <ada@example.com>", commit.author)
        assertEquals(1_727_500_000_000L, commit.authoredAtEpochMillis)
    }

    @Test
    fun `an incomplete last commit is absent and an unreadable time is zero`() {
        assertNull(GitInspectParser.parse(workspace, output(lastCommit = "abc${unit}only two\n")).repository!!.lastCommit)
        val commit = GitInspectParser.parse(workspace, output(lastCommit = "abc${unit}m${unit}a${unit}soon\n")).repository!!.lastCommit!!
        assertEquals(0L, commit.authoredAtEpochMillis)
    }

    @Test
    fun `an unborn repository has no commits and its branch from the header`() {
        val repository = GitInspectParser.parse(
            workspace,
            output(status = status("# branch.oid (initial)", "# branch.head trunk")),
        ).repository!!
        assertFalse(repository.hasCommits)
        assertEquals("trunk", repository.branch)
        assertEquals(listOf("trunk"), repository.branches)
        assertNull(repository.lastCommit)
    }

    @Test
    fun `detached HEAD currently shows the first local branch`() {
        // S5.6c replaces this with an explicit detached state.
        val repository = GitInspectParser.parse(
            workspace,
            output(
                status = status("# branch.oid 7d466e4c47d969352f2bc7f569d10ef8b72d2170", "# branch.head (detached)"),
                refs = "refs/heads/feature\nrefs/heads/main\n",
            ),
        ).repository!!
        assertEquals("feature", repository.branch)
    }

    @Test
    fun `missing sections fall back to the default branch`() {
        val repository = GitInspectParser.parse(workspace, "/workspace/app\n").repository!!
        assertEquals(GitInspectParser.DEFAULT_BRANCH, repository.branch)
        assertEquals(listOf(GitInspectParser.DEFAULT_BRANCH), repository.branches)
        assertFalse(repository.hasCommits)
        assertNull(repository.lastCommit)
    }

    @Test
    fun `each marker file names the operation in progress`() {
        val expected = mapOf(
            "MERGE_HEAD" to GitInProgress.Merge,
            "rebase-merge" to GitInProgress.Rebase,
            "rebase-apply" to GitInProgress.Rebase,
            "CHERRY_PICK_HEAD" to GitInProgress.CherryPick,
            "REVERT_HEAD" to GitInProgress.Revert,
            "BISECT_LOG" to GitInProgress.Bisect,
        )
        expected.forEach { (marker, operation) ->
            assertEquals(marker, operation, GitInspectParser.parse(workspace, output(markers = "$marker\n")).repository!!.inProgress)
        }
        // A rebase stops on merges of its own; the rebase is what must be ended.
        assertEquals(
            GitInProgress.Rebase,
            GitInspectParser.parse(workspace, output(markers = "MERGE_HEAD\nrebase-merge\n")).repository!!.inProgress,
        )
    }

    @Test
    fun `the script prints eight sections and exits with status's code`() {
        assertEquals(7, Regex("""printf '\\036'""").findAll(GitInspectParser.SCRIPT).count())
        assertTrue(GitInspectParser.SCRIPT.endsWith("exit \$S"))
        assertTrue(GitInspectParser.SCRIPT.contains("status --porcelain=v2 --branch -z"))
    }
}
