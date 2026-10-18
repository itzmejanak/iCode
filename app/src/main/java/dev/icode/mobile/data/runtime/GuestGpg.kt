package dev.icode.mobile.data.runtime

import android.content.Context
import java.io.File

/**
 * gpg's side of signed commits (docs/gpg-signing-plan.md), written with the runtime as `sudo` is:
 * iCode's pinentry and agent program — one script, `assets/runtime/guest/icode-gpg`, under both
 * names — and the system's gpg settings that name them. A user's own `~/.gnupg` files override
 * these, as gpg reads `/etc/gnupg` first.
 */
internal object GuestGpg {
    /** Where the script hands its requests over, from the root filesystem: the guest's `/tmp/icode-gpg`. */
    const val REQUESTS = "tmp/icode-gpg"

    private const val PINENTRY = "/usr/local/bin/icode-pinentry"
    private const val AGENT_PROGRAM = "/usr/local/bin/icode-gpg-agent"
    private const val SCRIPT_ASSET = "runtime/guest/icode-gpg"

    fun install(context: Context, rootfs: File) {
        val script = context.assets.open(SCRIPT_ASSET).use { it.readBytes() }
        listOf(PINENTRY, AGENT_PROGRAM).forEach { path ->
            File(rootfs, path.removePrefix("/")).apply {
                parentFile?.mkdirs()
                writeBytes(script)
                setExecutable(true, false)
            }
        }
        File(rootfs, "etc/gnupg").apply {
            mkdirs()
            File(this, "gpg.conf").writeText(GPG_CONF)
            File(this, "gpg-agent.conf").writeText(AGENT_CONF)
        }
    }

    private val GPG_CONF = """
        # Installed by iCode. With no gpg-agent running, gpg has iCode start one in a session of
        # its own, where it outlives the command that needed it. ~/.gnupg/gpg.conf overrides this.
        agent-program $AGENT_PROGRAM
    """.trimIndent() + "\n"

    private val AGENT_CONF = """
        # Installed by iCode. A passphrase is asked in iCode's own dialog, and kept until 30 minutes
        # after it was last used (2 hours at most, gpg-agent's max-cache-ttl).
        # ~/.gnupg/gpg-agent.conf overrides these.
        pinentry-program $PINENTRY
        default-cache-ttl 1800
    """.trimIndent() + "\n"
}
