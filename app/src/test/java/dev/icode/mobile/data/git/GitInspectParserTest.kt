package dev.icode.mobile.data.git

import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.domain.GitCommitSignature
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitSignatureStatus
import dev.icode.mobile.domain.GitStatusCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

/**
 * What each section of the inspect script means. The status section reuses
 * the porcelain format captured from real `git` in [GitPorcelainParserTest].
 */
class GitInspectParserTest {

    private val root = "/data/data/dev.icode.mobile/files/workspaces"
    private val workspace = "$root/app"
    private val paths = WorkspacePaths(File(root))
    private val nul = "\u0000"

    private fun parse(output: String) = GitInspectParser.parse(output, paths::locationOf)

    /** The script's sections in its order; status is last and runs to the end. */
    private fun output(
        topLevel: String = "/workspace/app\n",
        status: String = "",
        refs: String = "",
        remotes: String = "",
        name: String = "",
        email: String = "",
        lastCommit: String = "",
        markers: String = "",
    ) = listOf(topLevel, refs, remotes, name, email, lastCommit, markers).joinToString("") { it + nul } + status

    private fun status(vararg records: String) = records.joinToString(nul, postfix = nul)

    @Test
    fun `no toplevel means no repository`() {
        val empty = parse("")
        assertNull(empty.repository)
        assertTrue(empty.changes.isEmpty())
        assertNull(parse(output(topLevel = "\n")).repository)
    }

    @Test
    fun `a repository at the opened folder is reported at its location`() {
        val repository = parse(output()).repository!!
        assertEquals(workspace, repository.workTreePath)
    }

    @Test
    fun `a toplevel below the workspace is translated to its location`() {
        val repository = parse(output(topLevel = "/workspace/app/inner\n")).repository!!
        assertEquals("$workspace/inner", repository.workTreePath)
    }

    @Test
    fun `a toplevel outside the workspaces is not this workspace's repository`() {
        // A repository made at the runtime's own `/` holds the workspace but none of its files.
        assertNull(parse(output(topLevel = "/\n")).repository)
        assertNull(parse(output(topLevel = "/root/elsewhere\n")).repository)
    }

    @Test
    fun `reads branch, upstream, refs, remote, identity and changes`() {
        val snapshot = parse(
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
        val repository = parse(output(name = "\n", email = "  \n", remotes = "\n")).repository!!
        assertNull(repository.authorName)
        assertNull(repository.authorEmail)
        assertNull(repository.remoteName)
        assertFalse(repository.hasRemote)
    }

    @Test
    fun `the last commit is shortened and timed in milliseconds`() {
        val commit = parse(
            output(lastCommit = "7d466e4c47d969352f2bc7f569d10ef8b72d2170\n1727500000\nAda <ada@example.com>\n-\n\n\nFix: keep spaces here\n"),
        ).repository!!.lastCommit!!
        assertEquals("7d466e4c47d9", commit.id)
        assertEquals("Fix: keep spaces here", commit.message)
        assertEquals("Ada <ada@example.com>", commit.author)
        assertEquals(1_727_500_000_000L, commit.authoredAtEpochMillis)
        assertNull(commit.signature)
    }

    @Test
    fun `the last commit's signature is Git's verdict where GnuPG is installed to check it`() {
        val signed = parse(
            output(lastCommit = "abc\n1727500000\nAda <a@b.c>\nG\nBFDDD2AF39D6382D\nAda Lovelace <ada@example.com>\nfix: a\n"),
        ).repository!!.lastCommit!!
        assertEquals(
            GitCommitSignature(GitSignatureStatus.Verified, "Ada Lovelace <ada@example.com>", "BFDDD2AF39D6382D"),
            signed.signature,
        )
        assertEquals("fix: a", signed.message)
        val unsigned = parse(output(lastCommit = "abc\n1727500000\nAda <a@b.c>\nN\n\n\nfix: a\n")).repository!!.lastCommit!!
        assertEquals(GitCommitSignature(GitSignatureStatus.Unsigned, null, null), unsigned.signature)
    }

    @Test
    fun `an incomplete last commit is absent and an unreadable time is zero`() {
        assertNull(parse(output(lastCommit = "abc\nonly two\n")).repository!!.lastCommit)
        val commit = parse(output(lastCommit = "abc\nsoon\na\n-\n\n\nm\n")).repository!!.lastCommit!!
        assertEquals(0L, commit.authoredAtEpochMillis)
    }

    @Test
    fun `an unborn repository has no commits and its branch from the header`() {
        val repository = parse(
            output(status = status("# branch.oid (initial)", "# branch.head trunk")),
        ).repository!!
        assertFalse(repository.hasCommits)
        assertFalse(repository.detached)
        assertEquals("trunk", repository.branch)
        assertEquals(listOf("trunk"), repository.branches)
        assertNull(repository.lastCommit)
    }

    @Test
    fun `a detached HEAD is said the way Git says it, never as a branch`() {
        val detached = status("# branch.oid 7d466e4c47d969352f2bc7f569d10ef8b72d2170", "# branch.head (detached)")
        val repository = parse(
            output(status = detached, refs = "refs/heads/feature\nrefs/heads/main\n"),
        ).repository!!
        assertTrue(repository.detached)
        assertEquals("(HEAD detached at 7d466e4)", repository.branch)
        assertEquals(listOf("feature", "main"), repository.branches)
        // With no branch at all there is still none to list.
        assertEquals(emptyList<String>(), parse(output(status = detached)).repository!!.branches)
    }

    @Test
    fun `missing sections fall back to the default branch`() {
        val repository = parse("/workspace/app\n$nul").repository!!
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
            assertEquals(marker, operation, parse(output(markers = "$marker\n")).repository!!.inProgress)
        }
        // A rebase stops on merges of its own; the rebase is what must be ended.
        assertEquals(
            GitInProgress.Rebase,
            parse(output(markers = "MERGE_HEAD\nrebase-merge\n")).repository!!.inProgress,
        )
    }

    @Test
    fun `the script ends seven sections with NUL and finishes with status`() {
        assertEquals(7, "printf '\\0'".toRegex(RegexOption.LITERAL).findAll(GitInspectParser.SCRIPT).count())
        // Last, so the script's exit code is status's.
        assertTrue(GitInspectParser.SCRIPT.endsWith("status --porcelain=v2 --branch --untracked-files=all -z"))
    }

    @Test
    fun `no byte of a path can move a section boundary`() {
        // Legal names on Linux: a record separator, a unit separator, a newline.
        val hostile = "odd\u001Ename\u001F\nwith lines.txt"
        val snapshot = parse(
            output(
                status = status("# branch.oid (initial)", "# branch.head main", "? $hostile"),
                name = "Ada\n",
            ),
        )
        assertEquals(listOf(hostile), snapshot.changes.map { it.path })
        assertEquals("Ada", snapshot.repository!!.authorName)
    }

    @Test
    fun `a subject is kept whole, whatever it contains`() {
        val commit = parse(
            output(lastCommit = "abc\n1727500000\nAda <a@b.c>\n-\n\n\nfix: a\u001Fb \u001E c\n"),
        ).repository!!.lastCommit!!
        assertEquals("fix: a\u001Fb \u001E c", commit.message)
        assertEquals("Ada <a@b.c>", commit.author)
    }

    @Test
    fun `a repository folder ending in a space keeps it`() {
        val repository = parse(output(topLevel = "/workspace/app \n")).repository!!
        assertEquals("$root/app ", repository.workTreePath)
    }
}
