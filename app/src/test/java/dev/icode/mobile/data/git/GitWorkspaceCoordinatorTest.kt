package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.DirectFileWorkspaceSource
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.data.workspace.WorkspaceTree
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.WorkbenchEffect
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Pins how local Git work behaves as it leaves the gateway (S5.2): same calls, same state, same messages. */
class GitWorkspaceCoordinatorTest {
    private val root = "/workspace/app"
    private val change = GitChange(GitStatusCodes.MODIFIED, "a.txt", "a.txt", staged = false)
    private val originalWriter = BackendLog.writer
    private val logged = mutableListOf<String>()

    @Before fun captureLog() {
        BackendLog.writer = { _, tag, message -> logged += "$tag ${message.lineSequence().first()}" }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    private fun repository(branch: String = "main") = GitRepositoryState(
        workTreePath = root,
        branch = branch,
        hasCommits = true,
        remoteName = null,
        authorName = null,
        authorEmail = null,
        branches = listOf(branch),
        lastCommit = null,
    )

    private class Harness(val git: FakeGit, var root: String?, val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) {
        val workbench = WorkbenchStateHolder().apply { update { it.copy(activeWorkspaceLocation = root) } }
        var installed = 0
        val coordinator = GitWorkspaceCoordinator(
            workbench,
            scope,
            git,
            WorkspaceTree(workbench, DirectFileWorkspaceSource()) { emptyList() },
            activeRoot = { root },
            recentWorkspaces = { emptyList() },
            recentDocuments = { emptyList() },
            onInstalled = { installed++ },
        )
    }

    @Test fun `an action runs in the open workspace and then shows what Git reports`() = runBlocking {
        val git = FakeGit().apply { snapshot = GitWorkspaceSnapshot(repository(), listOf(change)) }
        val harness = Harness(git, root)

        harness.coordinator.stage("a.txt")

        assertEquals(listOf("stage $root a.txt", "inspect $root"), git.calls)
        assertEquals(listOf(change), harness.workbench.current.gitChanges)
        assertEquals("main", harness.workbench.current.activeBranch)
        assertTrue(harness.workbench.current.gitAvailable == true)
    }

    @Test fun `a failed action is said once in Git's words and nothing is refreshed`() = runBlocking {
        val git = FakeGit().apply { failure = IOException("pathspec 'x' did not match any files") }
        val harness = Harness(git, root)
        val message = async { harness.workbench.effects.first() }
        yield()

        harness.coordinator.stage("x")

        assertEquals(WorkbenchEffect.Message("pathspec 'x' did not match any files"), withTimeout(2_000) { message.await() })
        assertEquals(listOf("iCode/git Git operation failed"), logged)
        assertEquals(listOf("stage $root x"), git.calls)
    }

    @Test fun `without an open workspace nothing runs`() = runBlocking {
        val git = FakeGit()
        val harness = Harness(git, root = null)
        val message = async { harness.workbench.effects.first() }
        yield()

        harness.coordinator.commit("message")

        assertEquals(WorkbenchEffect.Message("Open a workspace first"), withTimeout(2_000) { message.await() })
        assertTrue(git.calls.isEmpty())
    }

    @Test fun `a commit refreshes and then announces itself`() = runBlocking {
        val git = FakeGit().apply { snapshot = GitWorkspaceSnapshot(repository(), emptyList()) }
        val harness = Harness(git, root)
        val effect = async { harness.workbench.effects.first() }
        yield()

        harness.coordinator.commit("first")

        assertEquals(WorkbenchEffect.GitCommitted, withTimeout(2_000) { effect.await() })
        assertEquals(listOf("commit $root first", "inspect $root"), git.calls)
    }

    @Test fun `without Git a repository on disk still names its branch`() = runBlocking {
        val folder = Files.createTempDirectory("git-head").toFile()
        try {
            File(folder, ".git").mkdirs()
            File(folder, ".git/HEAD").writeText("ref: refs/heads/dev\n")
            val git = FakeGit().apply { available = false }

            val picture = Harness(git, folder.path).coordinator.read(folder.path)

            assertFalse(picture.available)
            assertNull(picture.state.repository)
            assertEquals("dev", picture.branch)
            assertTrue("inspect needs Git", git.calls.isEmpty())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun `installing Git reports apt's own failure`() = runBlocking {
        val git = FakeGit().apply {
            available = false
            provisionOutcome = GitOutcome.Failure("E: Unable to locate package git", 100)
        }
        val harness = Harness(git, root)

        harness.coordinator.provision()

        assertEquals(
            GitOperationState.Failed("Installing Git", "E: Unable to locate package git"),
            harness.workbench.current.gitOperation,
        )
        assertEquals(0, harness.installed)
    }

    @Test fun `installing Git refreshes credentials and the workspace`() = runBlocking {
        val git = FakeGit().apply {
            available = false
            provisionOutcome = GitOutcome.Success(Unit)
            onProvision = { available = true }
            snapshot = GitWorkspaceSnapshot(repository(), emptyList())
        }
        val harness = Harness(git, root)

        harness.coordinator.provision()

        assertEquals(GitOperationState.Succeeded("Git installed"), harness.workbench.current.gitOperation)
        assertEquals(1, harness.installed)
        assertEquals(listOf("provision", "inspect $root"), git.calls)
    }

    @Test fun `a file staged and edited again offers only its unstaged edit for discard`() = runBlocking {
        val harness = Harness(FakeGit(), root)
        val staged = change.copy(staged = true)
        harness.workbench.update { it.copy(gitChanges = listOf(staged, change)) }

        harness.coordinator.requestDiscard("a.txt")
        assertEquals(change, harness.workbench.current.pendingDiscard)

        harness.workbench.update { it.copy(gitChanges = listOf(staged)) }
        harness.coordinator.requestDiscard("a.txt")
        assertNull("staged work is unstaged first, never discarded", harness.workbench.current.pendingDiscard)
    }

    @Test fun `a confirmed discard runs only while Git still reports that change`() = runBlocking {
        val git = FakeGit().apply { snapshot = GitWorkspaceSnapshot(repository(), listOf(change)) }
        val harness = Harness(git, root)
        harness.workbench.update { it.copy(gitChanges = listOf(change)) }
        harness.coordinator.requestDiscard("a.txt")

        harness.coordinator.confirmDiscard()

        assertEquals(listOf("inspect $root", "discard $root a.txt M", "inspect $root"), git.calls)
        assertNull(harness.workbench.current.pendingDiscard)
    }

    @Test fun `a change that moved on after confirming is not discarded`() = runBlocking {
        // Confirmed as an edit; meanwhile the file was deleted from the terminal.
        val git = FakeGit().apply { snapshot = GitWorkspaceSnapshot(repository(), listOf(change.copy(status = GitStatusCodes.DELETED))) }
        val harness = Harness(git, root)
        harness.workbench.update { it.copy(gitChanges = listOf(change)) }
        harness.coordinator.requestDiscard("a.txt")
        val message = async { harness.workbench.effects.first() }
        yield()

        harness.coordinator.confirmDiscard()

        assertEquals(WorkbenchEffect.Message("a.txt changed after you confirmed. Review it again"), withTimeout(2_000) { message.await() })
        assertEquals(listOf("inspect $root"), git.calls)
    }

    @Test fun `nothing outside the repository or inside dot-git is discarded`() = runBlocking {
        listOf(".git/config", "../elsewhere.txt", "sub/.git", "/etc/passwd", "a/./b").forEach { path ->
            val git = FakeGit()
            val harness = Harness(git, root)
            harness.workbench.update { it.copy(pendingDiscard = GitChange(GitStatusCodes.MODIFIED, "x", path, staged = false)) }

            harness.coordinator.confirmDiscard()

            assertTrue(path, git.calls.isEmpty())
            assertNull(harness.workbench.current.pendingDiscard)
        }
    }

    @Test fun `the discard rule`() {
        assertTrue(change.discardable)
        assertTrue(change.copy(status = GitStatusCodes.UNTRACKED, path = "build/").discardable)
        assertFalse(change.copy(staged = true).discardable)
        assertFalse(change.copy(status = GitStatusCodes.CONFLICTED).discardable)
    }

    @Test fun `saves in a burst become one refresh`() = runBlocking {
        val git = FakeGit().apply { snapshot = GitWorkspaceSnapshot(repository(), emptyList()) }
        val harness = Harness(git, root)
        try {
            repeat(3) {
                harness.coordinator.scheduleRefresh()
                delay(50)
            }
            withTimeout(2_000) { harness.workbench.snapshot.first { it.gitAvailable == true } }
            delay(500)
            assertEquals(listOf("inspect $root"), git.calls)
        } finally {
            harness.scope.cancel()
        }
    }

    @Test fun `an answer for a workspace no longer open is dropped`() = runBlocking {
        val git = FakeGit().apply { snapshot = GitWorkspaceSnapshot(repository(), listOf(change)) }
        val harness = Harness(git, root)
        harness.workbench.update { it.copy(activeWorkspaceLocation = "/workspace/other") }

        harness.coordinator.refresh(root)

        assertTrue(harness.workbench.current.gitChanges.isEmpty())
        assertNull(harness.workbench.current.gitRepository)
    }

    @Test fun `a refresh waiting for a closing workspace never runs`() = runBlocking {
        val git = FakeGit().apply { snapshot = GitWorkspaceSnapshot(repository(), emptyList()) }
        val harness = Harness(git, root)
        try {
            harness.coordinator.scheduleRefresh()
            harness.coordinator.workspaceClosing()
            delay(600)
            assertTrue(git.calls.isEmpty())
        } finally {
            harness.scope.cancel()
        }
    }

    @Test fun `a remote branch is checked out by its full name`() = runBlocking {
        val git = FakeGit().apply { snapshot = GitWorkspaceSnapshot(repository("feature"), emptyList()) }
        val harness = Harness(git, root)

        harness.coordinator.checkoutRemote("upstream/team/feature")

        assertEquals(listOf("track $root upstream/team/feature", "inspect $root"), git.calls)
        assertEquals("feature", harness.workbench.current.activeBranch)
    }

    @Test fun `a remote is added only with a clean name and an address without an account`() = runBlocking {
        val git = FakeGit().apply { snapshot = GitWorkspaceSnapshot(repository(), emptyList()) }
        val harness = Harness(git, root)

        harness.coordinator.addRemote("origin", "https://ada:ghp_secret@github.com/ada/app.git")
        harness.coordinator.addRemote("-bad", "git@github.com:ada/app.git")
        assertTrue(git.calls.isEmpty())

        harness.coordinator.addRemote("origin", "git@github.com:ada/app.git")
        assertEquals(listOf("remote $root origin git@github.com:ada/app.git", "inspect $root"), git.calls)
    }

    @Test fun `abort ends the operation Git reported`() = runBlocking {
        val git = FakeGit().apply { snapshot = GitWorkspaceSnapshot(repository(), emptyList()) }
        val harness = Harness(git, root)
        harness.workbench.update { it.copy(gitRepository = repository().copy(inProgress = GitInProgress.Rebase)) }

        harness.coordinator.abort()

        assertEquals(listOf("abort $root Rebase", "inspect $root"), git.calls)
    }

    private class FakeGit : GitWorkspaceOperations {
        val calls = mutableListOf<String>()
        var available = true
        var snapshot = GitWorkspaceSnapshot(null, emptyList())
        var failure: Exception? = null
        var provisionOutcome: GitOutcome<Unit> = GitOutcome.Success(Unit)
        var onProvision: () -> Unit = {}

        private fun record(call: String) {
            calls += call
            failure?.let { throw it }
        }

        override suspend fun isAvailable() = available
        override suspend fun provision(): GitOutcome<Unit> = provisionOutcome.also { calls += "provision"; onProvision() }
        override suspend fun inspect(workspacePath: String) = snapshot.also { calls += "inspect $workspacePath" }
        override suspend fun initialize(workspacePath: String) = record("init $workspacePath")
        override suspend fun stage(workspacePath: String, path: String) = record("stage $workspacePath $path")
        override suspend fun unstage(workspacePath: String, path: String) = record("unstage $workspacePath $path")
        override suspend fun stageAll(workspacePath: String) = record("stageAll $workspacePath")
        override suspend fun unstageAll(workspacePath: String) = record("unstageAll $workspacePath")
        override suspend fun commit(workspacePath: String, message: String) = record("commit $workspacePath $message")
        override suspend fun switchBranch(workspacePath: String, branch: String) = record("switch $workspacePath $branch")
        override suspend fun createBranch(workspacePath: String, branch: String) = record("create $workspacePath $branch")
        override suspend fun deleteBranch(workspacePath: String, branch: String) = record("delete $workspacePath $branch")
        override suspend fun checkoutRemote(workspacePath: String, remoteBranch: String) = record("track $workspacePath $remoteBranch")
        override suspend fun addRemote(workspacePath: String, name: String, url: String) = record("remote $workspacePath $name $url")
        override suspend fun diff(workspacePath: String, path: String, staged: Boolean): GitFileDiff? {
            record("diff $workspacePath $path $staged")
            return null
        }
        override suspend fun discard(workspacePath: String, path: String, status: String) = record("discard $workspacePath $path $status")
        override suspend fun abortInProgress(workspacePath: String, inProgress: GitInProgress) = record("abort $workspacePath $inProgress")
    }
}
