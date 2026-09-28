package dev.icode.mobile.data.git

import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimePackageInstaller
import dev.icode.mobile.domain.GitRemoteRules
import java.io.File

/**
 * Makes the user's credentials available to the real `git` binary.
 *
 * The application does not implement an account system of its own
 * (architecture.md section 15.3): it configures the mechanisms Git already
 * has — a credentials file for HTTPS and an ordinary key pair for SSH — and
 * then stays out of the way.
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
     * Writes the HTTPS credentials Git expects, with owner-only permissions.
     *
     * The file lives in the guest home inside app-private storage, which no
     * other application can read. It is rewritten from the encrypted store on
     * demand rather than kept in sync, so revoking a token takes effect at once.
     */
    override suspend fun applyHttpsCredentials(): Boolean {
        val credential = credentials.loadHttps() ?: return false
        val encodedUser = urlEncode(credential.username)
        val encodedToken = urlEncode(credential.token)
        val line = "https://$encodedUser:$encodedToken@${credential.host}"
        // Written through the guest so the permissions apply inside it.
        val script = buildString {
            append("umask 077; ")
            append("printf '%s\\n' ")
            append(GitCommandLine.quote(line))
            append(" > ")
            append(GitCommandLine.quote(GUEST_CREDENTIALS_PATH))
            append("; chmod 600 ")
            append(GitCommandLine.quote(GUEST_CREDENTIALS_PATH))
            append("; git config --global credential.helper ")
            append(GitCommandLine.quote("store --file=$GUEST_CREDENTIALS_PATH"))
        }
        return cli.runScript(null, script).succeeded
    }

    /** Removes the stored credentials from both the app and the guest. */
    override suspend fun clearHttpsCredentials() {
        credentials.clearHttps()
        cli.runScript(null, "rm -f ${GitCommandLine.quote(GUEST_CREDENTIALS_PATH)}")
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

    /** Installs the OpenSSH client, which the base image does not include. */
    override suspend fun ensureSshAvailable(): Boolean {
        if (File(layout.rootfs, SSH_KEYGEN_RELATIVE).exists()) return true
        val installed = packages.ensure("openssh-client") { File(layout.rootfs, SSH_KEYGEN_RELATIVE).exists() } ==
            RuntimePackageInstaller.Outcome.Ready
        if (installed) configureSshClient()
        return installed
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
        if (url.startsWith("https://")) applyHttpsCredentials()
        val result = cli.run(
            workspaceHostPath = null,
            arguments = listOf("ls-remote", "--heads", url),
        )
        return GitOperationOutcome(
            succeeded = result.succeeded,
            message = if (result.succeeded) {
                val branches = result.stdout.lines().count { it.isNotBlank() }
                "Connected — $branches branch(es) visible"
            } else {
                result.errorMessage
            },
        )
    }

    /** Percent-encodes the characters that would otherwise break the credential URL. */
    private fun urlEncode(value: String): String = buildString {
        value.forEach { character ->
            when {
                character.isLetterOrDigit() || character in UNRESERVED -> append(character)
                else -> append('%').append("%02X".format(character.code))
            }
        }
    }

    private companion object {
        const val GUEST_CREDENTIALS_PATH = "/root/.icode-git-credentials"
        const val GUEST_SSH_KEY_PATH = "/root/.ssh/id_ed25519"
        const val SSH_PUBLIC_RELATIVE = "root/.ssh/id_ed25519.pub"
        const val SSH_KEYGEN_RELATIVE = "usr/bin/ssh-keygen"
        const val UNRESERVED = "-._~"
    }
}

/** Result of an authentication action, carrying Git's own message on failure. */
internal data class GitOperationOutcome(val succeeded: Boolean, val message: String)
