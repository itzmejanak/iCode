package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.GitProgress
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.WorkbenchEffect
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pins how clone, fetch, pull and push behave as they leave the gateway (S5.3). */
class GitTransferCoordinatorTest {
    private val root = "/workspace/app"
    private val originalWriter = BackendLog.writer

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    private class Harness(val git: FakeTransfers, root: String? = "/workspace/app") {
        val workbench = WorkbenchStateHolder()
        val events = mutableListOf<String>()
        val coordinator = GitTransferCoordinator(
            workbench,
            git,
            activeRoot = { root },
            withCredentials = { block ->
                events += "credentials"
                block(listOf("credential.helper=store --file=/tmp/once"))
            },
            refresh = { events += "refresh $it" },
            openClone = { events += "open $it" },
        )
    }

    @Test fun `a fetch shows Git's progress while it runs and its own last word after`() = runBlocking {
        val harness = Harness(FakeTransfers())
        harness.git.stderr = listOf("Receiving objects:  45% (45/100)\r", "Receiving objects: 100% (100/100), done.\nAlready up to date.\n")
        harness.git.observe = { harness.workbench.current.gitOperation }
        val message = async { harness.workbench.effects.first() }
        yield()

        harness.coordinator.fetch()

        val running = harness.git.seen.first() as GitOperationState.Running
        assertEquals("Fetching", running.label)
        assertEquals(GitProgress("Receiving objects", 0.45f), running.progress)
        assertEquals(
            GitOperationState.Succeeded("Fetching", listOf("Receiving objects: 100% (100/100), done.", "Already up to date.")),
            harness.workbench.current.gitOperation,
        )
        assertEquals(listOf("credentials", "refresh $root"), harness.events)
        assertEquals(listOf("credential.helper=store --file=/tmp/once"), harness.git.config)
        assertEquals(WorkbenchEffect.Message("Already up to date."), withTimeout(2_000) { message.await() })
    }

    @Test fun `a failed pull keeps Git's reason and what it printed`() = runBlocking {
        val harness = Harness(FakeTransfers())
        harness.git.stderr = listOf("fatal: Not possible to fast-forward, aborting.\n")
        harness.git.failure = IOException("Not possible to fast-forward, aborting.")

        harness.coordinator.pull()

        assertEquals(
            GitOperationState.Failed("Pulling", "Not possible to fast-forward, aborting.", listOf("fatal: Not possible to fast-forward, aborting.")),
            harness.workbench.current.gitOperation,
        )
        assertEquals(listOf("credentials"), harness.events)
    }

    @Test fun `a push without an upstream sets one for the current branch`() = runBlocking {
        val harness = Harness(FakeTransfers())
        harness.workbench.update { it.copy(gitRepository = repository(upstream = null)) }

        harness.coordinator.push()

        assertEquals(listOf("push $root upstream=true branch=feature"), harness.git.calls)
    }

    @Test fun `a push with an upstream just pushes`() = runBlocking {
        val harness = Harness(FakeTransfers())
        harness.workbench.update { it.copy(gitRepository = repository(upstream = "origin/feature")) }

        harness.coordinator.push()

        assertEquals(listOf("push $root upstream=false branch=feature"), harness.git.calls)
    }

    @Test fun `a detached HEAD is not pushed`() = runBlocking {
        val harness = Harness(FakeTransfers())
        harness.workbench.update { it.copy(gitRepository = repository(upstream = null).copy(detached = true)) }
        val message = async { harness.workbench.effects.first() }
        yield()

        harness.coordinator.push()

        assertEquals(WorkbenchEffect.Message("HEAD is detached. Check out a branch before pushing"), withTimeout(2_000) { message.await() })
        assertTrue(harness.git.calls.isEmpty())
    }

    @Test fun `a sync without a workspace starts nothing`() = runBlocking {
        val harness = Harness(FakeTransfers(), root = null)
        val message = async { harness.workbench.effects.first() }
        yield()

        harness.coordinator.fetch()

        assertEquals(WorkbenchEffect.Message("Open a workspace first"), withTimeout(2_000) { message.await() })
        assertTrue(harness.git.calls.isEmpty())
        assertEquals(GitOperationState.Idle, harness.workbench.current.gitOperation)
    }

    @Test fun `a clone installs Git first and stops when that fails`() = runBlocking {
        val harness = Harness(FakeTransfers().apply {
            available = false
            provisionOutcome = GitOutcome.Failure("E: Unable to fetch some archives", 100)
        })

        harness.coordinator.clone("https://host/me/repo.git", "/storage/Projects", "repo")

        assertEquals(
            GitOperationState.Failed("Cloning", "E: Unable to fetch some archives", listOf("Installing Git…")),
            harness.workbench.current.gitOperation,
        )
        assertEquals(listOf("provision"), harness.git.calls)
        assertTrue(harness.events.isEmpty())
    }

    @Test fun `a finished clone opens where Git put it`() = runBlocking {
        val harness = Harness(FakeTransfers())

        harness.coordinator.clone("git@host:me/repo.git", "/storage/Projects", "repo")

        assertEquals(listOf("clone git@host:me/repo.git /storage/Projects repo"), harness.git.calls)
        assertEquals(GitOperationState.Succeeded("Cloning", emptyList()), harness.workbench.current.gitOperation)
        // A private repository needs the saved token for its first clone too.
        assertEquals(listOf("credentials", "open /storage/Projects/repo"), harness.events)
        assertEquals(listOf("credential.helper=store --file=/tmp/once"), harness.git.config)
    }

    private fun repository(upstream: String?) = GitRepositoryState(
        workTreePath = root,
        branch = "feature",
        hasCommits = true,
        remoteName = "origin",
        authorName = null,
        authorEmail = null,
        branches = listOf("feature"),
        lastCommit = null,
        upstream = upstream,
    )

    private class FakeTransfers : GitTransferOperations {
        val calls = mutableListOf<String>()
        val seen = mutableListOf<GitOperationState>()
        var available = true
        var provisionOutcome: GitOutcome<Unit> = GitOutcome.Success(Unit)
        var stderr = emptyList<String>()
        var failure: Exception? = null
        var observe: () -> GitOperationState = { GitOperationState.Idle }
        var config = emptyList<String>()

        /** Reports stderr as it grows, the way the stderr file is tailed. */
        private fun run(call: String, config: List<String>, onStderr: ((String) -> Unit)?) {
            calls += call
            this.config = config
            var printed = ""
            stderr.forEach { chunk ->
                printed += chunk
                onStderr?.invoke(printed)
                seen += observe()
            }
            failure?.let { throw it }
        }

        override suspend fun isAvailable() = available
        override suspend fun provision(): GitOutcome<Unit> = provisionOutcome.also { calls += "provision" }
        override suspend fun fetch(workspacePath: String, config: List<String>, onStderr: ((String) -> Unit)?) =
            run("fetch $workspacePath", config, onStderr)
        override suspend fun pull(workspacePath: String, config: List<String>, onStderr: ((String) -> Unit)?) =
            run("pull $workspacePath", config, onStderr)
        override suspend fun push(workspacePath: String, setUpstream: Boolean, branch: String, config: List<String>, onStderr: ((String) -> Unit)?) =
            run("push $workspacePath upstream=$setUpstream branch=$branch", config, onStderr)
        override suspend fun clone(url: String, destinationPath: String, directoryName: String, config: List<String>, onStderr: ((String) -> Unit)?) =
            run("clone $url $destinationPath $directoryName", config, onStderr)
    }
}
