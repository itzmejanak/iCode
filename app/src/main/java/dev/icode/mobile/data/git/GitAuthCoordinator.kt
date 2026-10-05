package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.GitAuthMethod
import dev.icode.mobile.domain.GitAuthTestResult
import dev.icode.mobile.domain.GitAuthWork
import dev.icode.mobile.domain.GitIdentityRules
import dev.icode.mobile.domain.GitProvider
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Who commits and how Git reaches a remote (S5): the author identity, the
 * saved token, the SSH key and the connection test. What reaches the snapshot
 * describes a credential; it is never the secret itself.
 */
internal class GitAuthCoordinator(
    private val workbench: ICodeStateHolder,
    private val config: GitConfigOperations,
    private val credentials: GitCredentialOperations,
    private val activeRoot: () -> String?,
    /** A change to the open repository, refreshed like any other local Git change. */
    private val actInRepository: suspend (operation: suspend (String) -> Unit, onSuccess: suspend () -> Unit) -> Unit,
    /** The repository's identity shows in Source Control, so it is read again after a change. */
    private val refreshRepository: suspend (root: String) -> Unit,
) {
    private val working = AtomicBoolean(false)

    fun selectProvider(provider: GitProvider) = workbench.update {
        it.copy(gitAuth = it.gitAuth.copy(provider = provider))
    }

    fun selectMethod(method: GitAuthMethod) = workbench.update {
        it.copy(gitAuth = it.gitAuth.copy(method = method))
    }

    suspend fun configureRepositoryIdentity(name: String, email: String) = actInRepository(
        { workspace -> config.configureIdentity(workspace, name, email) },
        { refresh() },
    )

    suspend fun configureGlobalIdentity(name: String, email: String) {
        if (name.isBlank() || !GitIdentityRules.isValidEmail(email)) {
            workbench.message("Enter a name and valid Git author email")
            return
        }
        if (!config.isAvailable()) {
            workbench.message("Install Git in the runtime first")
            return
        }
        runCatching {
            config.writeConfig(GitConfigScope.Global, "user.name", name.trim())
            config.writeConfig(GitConfigScope.Global, "user.email", email.trim())
            activeRoot()?.let { refreshRepository(it) }
            refresh()
        }.onFailure { error ->
            workbench.report("git", "Could not save Git identity", error)
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
            workbench.report("git", "Could not reset repository identity", error)
        }
    }

    suspend fun saveToken(host: String, username: String, token: String) {
        runCatching {
            credentials.saveHttps(host, username, token)
        }.onFailure { error ->
            workbench.report("git", "Unable to save the token", error)
        }
        refresh()
    }

    suspend fun clearToken() {
        runCatching {
            credentials.clearHttpsCredentials()
        }.onFailure { error ->
            workbench.report("git", "Unable to remove the token", error)
        }
        refresh()
    }

    /** The SSH client is installed first when it is missing; its install shows as its own. */
    suspend fun generateSshKey() = alone(GitAuthWork.GeneratingKey) {
        val client = credentials.ensureSshAvailable()
        val outcome = if (client.succeeded) credentials.generateSshKey(credentials.describeHttps() ?: "icode") else client
        if (!outcome.succeeded) workbench.message(outcome.message)
        refresh()
    }

    suspend fun testConnection(url: String) = alone(GitAuthWork.Testing) {
        workbench.update { it.copy(gitAuth = it.gitAuth.copy(lastTest = null)) }
        val outcome = credentials.testConnection(url)
        // Git's refusal can repeat the address it was given.
        val result = GitAuthTestResult(outcome.succeeded, BackendLog.credentialSafe(outcome.message))
        workbench.update { it.copy(gitAuth = it.gitAuth.copy(lastTest = result)) }
    }

    /**
     * apt, ssh-keygen and the network run here, off the mutation lock: nothing in the workspace
     * waits behind them. One at a time; a second request while one runs is dropped.
     */
    private suspend fun alone(work: GitAuthWork, block: suspend () -> Unit) {
        if (!working.compareAndSet(false, true)) return
        workbench.update { it.copy(gitAuth = it.gitAuth.copy(work = work)) }
        try {
            block()
        } finally {
            workbench.update { it.copy(gitAuth = it.gitAuth.copy(work = null)) }
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
        val workspace = activeRoot()?.takeIf { workbench.current.gitRepository != null }
        val localName = if (gitAvailable && workspace != null) config.readConfig(GitConfigScope.Local, "user.name", workspace) else null
        val localEmail = if (gitAvailable && workspace != null) config.readConfig(GitConfigScope.Local, "user.email", workspace) else null
        workbench.update {
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
                ),
            )
        }
    }
}
