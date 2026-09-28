package dev.icode.mobile.data.runtime

import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one way iCode installs a runtime package (git, openssh-client, ripgrep).
 *
 * dpkg has one database and one lock, so installs run one at a time. A second
 * request for a package already installing waits for that install instead of
 * starting apt again. The install belongs to [scope], not to the request: a
 * search replaced by the next keystroke, or a screen left, must not kill apt
 * half-way through its package database.
 */
internal class RuntimePackageInstaller(
    private val scope: CoroutineScope,
    private val runtimeReady: () -> Boolean,
    /** Runs one non-interactive apt command in the guest, reporting its output line by line. */
    private val apt: suspend (argv: List<String>, onLine: (String) -> Boolean) -> GuestCommand.Result,
) {
    constructor(layout: RuntimeLayout, scope: CoroutineScope) : this(
        scope,
        runtimeReady = { layout.isInstalled },
        apt = GuestCommand(layout).let { guest ->
            { argv, onLine -> guest.stream(null, argv, "apt", mapOf("DEBIAN_FRONTEND" to "noninteractive"), onLine) }
        },
    )

    sealed interface Outcome {
        data object Ready : Outcome
        data class Failed(val reason: String) : Outcome
    }

    private val dpkg = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<Outcome>>()

    /** Installs [packageName] unless [installed] already holds. */
    suspend fun ensure(packageName: String, installed: () -> Boolean): Outcome {
        if (installed()) return Outcome.Ready
        if (!runtimeReady()) return Outcome.Failed("Install the Linux runtime first")
        val task = synchronized(inFlight) {
            inFlight[packageName]?.takeIf { it.isActive }
                ?: scope.async(Dispatchers.IO) {
                    try {
                        dpkg.withLock { install(packageName, installed) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        val detail = error.message?.takeIf(String::isNotBlank)
                        Outcome.Failed("Could not install $packageName${detail?.let { ": $it" }.orEmpty()}")
                    }
                }.also { inFlight[packageName] = it }
        }
        return task.await()
    }

    private suspend fun install(packageName: String, installed: () -> Boolean): Outcome {
        // The install that held the lock before this one may have brought it.
        if (installed()) return Outcome.Ready
        val output = ArrayDeque<String>()
        val remember: (String) -> Boolean = { line ->
            if (line.isNotBlank()) {
                if (output.size == MAX_OUTPUT_LINES) output.removeFirst()
                output.addLast(line.trim())
            }
            true
        }
        val command = listOf("apt-get", "install", "-y", "--no-install-recommends", packageName)
        var result = apt(command, remember)
        if (result.exitCode != 0 || !installed()) {
            // A fresh runtime has no package lists, and old ones name archives
            // the mirror no longer has: read them again, then try once more.
            val update = apt(listOf("apt-get", "update"), remember)
            if (update.exitCode != 0) return Outcome.Failed(failure("Could not update runtime packages", update, output))
            result = apt(command, remember)
        }
        return if (result.exitCode == 0 && installed()) {
            Outcome.Ready
        } else {
            Outcome.Failed(failure("Could not install $packageName", result, output))
        }
    }

    private fun failure(fallback: String, result: GuestCommand.Result, output: ArrayDeque<String>): String {
        val detail = result.failureDetail() ?: output.lastOrNull()
        return if (detail == null) fallback else "$fallback: $detail"
    }

    private companion object {
        const val MAX_OUTPUT_LINES = 20
    }
}
