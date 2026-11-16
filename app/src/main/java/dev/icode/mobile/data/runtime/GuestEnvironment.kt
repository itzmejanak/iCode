package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.log.BackendLog
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The user's login environment (docs/runtime-and-terminal.md): what a fresh terminal's login
 * shell exports, for what runs on the user's behalf without one. Read with the user's own login shell,
 * interactive, so what an installer added to `~/.bashrc` is in it too; read again only when a profile
 * file changed. [of] gives each [GuestContext] its variables.
 */
internal class GuestEnvironment(
    private val rootfs: File,
    /** Root's login shell ([RuntimeLayout.loginShell]): the profile is read with it, and the base names it. */
    private val loginShell: () -> String,
    /** How long the profile has to be read: a profile waiting on something never ends a launch. */
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
    /** Runs a script in the guest home and answers its stdout; throws when it cannot. */
    private val shell: suspend (script: String) -> String,
) {
    constructor(layout: RuntimeLayout) : this(layout.rootfs, layout::loginShell, TIMEOUT_MILLIS, GuestCommand(layout, GuestContext.Install).let { guest ->
        { script -> guest.captureScript(null, script, "environment", mapOf("HISTFILE" to HISTFILE)).stdout }
    })

    /** The variables to launch with, and why the profile could not be read, if it could not. */
    data class Login(val variables: Map<String, String>, val failure: String? = null)

    private val lock = Mutex()
    private var cached: Pair<String, Login>? = null

    /** [context]'s variables ([GuestVariables.of]), with the profile's where it reads one, and why that could not be read. */
    suspend fun of(context: GuestContext): Login {
        val shell = loginShell()
        if (!context.readsProfile) return Login(GuestVariables.of(context, shell))
        val profile = current()
        return Login(GuestVariables.of(context, shell, profile.variables), profile.failure)
    }

    /** What the profile exports, as read last. */
    suspend fun current(): Login = lock.withLock {
        val shell = loginShell()
        val fingerprint = shell + "|" + fingerprint()
        cached?.takeIf { it.first == fingerprint }?.let { return it.second }
        return read(shell).also { cached = fingerprint to it }
    }

    private suspend fun read(loginShell: String): Login {
        val output = try {
            withTimeoutOrNull(timeoutMillis) { shell(script(loginShell)) }
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
        /** What a result says when it ran without the user's profile, [failure] saying why ([Login.failure]). */
        fun withoutProfile(failure: String) = "Your login environment could not be read ($failure): this ran with iCode's default environment"

        /** What the profile's shell prints after reading it: a marker, then `env -0`. */
        private const val MARKER = "\u0000icode-environment\u0000"

        /** The snapshot's own history file: the interactive shell that reads the profile writes no history. */
        private const val HISTFILE = "/dev/null"

        /**
         * [loginShell], as a login shell and interactive, so `~/.bashrc` runs to its end; stdin closed, so
         * nothing in it waits for input. The marker and the variables come from coreutils, which bash, zsh,
         * fish and dash all run the same.
         */
        internal fun script(loginShell: String): String =
            "exec ${shellQuote(loginShell)} -l -i -c ${shellQuote("/usr/bin/printf '\\000icode-environment\\000'; /usr/bin/env -0")} </dev/null"

        /**
         * The variables [output] carries after the marker — a banner a profile prints comes before it —
         * less the shell's own bookkeeping, which is the snapshot's and nobody else's; null when the marker
         * never came.
         */
        internal fun parse(output: String): Map<String, String>? {
            val start = output.indexOf(MARKER).takeIf { it >= 0 } ?: return null
            return output.substring(start + MARKER.length).split('\u0000')
                .mapNotNull { entry -> entry.indexOf('=').takeIf { it > 0 }?.let { entry.substring(0, it) to entry.substring(it + 1) } }
                .filterNot { (name, value) -> name in SHELL_OWNED || (name == "HISTFILE" && value == HISTFILE) }
                .toMap()
        }

        /** The reading shell's own: where it was, how deep, its last command. */
        private val SHELL_OWNED = setOf("PWD", "OLDPWD", "SHLVL", "_")

        private val PROFILE_FILES = listOf(
            "etc/environment", "etc/profile", "etc/bash.bashrc", "etc/passwd",
            "root/.profile", "root/.bash_profile", "root/.bash_login", "root/.bashrc", "root/.bash_aliases",
            "root/.zprofile", "root/.zshrc", "root/.zshenv", "root/.config/fish/config.fish",
        )
        private const val PROFILE_DIRECTORY = "etc/profile.d"
        private const val TIMEOUT_MILLIS = 10_000L
        private const val TAG = "GuestEnvironment"
    }
}
