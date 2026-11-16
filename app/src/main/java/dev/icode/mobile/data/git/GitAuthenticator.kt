package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimePackageInstaller
import dev.icode.mobile.domain.GitRemoteRules
import dev.icode.mobile.domain.GitSigningKey
import dev.icode.mobile.domain.GitSigningRules
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.countOf
import java.io.File

/**
 * Makes the user's credentials available to the real `git` binary.
 *
 * The application does not implement an account system of its own. It configures the mechanisms Git already
 * has — a credentials file for HTTPS, an ordinary key pair for SSH, and gpg's
 * own keys for signing (docs/source-control.md) — and then stays out of
 * the way.
 */
internal class GitAuthenticator(
    private val layout: RuntimeLayout,
    private val cli: CliGitService,
    private val packages: RuntimePackageInstaller,
    private val credentials: GitCredentialStore,
) : GitCredentialOperations {

    override fun describeHttps(): String? = credentials.describeHttps()

    override fun saveHttps(host: String, username: String, token: String) =
        credentials.saveHttps(GitCredentialStore.HttpsCredential(host, username, token))

    /**
     * Runs [block] with Git reading the saved token from a file made for that
     * one command: owner-only, in the guest's `/tmp` inside app-private
     * storage, and deleted when the command ends, however it ends. The token
     * is otherwise only ciphertext; Git in the user's own terminal asks for
     * credentials itself.
     */
    override suspend fun <T> withHttpsCredentials(block: suspend (config: List<String>) -> T): T {
        if (!credentials.hasHttps) return block(emptyList())
        val credential = credentials.loadHttps()
        if (credential == null) {
            // Encrypted with a key this device no longer has: unusable, so not kept.
            // Removing it is housekeeping: a failed write must not fail the command that found it.
            runCatching { credentials.clearHttps() }
                .onSuccess { BackendLog.w("git", "The saved token could not be decrypted and was removed") }
                .onFailure { BackendLog.w("git", "The saved token could not be decrypted, nor removed", it) }
            return block(emptyList())
        }
        val file = GitCredentialFile.create(
            File(layout.rootfs, "tmp"),
            GitCredentialFile.line(credential.host, credential.username, credential.token),
        )
        try {
            // The empty value drops any helper configured elsewhere, for this command only.
            return block(listOf("credential.helper=", "credential.helper=store --file=/tmp/${file.name}"))
        } finally {
            file.delete()
        }
    }

    /** Removes the saved token from the app; the guest never keeps one. */
    override suspend fun clearHttpsCredentials() {
        credentials.clearHttps()
    }

    /** True once an SSH key pair exists in the guest. */
    override fun hasSshKey(): Boolean = File(layout.rootfs, SSH_PUBLIC_RELATIVE).isFile

    override fun readSshPublicKey(): String? =
        runCatching { File(layout.rootfs, SSH_PUBLIC_RELATIVE).readText().trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    /**
     * Generates an ed25519 key pair inside the guest.
     *
     * ed25519 rather than RSA: shorter, faster on a phone, and accepted by
     * every major host. The private key never leaves the guest home, and only
     * the public half is ever shown.
     */
    override suspend fun generateSshKey(comment: String): GitOperationOutcome {
        if (hasSshKey()) return GitOperationOutcome(true, "An SSH key already exists")
        val script = buildString {
            append("mkdir -p ~/.ssh && chmod 700 ~/.ssh && ")
            append("ssh-keygen -t ed25519 -N '' -C ")
            append(GitCommandLine.quote(comment.ifBlank { "icode" }))
            append(" -f ")
            append(GitCommandLine.quote(GUEST_SSH_KEY_PATH))
        }
        val result = cli.runScript(null, script)
        if (result.succeeded) configureSshClient()
        return GitOperationOutcome(
            succeeded = result.succeeded && hasSshKey(),
            message = if (result.succeeded) "SSH key created" else result.errorMessage,
        )
    }

    /** Installs the SSH client, which the base image does not include. */
    override suspend fun ensureSshAvailable(): GitOperationOutcome {
        if (packages.isInstalled(RuntimeTools.SshClient)) return GitOperationOutcome(true, "The SSH client is installed")
        return when (val outcome = packages.ensure(RuntimeTools.SshClient)) {
            RuntimePackageInstaller.Outcome.Ready -> GitOperationOutcome(true, "The SSH client is installed").also { configureSshClient() }
            is RuntimePackageInstaller.Outcome.Failed -> GitOperationOutcome(false, outcome.reason)
            RuntimePackageInstaller.Outcome.Cancelled -> GitOperationOutcome(false, "Installing the SSH client was cancelled")
        }
    }

    /**
     * Writes an SSH client configuration that can run without a terminal.
     *
     * A first connection to an unknown host would otherwise stop to ask the
     * user to confirm the host key, which nothing can answer here.
     * `accept-new` records a host the first time but still refuses if a known
     * key later changes — so a genuine man-in-the-middle is still caught,
     * unlike `StrictHostKeyChecking no`.
     */
    suspend fun configureSshClient() {
        val config = listOf(
            "Host *",
            "    StrictHostKeyChecking accept-new",
            "    UserKnownHostsFile ~/.ssh/known_hosts",
            "    IdentityFile $GUEST_SSH_KEY_PATH",
        ).joinToString("\n")
        val script = buildString {
            append("mkdir -p ~/.ssh && chmod 700 ~/.ssh && umask 077 && ")
            append("printf '%s\\n' ")
            append(GitCommandLine.quote(config))
            append(" > ~/.ssh/config && chmod 600 ~/.ssh/config")
        }
        cli.runScript(null, script)
    }

    /**
     * Verifies credentials against a real remote.
     *
     * `ls-remote` is read-only and needs authentication, so it proves access
     * without touching the repository. Nothing is reported as working unless
     * Git itself exits zero (PR-GIT-12).
     */
    override suspend fun testConnection(url: String): GitOperationOutcome {
        GitRemoteRules.urlError(url.trim())?.let { return GitOperationOutcome(false, it) }
        val result = withHttpsCredentials { config ->
            cli.run(workspaceHostPath = null, arguments = listOf("ls-remote", "--heads", "--", url), config = config)
        }
        return GitOperationOutcome(
            succeeded = result.succeeded,
            message = if (result.succeeded) {
                val branches = result.stdout.lines().count { it.isNotBlank() }
                "Connected — ${countOf(branches, "branch", "branches")} visible"
            } else {
                result.errorMessage
            },
        )
    }

    override fun hasGnuPg(): Boolean = packages.isInstalled(RuntimeTools.GnuPg)

    override suspend fun ensureGnuPg(): GitOperationOutcome = when (val outcome = packages.ensure(RuntimeTools.GnuPg)) {
        RuntimePackageInstaller.Outcome.Ready -> GitOperationOutcome(true, "GnuPG is installed")
        is RuntimePackageInstaller.Outcome.Failed -> GitOperationOutcome(false, outcome.reason)
        RuntimePackageInstaller.Outcome.Cancelled -> GitOperationOutcome(false, "Installing GnuPG was cancelled")
    }

    override suspend fun signingKeys(): List<GitSigningKey> {
        if (!hasGnuPg()) return emptyList()
        val result = cli.runScript(null, "gpg --batch --list-secret-keys --with-colons --fixed-list-mode")
        return if (result.succeeded) GitSigningRules.keys(result.stdout) else emptyList()
    }

    override suspend fun publicSigningKey(fingerprint: String): String? =
        cli.runScript(null, "gpg --batch --armor --export ${GitCommandLine.quote(fingerprint)}")
            .takeIf { it.succeeded }?.stdout?.trim()?.ifEmpty { null }

    /** gpg's defaults — ed25519 to sign, cv25519 to encrypt, three years — as `gpg --quick-generate-key` makes them. */
    override suspend fun createSigningKey(userId: String): GitOperationOutcome {
        val result = cli.runScript(null, "gpg --batch --yes --quick-generate-key ${GitCommandLine.quote(userId)}")
        return gpgOutcome(result, result.succeeded, "Signing key created")
    }

    /** gpg exits 0 whether or not the passphrase changed: its status line says which. */
    override suspend fun changeSigningPassphrase(fingerprint: String): GitOperationOutcome {
        val result = cli.runScript(null, "gpg --batch --status-fd 1 --passwd ${GitCommandLine.quote(fingerprint)}")
        return gpgOutcome(result, result.succeeded && PASSWD_CHANGED in result.stdout.lines(), "Passphrase changed")
    }

    /** [done], or gpg's own words when it did not: "key generation failed: Operation cancelled". */
    private fun gpgOutcome(result: CliGitService.CommandResult, succeeded: Boolean, done: String) =
        GitOperationOutcome(succeeded, if (succeeded) done else GitSigningRules.failure(result.stderr) ?: "gpg did not finish")

    private companion object {
        const val GUEST_SSH_KEY_PATH = "/root/.ssh/id_ed25519"
        const val SSH_PUBLIC_RELATIVE = "root/.ssh/id_ed25519.pub"
        /** gpg's status line once `--passwd` has changed every key's passphrase. */
        const val PASSWD_CHANGED = "[GNUPG:] SUCCESS keyedit.passwd"
    }
}

/** Result of an authentication action, carrying Git's own message on failure. */
internal data class GitOperationOutcome(val succeeded: Boolean, val message: String)
