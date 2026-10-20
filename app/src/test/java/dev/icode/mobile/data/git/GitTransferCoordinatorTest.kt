package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.GitProgress
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.Said
import dev.icode.mobile.domain.said
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
    private val originalWriter = BackendLog.writer
    private val files = Files.createTempDirectory("files").toFile().canonicalFile
    private val workspaces = WorkspacePaths(File(files, "workspaces"))
    private val root = File(workspaces.root, "app").path

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
        files.deleteRecursively()
    }

    private inner class Harness(
        scope: CoroutineScope,
        val git: FakeTransfers,
        root: String? = this@GitTransferCoordinatorTest.root,
        /** Opening what was cloned waits for this, when given. */
        opening: CompletableDeferred<Unit>? = null,
    ) {
        val stateHolder = ICodeStateHolder()
        val events = mutableListOf<String>()
        /** What the progress screen showed while the clone was being opened. */
        val openedWhile = mutableListOf<GitOperationState?>()
        /** Commit & Push's commit: Git's refusal, or null to commit. */
        var commitFailure: String? = null
        val ledger = mutableListOf<String>()
        val coordinator = GitTransferCoordinator(
            stateHolder,
            scope,
            git,
            workspaces,
            activeRoot = { root },
            withCredentials = { block ->
                events += "credentials"
                block(listOf("credential.helper=store --file=/tmp/once"))
            },
            refresh = { events += "refresh $it" },
            openClone = {
                events += "open $it"
                openedWhile += stateHolder.current.gitOperation
                opening?.await()
            },
            commit = { message ->
                events += "commit $message while ${stateHolder.current.gitOperation}"
                commitFailure
            },
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
        harness.git.observe = { harness.stateHolder.current.gitOperation }
        val message = async { harness.stateHolder.effects.first() }
        yield()

        harness.coordinator.fetch()?.join()

        val running = harness.git.seen.first() as GitOperationState.Running
        assertEquals("Fetching", running.label)
        assertEquals(GitProgress("Receiving objects", 0.45f), running.progress)
        assertEquals(
            GitOperationState.Succeeded("Fetching", listOf("Receiving objects: 100% (100/100), done.", "Already up to date.")),
            harness.stateHolder.current.gitOperation,
        )
        assertEquals(listOf("credentials", "refresh $root"), harness.events)
        assertEquals(listOf("credential.helper=store --file=/tmp/once"), harness.git.config)
        assertEquals(Said(NoticeTone.Done, "Already up to date."), withTimeout(2_000) { message.await() }.said())
    }

    @Test fun `a failed pull keeps Git's reason and what it printed`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.git.stderr = listOf("fatal: Not possible to fast-forward, aborting.\n")
        harness.git.failure = IOException("Not possible to fast-forward, aborting.")

        harness.coordinator.pull()?.join()

        assertEquals(
            GitOperationState.Failed("Pulling", "Not possible to fast-forward, aborting.", listOf("fatal: Not possible to fast-forward, aborting.")),
            harness.stateHolder.current.gitOperation,
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

        val failed = harness.stateHolder.current.gitOperation as GitOperationState.Failed
        val shown = (failed.output + failed.reason).joinToString("\n")
        assertFalse(shown.contains("ghp_secretValue123"))
        assertTrue(shown.contains("https://***@github.com/ada/app.git/"))
        assertTrue(failed.output.all { it.length <= 401 })
    }

    @Test fun `a push without an upstream sets one for the current branch`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.stateHolder.update { it.copy(gitRepository = repository(upstream = null)) }

        harness.coordinator.push()?.join()

        assertEquals(listOf("push $root upstream=true branch=feature"), harness.git.calls)
    }

    @Test fun `a push with an upstream just pushes`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.stateHolder.update { it.copy(gitRepository = repository(upstream = "origin/feature")) }

        harness.coordinator.push()?.join()

        assertEquals(listOf("push $root upstream=false branch=feature"), harness.git.calls)
    }

    @Test fun `one transfer at a time, and the one asked second is told why`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val harness = Harness(this, FakeTransfers().apply { hold = gate })
        val fetching = harness.coordinator.fetch()
        yield()
        val message = async { harness.stateHolder.effects.first() }
        yield()

        val second = harness.coordinator.pull()

        assertEquals(null, second)
        assertEquals(Said(NoticeTone.HeadsUp, "Git is still fetching; try again when it finishes"), withTimeout(2_000) { message.await() }.said())
        gate.complete(Unit)
        fetching?.join()
        assertEquals(listOf("fetch $root"), harness.git.calls)
        assertTrue(harness.stateHolder.current.gitOperation is GitOperationState.Succeeded)
    }

    @Test fun `a cancelled clone stops, says so, and leaves nothing`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val harness = Harness(this, FakeTransfers().apply {
            hold = gate
            // Git has written part of the repository when it is stopped.
            beforeHold = { destination, directory -> File(destination, "$directory/.git").mkdirs() }
        })
        val cloning = harness.coordinator.clone("https://host/me/repo.git", "repo")
        while (harness.git.calls.isEmpty()) yield()

        harness.coordinator.cancel()
        cloning?.join()

        assertEquals(GitOperationState.Cancelled("Cloning"), harness.stateHolder.current.gitOperation)
        assertTrue("never opened", harness.events == listOf("credentials"))
        assertEquals("iCode's staging is gone with it", emptyList<String>(), workspaces.root.list()!!.toList())
    }

    @Test fun `every transfer is written down while it runs, and crossed off however it ends`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.coordinator.fetch()?.join()
        assertEquals(listOf("started Fetching /workspace/app", "finished"), harness.ledger)

        val failing = Harness(this, FakeTransfers().apply { failure = IOException("offline") })
        failing.coordinator.pull()?.join()
        assertEquals(listOf("started Pulling /workspace/app", "finished"), failing.ledger)

        val cancelled = Harness(this, FakeTransfers().apply { hold = CompletableDeferred() })
        val cloning = cancelled.coordinator.clone("https://host/me/repo.git", "repo")
        while (cancelled.git.calls.isEmpty()) yield()
        cancelled.coordinator.cancel()
        cloning?.join()
        assertEquals(listOf("started Cloning /workspace/repo", "finished"), cancelled.ledger)
    }

    @Test fun `a cancelled fetch is not left running`() = runBlocking {
        val harness = Harness(this, FakeTransfers().apply { hold = CompletableDeferred() })
        val fetching = harness.coordinator.fetch()
        while (harness.git.calls.isEmpty()) yield()

        harness.coordinator.cancel()
        fetching?.join()

        assertEquals(GitOperationState.Cancelled("Fetching"), harness.stateHolder.current.gitOperation)
        assertTrue(harness.events == listOf("credentials"))
    }

    @Test fun `a detached HEAD is not pushed`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.stateHolder.update { it.copy(gitRepository = repository(upstream = null).copy(detached = true)) }

        harness.coordinator.push()?.join()

        // Said where the push is followed: its page shows why it stopped.
        assertEquals(GitOperationState.Failed("Pushing", "Check out a branch first"), harness.stateHolder.current.gitOperation)
        assertTrue(harness.git.calls.isEmpty())
    }

    @Test fun `a sync without a workspace starts nothing`() = runBlocking {
        val harness = Harness(this, FakeTransfers(), root = null)

        harness.coordinator.fetch()?.join()

        assertTrue(harness.git.calls.isEmpty())
        assertEquals(GitOperationState.Failed("Fetching", "Open a workspace first"), harness.stateHolder.current.gitOperation)
    }

    @Test fun `a transfer is running from the moment it is accepted, not after the one before`() = runBlocking<Unit> {
        val harness = Harness(this, FakeTransfers())
        harness.stateHolder.update { it.copy(gitOperation = GitOperationState.Succeeded("Pushing", listOf("main -> main"))) }

        val fetching = harness.coordinator.fetch()

        // Before its job has run at all: the page opened with it follows this one.
        assertEquals(GitOperationState.Running("Fetching"), harness.stateHolder.current.gitOperation)
        fetching?.join()
    }

    @Test fun `commit and push commits what is staged, then pushes it, as one transfer`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.stateHolder.update { it.copy(gitRepository = repository(upstream = "origin/feature")) }

        harness.coordinator.handle(ICodeIntent.CommitAndPush("Fix the build"))?.join()

        assertEquals("commit Fix the build while ${GitOperationState.Running("Committing")}", harness.events.first())
        assertEquals(listOf("push $root upstream=false branch=feature"), harness.git.calls)
        assertTrue(harness.stateHolder.current.gitOperation is GitOperationState.Succeeded)
    }

    @Test fun `a commit Git refuses is not pushed`() = runBlocking {
        val harness = Harness(this, FakeTransfers())
        harness.stateHolder.update { it.copy(gitRepository = repository(upstream = "origin/feature")) }
        harness.commitFailure = "nothing added to commit"

        harness.coordinator.commitAndPush("Fix the build")?.join()

        assertEquals(GitOperationState.Failed("Committing", "nothing added to commit"), harness.stateHolder.current.gitOperation)
        assertTrue(harness.git.calls.isEmpty())
    }

    @Test fun `a clone installs Git first and stops when that fails`() = runBlocking {
        val harness = Harness(this, FakeTransfers().apply {
            available = false
            provisionOutcome = GitOutcome.Failure("E: Unable to fetch some archives", 100)
        })

        harness.coordinator.clone("https://host/me/repo.git", "repo")?.join()

        assertEquals(
            GitOperationState.Failed("Cloning", "E: Unable to fetch some archives", listOf("Installing Git…")),
            harness.stateHolder.current.gitOperation,
        )
        assertEquals(listOf("provision"), harness.git.calls)
        assertTrue(harness.events.isEmpty())
    }

    @Test fun `a named branch is cloned by name, into iCode's staging`() = runBlocking {
        val harness = Harness(this, FakeTransfers())

        harness.coordinator.clone(" https://host/me/repo.git ", "repo", branch = " release/2.0 ")?.join()

        val call = harness.git.calls.single()
        assertTrue(call, Regex("""clone https://host/me/repo\.git ${Regex.escape(workspaces.root.path)} \.clone-\d+ branch=release/2\.0""").matches(call))
    }

    @Test fun `a clone is refused before Git runs when it could only fail`() = runBlocking {
        File(workspaces.root, "taken").mkdirs()
        val cases = listOf(
            ("--upload-pack=evil" to "repo") to "An address cannot start with a dash",
            ("https://host/me/taken.git" to "taken") to "A workspace named taken already exists",
            ("https://host/me/.hidden.git" to ".hidden") to "A workspace name cannot start with a dot",
            ("https://host/" to "") to "The address does not name a repository folder",
        )
        cases.forEach { (request, reason) ->
            val harness = Harness(this, FakeTransfers())
            harness.coordinator.clone(request.first, request.second)?.join()
            assertEquals(GitOperationState.Failed("Cloning", reason), harness.stateHolder.current.gitOperation)
            assertTrue(harness.git.calls.isEmpty())
        }
    }

    @Test fun `a finished clone becomes the workspace and opens`() = runBlocking {
        val harness = Harness(this, FakeTransfers())

        harness.coordinator.clone("git@host:me/repo.git", "repo")?.join()

        assertEquals(GitOperationState.Succeeded("Cloning", emptyList()), harness.stateHolder.current.gitOperation)
        // Still running while it opens: the progress screen gives way to the clone, not to what was open.
        assertTrue(harness.openedWhile.single() is GitOperationState.Running)
        // A private repository needs the saved token for its first clone too.
        assertEquals(listOf("credentials", "open ${workspaces.root.path}/repo"), harness.events)
        assertEquals(listOf("credential.helper=store --file=/tmp/once"), harness.git.config)
        assertEquals(listOf("repo"), workspaces.root.list()!!.toList())
        assertTrue(File(workspaces.root, "repo/.git").isDirectory)
    }

    @Test fun `a clone already whole is not cancelled while it opens`() = runBlocking {
        val opening = CompletableDeferred<Unit>()
        val harness = Harness(this, FakeTransfers(), opening = opening)
        val cloning = harness.coordinator.clone("git@host:me/repo.git", "repo")
        while (harness.openedWhile.isEmpty()) yield()

        harness.coordinator.cancel()
        opening.complete(Unit)
        cloning?.join()

        assertEquals(GitOperationState.Succeeded("Cloning", emptyList()), harness.stateHolder.current.gitOperation)
        assertTrue(File(workspaces.root, "repo/.git").isDirectory)
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
        /** Where a clone writes, before it is held. */
        var beforeHold: (destination: String, directory: String) -> Unit = { _, _ -> }

        /** Reports stderr as it grows, the way the stderr file is tailed. */
        private suspend fun run(call: String, config: List<String>, onStderr: ((String) -> Unit)?, clone: Pair<String, String>? = null) {
            calls += call
            clone?.let { (destination, directory) -> beforeHold(destination, directory) }
            hold?.await()
            this.config = config
            var printed = ""
            stderr.forEach { chunk ->
                printed += chunk
                onStderr?.invoke(printed)
                seen += observe()
            }
            failure?.let { throw it }
            // As Git does: the repository is there once it succeeds.
            clone?.let { (destination, directory) -> File(destination, "$directory/.git").mkdirs() }
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
        ) = run("clone $url $destinationPath $directoryName branch=$branch", config, onStderr, destinationPath to directoryName)
    }
}
