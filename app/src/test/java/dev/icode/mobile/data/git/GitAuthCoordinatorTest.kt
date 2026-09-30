package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.GitAuthTestResult
import dev.icode.mobile.domain.GitAuthWork
import dev.icode.mobile.domain.GitIdentityRules
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.WorkbenchEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pins identity and credential handling as it leaves the gateway (S5.4). */
class GitAuthCoordinatorTest {
    private val root = "/workspace/app"
    private val originalWriter = BackendLog.writer

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    private class Harness(root: String? = "/workspace/app", repositoryOpen: Boolean = true) {
        val workbench = WorkbenchStateHolder()
        val config = FakeConfig()
        val credentials = FakeCredentials()
        val events = mutableListOf<String>()
        val coordinator = GitAuthCoordinator(
            workbench,
            config,
            credentials,
            activeRoot = { root },
            actInRepository = { operation, onSuccess ->
                events += "act"
                operation(root!!)
                onSuccess()
            },
            refreshRepository = { events += "refresh $it" },
        )

        init {
            if (repositoryOpen) workbench.update { it.copy(gitRepository = repository(root.orEmpty())) }
        }
    }

    private suspend fun Harness.nextMessage(block: suspend () -> Unit): WorkbenchEffect {
        val effect = kotlinx.coroutines.coroutineScope { async { workbench.effects.first() }.also { yield(); block() } }
        return withTimeout(2_000) { effect.await() }
    }

    @Test fun `the email rule accepts an address and refuses the rest`() {
        assertTrue(GitIdentityRules.isValidEmail("ada@example.com"))
        listOf("", "ada", "ada@example", "a da@example.com", "@example.com").forEach {
            assertFalse(it, GitIdentityRules.isValidEmail(it))
        }
    }

    @Test fun `a default identity is checked before Git is asked`() = runBlocking {
        val harness = Harness()
        val effect = harness.nextMessage { harness.coordinator.configureGlobalIdentity("Ada", "not-an-email") }
        assertEquals(WorkbenchEffect.Message("Enter a name and valid Git author email"), effect)
        assertTrue(harness.config.values.isEmpty())
    }

    @Test fun `a default identity needs Git in the runtime`() = runBlocking {
        val harness = Harness().apply { config.available = false }
        val effect = harness.nextMessage { harness.coordinator.configureGlobalIdentity("Ada", "ada@example.com") }
        assertEquals(WorkbenchEffect.Message("Install Git in the runtime first"), effect)
    }

    @Test fun `a default identity is saved trimmed and read back for every repository`() = runBlocking {
        val harness = Harness()

        // The form sends the email as typed; spaces inside it fail the rule, around the name they are trimmed.
        harness.coordinator.configureGlobalIdentity("  Ada  ", "ada@example.com")

        assertEquals("Ada", harness.config.values["Global user.name"])
        assertEquals("ada@example.com", harness.config.values["Global user.email"])
        assertEquals(listOf("refresh $root"), harness.events)
        assertEquals("Ada", harness.workbench.current.globalGitAuthorName)
        assertTrue(harness.workbench.current.gitIdentityLoaded)
    }

    @Test fun `a repository override is written in the repository and then shown`() = runBlocking {
        val harness = Harness()

        harness.coordinator.configureRepositoryIdentity("Ada", "ada@work.example")

        assertEquals(listOf("act"), harness.events)
        assertEquals("Ada", harness.workbench.current.localGitAuthorName)
        assertEquals("ada@work.example", harness.workbench.current.localGitAuthorEmail)
    }

    @Test fun `resetting the override leaves the default in place`() = runBlocking {
        val harness = Harness()
        harness.config.values += mapOf(
            "Global user.name" to "Ada",
            "Local user.name $root" to "Work Ada",
            "Local user.email $root" to "ada@work.example",
        )

        harness.coordinator.clearRepositoryIdentity()

        assertNull(harness.workbench.current.localGitAuthorName)
        assertNull(harness.workbench.current.localGitAuthorEmail)
        assertEquals("Ada", harness.workbench.current.globalGitAuthorName)
        assertEquals(listOf("refresh $root"), harness.events)
    }

    @Test fun `without an open repository only the default identity is read`() = runBlocking {
        val harness = Harness(repositoryOpen = false)
        harness.config.values["Local user.name $root"] = "Stale"

        harness.coordinator.refresh()

        assertNull(harness.workbench.current.localGitAuthorName)
    }

    @Test fun `a saved token is described, never kept in state`() = runBlocking {
        val harness = Harness()

        harness.coordinator.saveToken("github.com", "ada", "ghp_secretValue123")

        assertEquals("ada@github.com", harness.workbench.current.gitAuth.httpsAccount)
        assertFalse(harness.workbench.current.toString().contains("ghp_secretValue123"))
    }

    @Test fun `a refused token is said and the surface still refreshes`() = runBlocking {
        val harness = Harness()

        val effect = harness.nextMessage { harness.coordinator.saveToken("github.com", "ada", "") }

        assertEquals(WorkbenchEffect.Message("Enter a personal access token"), effect)
        assertNull(harness.workbench.current.gitAuth.httpsAccount)
    }

    @Test fun `a token that cannot be removed is said, and still shown`() = runBlocking {
        val harness = Harness()
        harness.coordinator.saveToken("github.com", "ada", "ghp_secretValue123")
        harness.credentials.clearFails = true

        val effect = harness.nextMessage { harness.coordinator.clearToken() }

        assertEquals(WorkbenchEffect.Message("Unable to remove the token"), effect)
        assertEquals("ada@github.com", harness.workbench.current.gitAuth.httpsAccount)
    }

    @Test fun `an SSH key needs the SSH client first, and apt's words say why it could not come`() = runBlocking {
        val harness = Harness().apply {
            credentials.clientInstall = GitOperationOutcome(false, "Could not download SSH client: E: Unable to fetch some archives")
        }

        val effect = harness.nextMessage { harness.coordinator.generateSshKey() }

        assertEquals(WorkbenchEffect.Message("Could not download SSH client: E: Unable to fetch some archives"), effect)
        assertNull(harness.workbench.current.gitAuth.work)
        assertFalse(harness.workbench.current.gitAuth.hasSshKey)
    }

    @Test fun `a connection test shows Git's answer and ends`() = runBlocking {
        val harness = Harness()

        harness.coordinator.testConnection("git@github.com:ada/repo.git")

        assertEquals(GitAuthTestResult(true, "Connected — 2 branch(es) visible"), harness.workbench.current.gitAuth.lastTest)
        assertNull(harness.workbench.current.gitAuth.work)
    }

    @Test fun `one thing at a time — while a key is generated it says so, and a test asked meanwhile is dropped`() = runBlocking {
        val harness = Harness().apply { credentials.clientGate = CompletableDeferred() }
        val generating = async(Dispatchers.Default) { harness.coordinator.generateSshKey() }
        withTimeout(2_000) { while (harness.workbench.current.gitAuth.work == null) delay(5) }
        assertEquals(GitAuthWork.GeneratingKey, harness.workbench.current.gitAuth.work)

        harness.coordinator.testConnection("git@github.com:ada/repo.git")
        assertNull("dropped", harness.workbench.current.gitAuth.lastTest)

        harness.credentials.clientGate!!.complete(Unit)
        withTimeout(2_000) { generating.await() }
        assertNull(harness.workbench.current.gitAuth.work)
        assertTrue(harness.workbench.current.gitAuth.hasSshKey)
    }

    private class FakeConfig : GitConfigOperations {
        var available = true
        val values = mutableMapOf<String, String>()
        private fun key(scope: GitConfigScope, key: String, workspace: String?) =
            if (scope == GitConfigScope.Local) "Local $key $workspace" else "Global $key"

        override suspend fun isAvailable() = available
        override suspend fun readConfig(scope: GitConfigScope, key: String, workspacePath: String?) = values[key(scope, key, workspacePath)]
        override suspend fun writeConfig(scope: GitConfigScope, key: String, value: String, workspacePath: String?) {
            values[key(scope, key, workspacePath)] = value
        }
        override suspend fun unsetConfig(scope: GitConfigScope, key: String, workspacePath: String?) {
            values.remove(key(scope, key, workspacePath))
        }
        override suspend fun configureIdentity(workspacePath: String, name: String, email: String) {
            writeConfig(GitConfigScope.Local, "user.name", name, workspacePath)
            writeConfig(GitConfigScope.Local, "user.email", email, workspacePath)
        }
    }

    private class FakeCredentials : GitCredentialOperations {
        private var account: String? = null
        var clientInstall = GitOperationOutcome(true, "The SSH client is installed")
        /** Held while the SSH client "installs". */
        var clientGate: CompletableDeferred<Unit>? = null
        /** As the store does when its write fails. */
        var clearFails = false
        private var key: String? = null

        override fun describeHttps() = account
        override fun saveHttps(host: String, username: String, token: String) {
            require(token.isNotBlank()) { "Enter a personal access token" }
            account = "$username@$host"
        }
        override suspend fun <T> withHttpsCredentials(block: suspend (config: List<String>) -> T): T = block(emptyList())
        override suspend fun clearHttpsCredentials() {
            check(!clearFails) { "Unable to remove the token" }
            account = null
        }
        override fun hasSshKey() = key != null
        override fun readSshPublicKey() = key
        override suspend fun ensureSshAvailable() = clientInstall.also { clientGate?.await() }
        override suspend fun generateSshKey(comment: String) = GitOperationOutcome(true, "SSH key created").also { key = "ssh-ed25519 AAAA $comment" }
        override suspend fun testConnection(url: String) = GitOperationOutcome(true, "Connected — 2 branch(es) visible")
    }

    private companion object {
        fun repository(root: String) = GitRepositoryState(
            workTreePath = root,
            branch = "main",
            hasCommits = true,
            remoteName = "origin",
            authorName = null,
            authorEmail = null,
            branches = listOf("main"),
            lastCommit = null,
        )
    }
}
