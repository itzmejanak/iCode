package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.GitAuthTestResult
import dev.icode.mobile.domain.GitAuthWork
import dev.icode.mobile.domain.GitIdentityRules
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.GitSigningKey
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.SigningIntent
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
        val stateHolder = ICodeStateHolder()
        val config = FakeConfig()
        val credentials = FakeCredentials()
        val events = mutableListOf<String>()
        val coordinator = GitAuthCoordinator(
            stateHolder,
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
            if (repositoryOpen) stateHolder.update { it.copy(gitRepository = repository(root.orEmpty())) }
        }
    }

    private suspend fun Harness.nextMessage(block: suspend () -> Unit): ICodeEffect {
        val effect = kotlinx.coroutines.coroutineScope { async { stateHolder.effects.first() }.also { yield(); block() } }
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
        assertEquals(ICodeEffect.Message("Enter a name and valid Git author email"), effect)
        assertTrue(harness.config.values.isEmpty())
    }

    @Test fun `a default identity needs Git in the runtime`() = runBlocking {
        val harness = Harness().apply { config.available = false }
        val effect = harness.nextMessage { harness.coordinator.configureGlobalIdentity("Ada", "ada@example.com") }
        assertEquals(ICodeEffect.Message("Install Git in the runtime first"), effect)
    }

    @Test fun `a default identity is saved trimmed and read back for every repository`() = runBlocking {
        val harness = Harness()

        // The form sends the email as typed; spaces inside it fail the rule, around the name they are trimmed.
        harness.coordinator.configureGlobalIdentity("  Ada  ", "ada@example.com")

        assertEquals("Ada", harness.config.values["Global user.name"])
        assertEquals("ada@example.com", harness.config.values["Global user.email"])
        assertEquals(listOf("refresh $root"), harness.events)
        assertEquals("Ada", harness.stateHolder.current.globalGitAuthorName)
        assertTrue(harness.stateHolder.current.gitIdentityLoaded)
    }

    @Test fun `a repository override is written in the repository and then shown`() = runBlocking {
        val harness = Harness()

        harness.coordinator.configureRepositoryIdentity("Ada", "ada@work.example")

        assertEquals(listOf("act"), harness.events)
        assertEquals("Ada", harness.stateHolder.current.localGitAuthorName)
        assertEquals("ada@work.example", harness.stateHolder.current.localGitAuthorEmail)
    }

    @Test fun `resetting the override leaves the default in place`() = runBlocking {
        val harness = Harness()
        harness.config.values += mapOf(
            "Global user.name" to "Ada",
            "Local user.name $root" to "Work Ada",
            "Local user.email $root" to "ada@work.example",
        )

        harness.coordinator.clearRepositoryIdentity()

        assertNull(harness.stateHolder.current.localGitAuthorName)
        assertNull(harness.stateHolder.current.localGitAuthorEmail)
        assertEquals("Ada", harness.stateHolder.current.globalGitAuthorName)
        assertEquals(listOf("refresh $root"), harness.events)
    }

    @Test fun `without an open repository only the default identity is read`() = runBlocking {
        val harness = Harness(repositoryOpen = false)
        harness.config.values["Local user.name $root"] = "Stale"

        harness.coordinator.refresh()

        assertNull(harness.stateHolder.current.localGitAuthorName)
    }

    @Test fun `a saved token is described, never kept in state`() = runBlocking {
        val harness = Harness()

        harness.coordinator.saveToken("github.com", "ada", "ghp_secretValue123")

        assertEquals("ada@github.com", harness.stateHolder.current.gitAuth.httpsAccount)
        assertFalse(harness.stateHolder.current.toString().contains("ghp_secretValue123"))
    }

    @Test fun `a refused token is said and the surface still refreshes`() = runBlocking {
        val harness = Harness()

        val effect = harness.nextMessage { harness.coordinator.saveToken("github.com", "ada", "") }

        assertEquals(ICodeEffect.Message("Enter a personal access token"), effect)
        assertNull(harness.stateHolder.current.gitAuth.httpsAccount)
    }

    @Test fun `a token that cannot be removed is said, and still shown`() = runBlocking {
        val harness = Harness()
        harness.coordinator.saveToken("github.com", "ada", "ghp_secretValue123")
        harness.credentials.clearFails = true

        val effect = harness.nextMessage { harness.coordinator.clearToken() }

        assertEquals(ICodeEffect.Message("Unable to remove the token"), effect)
        assertEquals("ada@github.com", harness.stateHolder.current.gitAuth.httpsAccount)
    }

    @Test fun `an SSH key needs the SSH client first, and apt's words say why it could not come`() = runBlocking {
        val harness = Harness().apply {
            credentials.clientInstall = GitOperationOutcome(false, "Could not download SSH client: E: Unable to fetch some archives")
        }

        val effect = harness.nextMessage { harness.coordinator.generateSshKey() }

        assertEquals(ICodeEffect.Message("Could not download SSH client: E: Unable to fetch some archives"), effect)
        assertNull(harness.stateHolder.current.gitAuth.work)
        assertFalse(harness.stateHolder.current.gitAuth.hasSshKey)
    }

    @Test fun `a connection test shows Git's answer and ends`() = runBlocking {
        val harness = Harness()

        harness.coordinator.testConnection("git@github.com:ada/repo.git")

        assertEquals(GitAuthTestResult(true, "Connected — 2 branch(es) visible"), harness.stateHolder.current.gitAuth.lastTest)
        assertNull(harness.stateHolder.current.gitAuth.work)
    }

    @Test fun `one thing at a time — while a key is generated it says so, and a test asked meanwhile is dropped`() = runBlocking {
        val harness = Harness().apply { credentials.clientGate = CompletableDeferred() }
        val generating = async(Dispatchers.Default) { harness.coordinator.generateSshKey() }
        withTimeout(2_000) { while (harness.stateHolder.current.gitAuth.work == null) delay(5) }
        assertEquals(GitAuthWork.GeneratingKey, harness.stateHolder.current.gitAuth.work)

        harness.coordinator.testConnection("git@github.com:ada/repo.git")
        assertNull("dropped", harness.stateHolder.current.gitAuth.lastTest)

        harness.credentials.clientGate!!.complete(Unit)
        withTimeout(2_000) { generating.await() }
        assertNull(harness.stateHolder.current.gitAuth.work)
        assertTrue(harness.stateHolder.current.gitAuth.hasSshKey)
    }

    @Test fun `a signing key is made for the default identity, and Git then signs every commit with it, by OpenPGP`() = runBlocking {
        val harness = Harness()
        harness.config.values["Global user.name"] = "Ada"
        harness.config.values["Global user.email"] = "ada@example.com"
        harness.config.values["Global gpg.format"] = "ssh"
        harness.coordinator.handle(SigningIntent.CreateKey)
        assertEquals(listOf("Ada <ada@example.com>"), harness.credentials.created)
        val made = harness.credentials.keys.single()
        assertEquals(made.fingerprint, harness.config.values["Global user.signingkey"])
        assertEquals("true", harness.config.values["Global commit.gpgsign"])
        assertEquals("openpgp", harness.config.values["Global gpg.format"])
        val signing = harness.stateHolder.current.gitAuth.signing
        assertEquals(made, signing.key)
        assertTrue(signing.signsCommits)
        assertEquals("-----BEGIN PGP PUBLIC KEY BLOCK----- ${made.fingerprint}", signing.publicKey)
        assertNull(harness.stateHolder.current.gitAuth.work)
    }

    @Test fun `no key is made without a default identity, and gpg's refusal is said in its words`() = runBlocking {
        val harness = Harness()
        assertEquals(
            ICodeEffect.Message("Save a default commit identity first"),
            harness.nextMessage { harness.coordinator.handle(SigningIntent.CreateKey) },
        )
        assertTrue(harness.credentials.created.isEmpty())
        harness.config.values["Global user.name"] = "Ada"
        harness.config.values["Global user.email"] = "ada@example.com"
        harness.credentials.creation = "key generation failed: Operation cancelled"
        assertEquals(
            ICodeEffect.Message("key generation failed: Operation cancelled"),
            harness.nextMessage { harness.coordinator.handle(SigningIntent.CreateKey) },
        )
        assertNull(harness.config.values["Global commit.gpgsign"])
    }

    @Test fun `signing turned on uses the key shown, another key can be chosen, and off is Git's default again`() = runBlocking {
        val harness = Harness()
        val first = signingKey("A".repeat(24) + "1111111111111111", "Ada <ada@example.com>")
        val second = signingKey("B".repeat(24) + "2222222222222222", "Ada <ada@work.example>")
        harness.credentials.keys += listOf(first, second)
        harness.coordinator.refresh()
        assertNull(harness.stateHolder.current.gitAuth.signing.key)
        harness.coordinator.handle(SigningIntent.SignCommits(true))
        assertEquals(first.fingerprint, harness.config.values["Global user.signingkey"])
        assertEquals("true", harness.config.values["Global commit.gpgsign"])
        harness.coordinator.handle(SigningIntent.UseKey(second.fingerprint))
        assertEquals(second, harness.stateHolder.current.gitAuth.signing.key)
        harness.coordinator.handle(SigningIntent.SignCommits(false))
        assertNull(harness.config.values["Global commit.gpgsign"])
        assertFalse(harness.stateHolder.current.gitAuth.signing.signsCommits)
    }

    @Test fun `a passphrase is changed for the key named, and that is said`() = runBlocking {
        val harness = Harness()
        val effect = harness.nextMessage { harness.coordinator.handle(SigningIntent.ChangePassphrase("ABC")) }
        assertEquals(ICodeEffect.Message("Passphrase changed"), effect)
        assertEquals(listOf("ABC"), harness.credentials.passphrases)
    }

    @Test fun `without GnuPG the step says so, and Install brings it`() = runBlocking {
        val harness = Harness()
        harness.credentials.gnupg = false
        harness.coordinator.refresh()
        assertEquals(false, harness.stateHolder.current.gitAuth.signing.gnupgInstalled)
        harness.coordinator.handle(SigningIntent.InstallGnuPg)
        assertEquals(true, harness.stateHolder.current.gitAuth.signing.gnupgInstalled)
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

        var gnupg = true
        val keys = mutableListOf<GitSigningKey>()
        /** What gpg answers creating a key: made, or why not ("Operation cancelled"). */
        var creation: String? = null
        val created = mutableListOf<String>()
        val passphrases = mutableListOf<String>()
        override fun hasGnuPg() = gnupg
        override suspend fun ensureGnuPg() = GitOperationOutcome(true, "GnuPG is installed").also { gnupg = true }
        override suspend fun signingKeys() = keys.toList()
        override suspend fun publicSigningKey(fingerprint: String) = "-----BEGIN PGP PUBLIC KEY BLOCK----- $fingerprint"
        override suspend fun createSigningKey(userId: String): GitOperationOutcome {
            created += userId
            creation?.let { return GitOperationOutcome(false, it) }
            keys += signingKey("F".repeat(24) + "%016X".format(keys.size + 1), userId)
            return GitOperationOutcome(true, "Signing key created")
        }
        override suspend fun changeSigningPassphrase(fingerprint: String) =
            GitOperationOutcome(true, "Passphrase changed").also { passphrases += fingerprint }
    }

    private companion object {
        fun signingKey(fingerprint: String, userId: String) =
            GitSigningKey(fingerprint, fingerprint.takeLast(16), userId, "ed25519", 0L, null)

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
