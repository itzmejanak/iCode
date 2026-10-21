package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.GitAuthMethod
import dev.icode.mobile.domain.GitAuthTestResult
import dev.icode.mobile.domain.GitAuthWork
import dev.icode.mobile.domain.GitAuthWorkIntent
import dev.icode.mobile.domain.GitIdentityRules
import dev.icode.mobile.domain.GitProvider
import dev.icode.mobile.domain.GitSigningRules
import dev.icode.mobile.domain.GitSigningState
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.SigningIntent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Who commits and how Git reaches a remote (S5): the author identity, the
 * saved token, the SSH key, the connection test, and the key commits are
 * signed with (docs/gpg-signing-plan.md §7). What reaches the snapshot
 * describes a credential; it is never the secret itself.
 */
internal class GitAuthCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val config: GitConfigOperations,
    private val credentials: GitCredentialOperations,
    private val activeRoot: () -> String?,
    /** A change to the open repository, refreshed like any other local Git change. */
    private val actInRepository: suspend (operation: suspend (String) -> Unit, onSuccess: suspend () -> Unit) -> Unit,
    /** The repository's identity shows in Source Control, so it is read again after a change. */
    private val refreshRepository: suspend (root: String) -> Unit,
) {
    private val working = AtomicBoolean(false)

    fun selectProvider(provider: GitProvider) = stateHolder.update {
        it.copy(gitAuth = it.gitAuth.copy(provider = provider))
    }

    fun selectMethod(method: GitAuthMethod) = stateHolder.update {
        it.copy(gitAuth = it.gitAuth.copy(method = method))
    }

    suspend fun configureRepositoryIdentity(name: String, email: String) = actInRepository(
        { workspace -> config.configureIdentity(workspace, name, email) },
        { refresh() },
    )

    suspend fun configureGlobalIdentity(name: String, email: String) {
        if (name.isBlank() || !GitIdentityRules.isValidEmail(email)) {
            stateHolder.notify(NoticeTone.HeadsUp, "Enter a name and valid Git author email")
            return
        }
        if (!config.isAvailable()) {
            stateHolder.notify(NoticeTone.HeadsUp, "Install Git in the runtime first")
            return
        }
        runCatching {
            config.writeConfig(GitConfigScope.Global, "user.name", name.trim())
            config.writeConfig(GitConfigScope.Global, "user.email", email.trim())
            activeRoot()?.let { refreshRepository(it) }
            refresh()
        }.onFailure { error ->
            stateHolder.report("git", "Could not save Git identity", error)
        }
    }

    suspend fun clearRepositoryIdentity() {
        val workspace = activeRoot() ?: return
        runCatching {
            listOf("user.name", "user.email").forEach { key ->
                config.unsetConfig(GitConfigScope.Local, key, workspace)
            }
            refreshRepository(workspace)
            refresh()
        }.onFailure { error ->
            stateHolder.report("git", "Could not reset repository identity", error)
        }
    }

    suspend fun saveToken(host: String, username: String, token: String) {
        runCatching {
            credentials.saveHttps(host, username, token)
        }.onFailure { error ->
            stateHolder.report("git", "Unable to save the token", error)
        }
        refresh()
    }

    suspend fun clearToken() {
        runCatching {
            credentials.clearHttpsCredentials()
        }.onFailure { error ->
            stateHolder.report("git", "Unable to remove the token", error)
        }
        refresh()
    }

    /** What never waits on the mutation lock: apt, ssh-keygen, gpg — and the GnuPG dialog — and the network. */
    suspend fun handle(intent: GitAuthWorkIntent) = when (intent) {
        ICodeIntent.GenerateSshKey -> generateSshKey()
        is ICodeIntent.TestGitConnection -> testConnection(intent.url)
        SigningIntent.InstallGnuPg -> alone(GitAuthWork.InstallingGnuPg) {
            credentials.ensureGnuPg().takeUnless { it.succeeded }?.let { stateHolder.notify(NoticeTone.Problem, it.message) }
            refresh()
        }
        SigningIntent.CreateKey -> createSigningKey()
        // Git's config, written at once: the page waits on nothing else meanwhile.
        is SigningIntent.UseKey -> configureSigning { config.writeConfig(GitConfigScope.Global, SIGNING_KEY, intent.fingerprint) }
        is SigningIntent.SignCommits -> configureSigning { signCommits(intent.enabled) }
        // Nothing on the page changes with it, so its outcome is said either way.
        is SigningIntent.ChangePassphrase -> alone(GitAuthWork.ChangingPassphrase) {
            val outcome = credentials.changeSigningPassphrase(intent.fingerprint)
            stateHolder.notify(if (outcome.succeeded) NoticeTone.Done else NoticeTone.Problem, outcome.message)
        }
    }

    /**
     * A key of gpg's defaults for the default commit identity — gpg asks its passphrase in the GnuPG
     * dialog — then Git signs every commit with it.
     */
    private suspend fun createSigningKey() = alone(GitAuthWork.CreatingSigningKey) {
        val name = config.readConfig(GitConfigScope.Global, "user.name")
        val email = config.readConfig(GitConfigScope.Global, "user.email")
        if (name.isNullOrBlank() || email.isNullOrBlank()) return@alone stateHolder.notify(NoticeTone.HeadsUp, "Save a default commit identity first")
        val before = credentials.signingKeys().mapTo(hashSetOf()) { it.fingerprint }
        val outcome = credentials.createSigningKey("$name <$email>")
        val created = credentials.signingKeys().firstOrNull { it.fingerprint !in before }
        if (created == null) {
            stateHolder.notify(NoticeTone.Problem, outcome.message)
            return@alone refresh()
        }
        configureSigning {
            config.writeConfig(GitConfigScope.Global, SIGNING_KEY, created.fingerprint)
            signCommits(true)
        }
    }

    /** On: Git signs every commit, with OpenPGP — not SSH — and the chosen key. Off: Git's default again. */
    private suspend fun signCommits(enabled: Boolean) {
        if (!enabled) return config.unsetConfig(GitConfigScope.Global, SIGN_COMMITS)
        val signing = stateHolder.current.gitAuth.signing
        if (signing.key == null) signing.keys.firstOrNull()?.let { config.writeConfig(GitConfigScope.Global, SIGNING_KEY, it.fingerprint) }
        config.writeConfig(GitConfigScope.Global, "gpg.format", "openpgp")
        config.writeConfig(GitConfigScope.Global, SIGN_COMMITS, "true")
    }

    private suspend fun configureSigning(change: suspend () -> Unit) {
        runCatching { change() }.onFailure { error -> stateHolder.report("git", "Could not change commit signing", error) }
        refresh()
    }

    /** The SSH client is installed first when it is missing; its install shows as its own. */
    suspend fun generateSshKey() = alone(GitAuthWork.GeneratingKey) {
        val client = credentials.ensureSshAvailable()
        val outcome = if (client.succeeded) credentials.generateSshKey(credentials.describeHttps() ?: "icode") else client
        if (!outcome.succeeded) stateHolder.notify(NoticeTone.Problem, outcome.message)
        refresh()
    }

    suspend fun testConnection(url: String) = alone(GitAuthWork.Testing) {
        stateHolder.update { it.copy(gitAuth = it.gitAuth.copy(lastTest = null)) }
        val outcome = credentials.testConnection(url)
        // Git's refusal can repeat the address it was given.
        val result = GitAuthTestResult(outcome.succeeded, BackendLog.credentialSafe(outcome.message))
        stateHolder.update { it.copy(gitAuth = it.gitAuth.copy(lastTest = result)) }
    }

    /**
     * apt, ssh-keygen and the network run here, off the mutation lock: nothing in the workspace
     * waits behind them. One at a time; a second request while one runs is dropped.
     */
    private suspend fun alone(work: GitAuthWork, block: suspend () -> Unit) {
        if (!working.compareAndSet(false, true)) return
        stateHolder.update { it.copy(gitAuth = it.gitAuth.copy(work = work)) }
        try {
            block()
        } finally {
            stateHolder.update { it.copy(gitAuth = it.gitAuth.copy(work = null)) }
            working.set(false)
        }
    }

    /** Recomputes what the auth surface may show — never the secret itself. */
    suspend fun refresh() {
        val account = credentials.describeHttps()
        val hasSshKey = credentials.hasSshKey()
        val publicKey = credentials.readSshPublicKey()
        val gitAvailable = config.isAvailable()
        val globalName = if (gitAvailable) config.readConfig(GitConfigScope.Global, "user.name") else null
        val globalEmail = if (gitAvailable) config.readConfig(GitConfigScope.Global, "user.email") else null
        val workspace = activeRoot()?.takeIf { stateHolder.current.gitRepository != null }
        val localName = if (gitAvailable && workspace != null) config.readConfig(GitConfigScope.Local, "user.name", workspace) else null
        val localEmail = if (gitAvailable && workspace != null) config.readConfig(GitConfigScope.Local, "user.email", workspace) else null
        val signing = signingState(gitAvailable)
        stateHolder.update {
            it.copy(
                gitAvailable = gitAvailable,
                globalGitAuthorName = globalName,
                globalGitAuthorEmail = globalEmail,
                localGitAuthorName = localName,
                localGitAuthorEmail = localEmail,
                gitIdentityLoaded = true,
                gitAuth = it.gitAuth.copy(
                    httpsAccount = account,
                    hasSshKey = hasSshKey,
                    sshPublicKey = publicKey,
                    signing = signing,
                ),
            )
        }
    }

    /** gpg's keys, and what Git's global config signs with; the chosen key's public half for the provider's form. */
    private suspend fun signingState(gitAvailable: Boolean): GitSigningState {
        val gnupg = credentials.hasGnuPg()
        val keys = credentials.signingKeys()
        val signingKey = if (gitAvailable) config.readConfig(GitConfigScope.Global, SIGNING_KEY) else null
        val signs = gitAvailable && config.readConfig(GitConfigScope.Global, SIGN_COMMITS)?.lowercase() in GIT_TRUE
        val publicKey = GitSigningRules.keyFor(signingKey, keys)?.let { credentials.publicSigningKey(it.fingerprint) }
        return GitSigningState(gnupg, keys, signingKey, signs, publicKey)
    }

    private companion object {
        const val SIGNING_KEY = "user.signingkey"
        const val SIGN_COMMITS = "commit.gpgsign"
        /** How Git's config spells a true boolean. */
        val GIT_TRUE = setOf("true", "yes", "on", "1")
    }
}
