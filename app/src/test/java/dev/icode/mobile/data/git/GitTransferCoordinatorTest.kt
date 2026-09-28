package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.GitProgress
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.WorkbenchEffect
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pins how clone, fetch, pull and push behave as they leave the gateway (S5.3). */
class GitTransferCoordinatorTest {
    private val root = "/workspace/app"
    private val originalWriter = BackendLog.writer
    private val destination = Files.createTempDirectory("projects").toFile()

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
        destination.deleteRecursively()
    }

    private class Harness(scope: CoroutineScope, val git: FakeTransfers, root: String? = "/workspace/app") {
        val workbench = WorkbenchStateHolder()
        val events = mutableListOf<String>()
        val ledger = mutableListOf<String>()
        val coordinator = GitTransferCoordinator(
            workbench,
            scope,
            git,
            activeRoot = { root },
            withCredentials = { block ->
                events += "credentials"
                block(listOf("credential.helper=store --file=/tmp/once"))
            },
            refresh = { events += "refresh $it" },
            openClone = { events += "open $it" },
            ledger = object : TransferLedger {
                override fun started(label: String, target: String) {
                    ledger += "started $label $target"
                }
                override fun finished() {
                    ledger += "finished"
                }
            },
        )
    }

    @Test fun `a fetch shows Git's progress while it runs and its own last word after`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.git.stderr = listOf("Receiving objects:  45% (45/100)\r", "Receiving objects: 100% (100/100), done.\nAlready up to date.\n")
        harness.git.observe = { harness.workbench.current.gitOperation }
        val message = async { harness.workbench.effects.first() }
        yield()

        harness.coordinator.fetch()?.join()

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
        val harness = Harness(this, FakeTransfers())
        harness.git.stderr = listOf("fatal: Not possible to fast-forward, aborting.\n")
        harness.git.failure = IOException("Not possible to fast-forward, aborting.")

        harness.coordinator.pull()?.join()

        assertEquals(
            GitOperationState.Failed("Pulling", "Not possible to fast-forward, aborting.", listOf("fatal: Not possible to fast-forward, aborting.")),
            harness.workbench.current.gitOperation,
        )
        assertEquals(listOf("credentials"), harness.events)
    }

    @Test fun `no token Git echoes reaches the screen, and no line runs on`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.git.stderr = listOf(
            "fatal: unable to access 'https://ada:ghp_secretValue123@github.com/ada/app.git/': 403\n",
            "remote: " + "x".repeat(2_000) + "\n",
        )
        harness.git.failure = IOException("unable to access 'https://ada:ghp_secretValue123@github.com/ada/app.git/': 403")

        harness.coordinator.fetch()?.join()

        val failed = harness.workbench.current.gitOperation as GitOperationState.Failed
        val shown = (failed.output + failed.reason).joinToString("\n")
        assertFalse(shown.contains("ghp_secretValue123"))
        assertTrue(shown.contains("https://***@github.com/ada/app.git/"))
        assertTrue(failed.output.all { it.length <= 401 })
    }

    @Test fun `a push without an upstream sets one for the current branch`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.workbench.update { it.copy(gitRepository = repository(upstream = null)) }

        harness.coordinator.push()?.join()

        assertEquals(listOf("push $root upstream=true branch=feature"), harness.git.calls)
    }

    @Test fun `a push with an upstream just pushes`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.workbench.update { it.copy(gitRepository = repository(upstream = "origin/feature")) }

        harness.coordinator.push()?.join()

        assertEquals(listOf("push $root upstream=false branch=feature"), harness.git.calls)
    }

    @Test fun `one transfer at a time, and the one asked second is told why`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val harness = Harness(this, FakeTransfers().apply { hold = gate })
        val fetching = harness.coordinator.fetch()
        yield()
        val message = async { harness.workbench.effects.first() }
        yield()

        val second = harness.coordinator.pull()

        assertEquals(null, second)
        assertEquals(WorkbenchEffect.Message("Git is still fetching; try again when it finishes"), withTimeout(2_000) { message.await() })
        gate.complete(Unit)
        fetching?.join()
        assertEquals(listOf("fetch $root"), harness.git.calls)
        assertTrue(harness.workbench.current.gitOperation is GitOperationState.Succeeded)
    }

    @Test fun `a cancelled clone stops, says so, and names what Git left`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val harness = Harness(this, FakeTransfers().apply {
            hold = gate
            // Git has written part of the repository when it is stopped.
            beforeHold = { File(destination, "repo/.git").mkdirs() }
        })
        val cloning = harness.coordinator.clone("https://host/me/repo.git", destination.path, "repo")
        while (harness.git.calls.isEmpty()) yield()

        harness.coordinator.cancel()
        cloning?.join()

        assertEquals(
            GitOperationState.Cancelled("Cloning", emptyList(), leftover = File(destination, "repo").path),
            harness.workbench.current.gitOperation,
        )
        assertTrue("never opened", harness.events == listOf("credentials"))
        assertTrue("never deleted", File(destination, "repo/.git").isDirectory)
    }

    @Test fun `every transfer is written down while it runs, and crossed off however it ends`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.coordinator.fetch()?.join()
        assertEquals(listOf("started Fetching $root", "finished"), harness.ledger)

        val failing = Harness(this, FakeTransfers().apply { failure = IOException("offline") })
        failing.coordinator.pull()?.join()
        assertEquals(listOf("started Pulling $root", "finished"), failing.ledger)

        val cancelled = Harness(this, FakeTransfers().apply { hold = CompletableDeferred() })
        val cloning = cancelled.coordinator.clone("https://host/me/repo.git", destination.path, "repo")
        while (cancelled.git.calls.isEmpty()) yield()
        cancelled.coordinator.cancel()
        cloning?.join()
        assertEquals(listOf("started Cloning ${File(destination, "repo").path}", "finished"), cancelled.ledger)
    }

    @Test fun `a cancelled fetch is not left running`() = runBlocking {
        val harness = Harness(this, FakeTransfers().apply { hold = CompletableDeferred() })
        val fetching = harness.coordinator.fetch()
        while (harness.git.calls.isEmpty()) yield()

        harness.coordinator.cancel()
        fetching?.join()

        assertEquals(GitOperationState.Cancelled("Fetching"), harness.workbench.current.gitOperation)
        assertTrue(harness.events == listOf("credentials"))
    }

    @Test fun `a detached HEAD is not pushed`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.workbench.update { it.copy(gitRepository = repository(upstream = null).copy(detached = true)) }
        val message = async { harness.workbench.effects.first() }
        yield()

        harness.coordinator.push()?.join()

        assertEquals(WorkbenchEffect.Message("HEAD is detached. Check out a branch before pushing"), withTimeout(2_000) { message.await() })
        assertTrue(harness.git.calls.isEmpty())
    }

    @Test fun `a sync without a workspace starts nothing`() = runBlocking {
        val harness = Harness(this, FakeTransfers(), root = null)
        val message = async { harness.workbench.effects.first() }
        yield()

        harness.coordinator.fetch()?.join()

        assertEquals(WorkbenchEffect.Message("Open a workspace first"), withTimeout(2_000) { message.await() })
        assertTrue(harness.git.calls.isEmpty())
        assertEquals(GitOperationState.Idle, harness.workbench.current.gitOperation)
    }

    @Test fun `a clone installs Git first and stops when that fails`() = runBlocking {
        val harness = Harness(this, FakeTransfers().apply {
            available = false
            provisionOutcome = GitOutcome.Failure("E: Unable to fetch some archives", 100)
        })

        harness.coordinator.clone("https://host/me/repo.git", destination.path, "repo")?.join()

        assertEquals(
            GitOperationState.Failed("Cloning", "E: Unable to fetch some archives", listOf("Installing Git…")),
            harness.workbench.current.gitOperation,
        )
        assertEquals(listOf("provision"), harness.git.calls)
        assertTrue(harness.events.isEmpty())
    }

    @Test fun `a named branch is cloned by name`() = runBlocking {
        val harness = Harness(this, FakeTransfers())

        harness.coordinator.clone(" https://host/me/repo.git ", destination.path, "repo", branch = " release/2.0 ")?.join()

        assertEquals(listOf("clone https://host/me/repo.git ${destination.path} repo branch=release/2.0"), harness.git.calls)
    }

    @Test fun `a clone is refused before Git runs when it could only fail`() = runBlocking {
        File(destination, "taken").mkdirs()
        File(destination, "taken/file.txt").writeText("mine")
        val cases = listOf(
            Triple("--upload-pack=evil", destination.path, "repo") to "An address cannot start with a dash",
            Triple("https://host/me/repo.git", "${destination.path}/missing", "repo") to "Choose the folder to clone into",
            Triple("https://host/me/taken.git", destination.path, "taken") to "taken already exists there. Choose another folder or remove it first",
        )
        cases.forEach { (request, reason) ->
            val harness = Harness(this, FakeTransfers())
            harness.coordinator.clone(request.first, request.second, request.third)?.join()
            assertEquals(GitOperationState.Failed("Cloning", reason), harness.workbench.current.gitOperation)
            assertTrue(harness.git.calls.isEmpty())
        }
        // An empty folder of that name is where Git may clone.
        File(destination, "empty").mkdirs()
        val harness = Harness(this, FakeTransfers())
        harness.coordinator.clone("https://host/me/empty.git", destination.path, "empty")?.join()
        assertEquals(1, harness.git.calls.size)
    }

    @Test fun `a finished clone opens where Git put it`() = runBlocking {
        val harness = Harness(this, FakeTransfers())

        harness.coordinator.clone("git@host:me/repo.git", destination.path, "repo")?.join()

        assertEquals(listOf("clone git@host:me/repo.git ${destination.path} repo branch=null"), harness.git.calls)
        assertEquals(GitOperationState.Succeeded("Cloning", emptyList()), harness.workbench.current.gitOperation)
        // A private repository needs the saved token for its first clone too.
        assertEquals(listOf("credentials", "open ${destination.path}/repo"), harness.events)
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
        var hold: CompletableDeferred<Unit>? = null
        var beforeHold: () -> Unit = {}

        /** Reports stderr as it grows, the way the stderr file is tailed. */
        private suspend fun run(call: String, config: List<String>, onStderr: ((String) -> Unit)?) {
            calls += call
            beforeHold()
            hold?.await()
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
        override suspend fun clone(
            url: String,
            destinationPath: String,
            directoryName: String,
            branch: String?,
            config: List<String>,
            onStderr: ((String) -> Unit)?,
        ) = run("clone $url $destinationPath $directoryName branch=$branch", config, onStderr)
    }
}
