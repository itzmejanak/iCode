package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitHistoryCommit
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.GitSigningKey

// What each Git owner needs from the real client, narrow enough for a test to fake.

/** What the editor's change markers read from Git and write to it (docs/editor-plan.md §3); tests put a fake in their place. */
internal interface GitIndexOperations {
    suspend fun isAvailable(): Boolean
    /** The top of the repository holding [location], read from disk; null when none does. */
    fun repositoryTopOf(location: String): String?
    /** When the index of the repository at [top] last changed — its file's size and time; null without one. */
    fun indexStampOf(top: String): String?
    /** [paths]' versions in that index as a checkout writes them; a path without one is left out. */
    suspend fun indexVersions(top: String, paths: List<String>): Map<String, IndexVersion>
    /** Stages [bytes] as [path]'s version, with [mode]. */
    suspend fun stageVersion(top: String, path: String, mode: String, bytes: ByteArray)
}

/** The local operations on the open workspace's repository; tests put a fake in their place. */
internal interface GitWorkspaceOperations : GitIndexOperations {
    /** The branch `HEAD` names, read from disk without Git: a repository names it before Git is installed. */
    fun branchOf(workspacePath: String): String?
    /** Whether [workspacePath] is in a repository, read from disk without Git. */
    fun isRepository(workspacePath: String): Boolean
    /** The repositories in [workspacePath], read from disk; Git works in the chosen one. */
    fun repositoriesIn(workspacePath: String): List<String>
    fun chooseRepository(workspacePath: String, repository: String)
    suspend fun provision(): GitOutcome<Unit>
    suspend fun inspect(workspacePath: String): GitWorkspaceSnapshot
    suspend fun initialize(workspacePath: String)
    suspend fun stage(workspacePath: String, path: String)
    suspend fun unstage(workspacePath: String, path: String)
    suspend fun stageAll(workspacePath: String)
    suspend fun unstageAll(workspacePath: String)
    suspend fun commit(workspacePath: String, message: String)
    suspend fun switchBranch(workspacePath: String, branch: String)
    suspend fun createBranch(workspacePath: String, branch: String)
    suspend fun deleteBranch(workspacePath: String, branch: String)
    suspend fun checkoutRemote(workspacePath: String, remoteBranch: String)
    suspend fun addRemote(workspacePath: String, name: String, url: String)
    suspend fun diff(workspacePath: String, path: String, staged: Boolean): GitFileDiff?
    /** [path] on disk against [editorBytes], as Git diffs two files; null when they are the same. */
    suspend fun compareWithBytes(workspacePath: String, path: String, editorBytes: ByteArray): GitOutcome<GitFileDiff?>
    suspend fun discard(workspacePath: String, path: String, status: String)
    suspend fun abortInProgress(workspacePath: String, inProgress: GitInProgress)
}

/** HEAD's history, which Source Control's History sheet reads a page at a time; tests put a fake in its place. */
internal interface GitHistoryOperations {
    /** [count] commits of HEAD's history after its first [skip], each before its parents. */
    suspend fun history(workspacePath: String, skip: Int, count: Int): List<GitHistoryCommit>
}

/**
 * The network operations, each reporting Git's stderr while it runs; tests put
 * a fake in their place. Nothing is finished until Git exits (PR-GIT-12).
 */
internal interface GitTransferOperations {
    suspend fun isAvailable(): Boolean
    suspend fun provision(): GitOutcome<Unit>
    // [config] (`key=value`) holds for that one command: the saved token's helper.
    suspend fun fetch(workspacePath: String, config: List<String>, onStderr: ((String) -> Unit)?)
    suspend fun pull(workspacePath: String, config: List<String>, onStderr: ((String) -> Unit)?)
    suspend fun push(workspacePath: String, setUpstream: Boolean, branch: String, config: List<String>, onStderr: ((String) -> Unit)?)
    suspend fun clone(
        url: String,
        destinationPath: String,
        directoryName: String,
        branch: String?,
        config: List<String>,
        onStderr: ((String) -> Unit)?,
    )
}

/** An imported repository's checkout brought back to what Git recorded; tests put a fake in its place. */
internal interface GitCheckoutOperations {
    suspend fun isAvailable(): Boolean
    suspend fun provision(): GitOutcome<Unit>
    /** Restores the executables and links of the repository at [top] ([GitCheckoutRepair]); Git's message on failure. */
    suspend fun restoreCheckout(top: String): GitCheckoutRepair.Restored
}

/** Where a Git setting lives: the runtime-wide default or one repository's override. */
internal enum class GitConfigScope(val option: String) { Global("--global"), Local("--local") }

/** Git's own configuration, read and written in one place. */
internal interface GitConfigOperations {
    suspend fun isAvailable(): Boolean
    /** The value set in exactly [scope], or null when it is unset there. [workspacePath] locates a local one. */
    suspend fun readConfig(scope: GitConfigScope, key: String, workspacePath: String? = null): String?
    suspend fun writeConfig(scope: GitConfigScope, key: String, value: String, workspacePath: String? = null)
    /** Removes every value of [key] set in exactly [scope]; nothing to do when it is unset. */
    suspend fun unsetConfig(scope: GitConfigScope, key: String, workspacePath: String? = null)
    suspend fun configureIdentity(workspacePath: String, name: String, email: String)
}

/** Credentials Git uses, and the checks made with them. Never returns a secret. */
internal interface GitCredentialOperations {
    /** `account@host` of the saved token — never the token. */
    fun describeHttps(): String?
    fun saveHttps(host: String, username: String, token: String)
    /**
     * Runs [block] with the Git configuration that hands it the saved token
     * for that one command, or none when no token is saved.
     */
    suspend fun <T> withHttpsCredentials(block: suspend (config: List<String>) -> T): T
    suspend fun clearHttpsCredentials()
    fun hasSshKey(): Boolean
    fun readSshPublicKey(): String?
    /** Installs the SSH client when it is missing; apt's words say why it could not. */
    suspend fun ensureSshAvailable(): GitOperationOutcome
    suspend fun generateSshKey(comment: String): GitOperationOutcome
    suspend fun testConnection(url: String): GitOperationOutcome
    /** GnuPG is installed in the runtime: signing commits needs it. */
    fun hasGnuPg(): Boolean
    /** Installs GnuPG when it is missing; apt's words say why it could not. */
    suspend fun ensureGnuPg(): GitOperationOutcome
    /** The secret keys gpg holds that can sign. */
    suspend fun signingKeys(): List<GitSigningKey>
    /** [fingerprint]'s public half, armored; null when gpg has none. */
    suspend fun publicSigningKey(fingerprint: String): String?
    /** A key of gpg's defaults for [userId] ("Name <email>"); gpg asks its passphrase in the GnuPG dialog. */
    suspend fun createSigningKey(userId: String): GitOperationOutcome
    /** gpg asks the current passphrase, then the new one twice, in the GnuPG dialog. */
    suspend fun changeSigningPassphrase(fingerprint: String): GitOperationOutcome
}
