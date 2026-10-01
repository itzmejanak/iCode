package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.log.BackendLog
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The user's login environment (docs/runtime-foundation-plan.md §3.1): what a terminal's shell
 * would have, for what runs on the user's behalf without one — the assistant's commands, managed
 * processes, language servers. Read with an interactive login bash, so what an installer added to
 * `~/.bashrc` is in it too; read again only when a profile file changed.
 */
internal class GuestEnvironment(
    private val rootfs: File,
    /** How long the profile has to be read: a profile waiting on something never ends a launch. */
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
    /** Runs bash's script in the guest home and answers its stdout; throws when it cannot. */
    private val shell: suspend (script: String) -> String,
) {
    constructor(layout: RuntimeLayout, guest: GuestCommand) : this(layout.rootfs, TIMEOUT_MILLIS, { script ->
        guest.captureScript(null, script, "environment", mapOf("HISTFILE" to "/dev/null")).stdout
    })

    /** The variables to launch with, and why the profile could not be read, if it could not. */
    data class Login(val variables: Map<String, String>, val failure: String? = null)

    private val lock = Mutex()
    private var cached: Pair<String, Login>? = null

    suspend fun current(): Login = lock.withLock {
        val fingerprint = fingerprint()
        cached?.takeIf { it.first == fingerprint }?.let { return it.second }
        return read().also { cached = fingerprint to it }
    }

    private suspend fun read(): Login {
        val output = try {
            withTimeoutOrNull(timeoutMillis) { shell(SCRIPT) }
                ?: return Login(emptyMap(), "your shell profile did not finish in ${timeoutMillis / 1000} s")
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failure: Exception) {
            BackendLog.w(TAG, "The login environment could not be read (${failure.javaClass.simpleName})")
            return Login(emptyMap(), "your shell profile could not be run")
        }
        val variables = parse(output) ?: return Login(emptyMap(), "your shell profile ended before it was read through (an `exec` in it?)")
        return Login(variables)
    }

    /** Each profile file's size and time: a change to any of them is read at the next launch. */
    private fun fingerprint(): String = buildList {
        PROFILE_FILES.forEach { add(File(rootfs, it)) }
        File(rootfs, PROFILE_DIRECTORY).listFiles().orEmpty().sortedBy { it.name }.forEach(::add)
    }.joinToString("|") { file -> if (file.isFile) "${file.path}:${file.length()}:${file.lastModified()}" else "${file.path}:-" }

    companion object {
        /** What the profile's shell prints after reading it: a marker, then `env -0`. */
        private const val MARKER = "\u0000icode-environment\u0000"

        /** Interactive, so `~/.bashrc` runs to its end; stdin closed, so nothing in it waits for input. */
        internal const val SCRIPT = "exec /bin/bash -lic 'printf \"\\0icode-environment\\0\"; env -0' </dev/null"

        /**
         * The variables [output] carries after the marker — a banner a profile prints comes before
         * it — less what every launch sets itself; null when the marker never came.
         */
        internal fun parse(output: String): Map<String, String>? {
            val start = output.indexOf(MARKER).takeIf { it >= 0 } ?: return null
            return output.substring(start + MARKER.length).split('\u0000')
                .mapNotNull { entry -> entry.indexOf('=').takeIf { it > 0 }?.let { entry.substring(0, it) to entry.substring(it + 1) } }
                .filterNot { (name, _) -> name in LAUNCH_OWNED || name.startsWith("PROOT_") }
                .toMap()
        }

        /** Set by each launch for itself (PRoot's own, the shell's bookkeeping, the terminal type). */
        private val LAUNCH_OWNED = setOf(
            "PWD", "OLDPWD", "SHLVL", "_", "HOME", "TERM", "COLORTERM", "HISTFILE",
            "LD_LIBRARY_PATH", "LD_PRELOAD", "GLIBC_TUNABLES",
        )

        private val PROFILE_FILES = listOf(
            "etc/environment", "etc/profile", "etc/bash.bashrc",
            "root/.profile", "root/.bash_profile", "root/.bash_login", "root/.bashrc", "root/.bash_aliases",
        )
        private const val PROFILE_DIRECTORY = "etc/profile.d"
        private const val TIMEOUT_MILLIS = 10_000L
        private const val TAG = "GuestEnvironment"
    }
}
