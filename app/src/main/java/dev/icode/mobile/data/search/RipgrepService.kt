package dev.icode.mobile.data.search

import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.RuntimeLayout
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

internal interface WorkspaceSearchEngine {
    fun isAvailable(): Boolean
    suspend fun ensureAvailable(): RipgrepAvailability
    suspend fun index(rootLocation: String): RipgrepIndexOutcome
    suspend fun search(
        rootLocation: String,
        query: String,
        matchCase: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
    ): RipgrepSearchOutcome
}

/** The real workspace-search engine: one provisioned `rg` binary, used through the guest process bridge. */
internal class RipgrepService(
    private val layout: RuntimeLayout,
    private val scope: CoroutineScope,
) : WorkspaceSearchEngine {
    private val guest = GuestCommand(layout)
    private val provisionLock = Any()
    private var provisionTask: Deferred<RipgrepAvailability>? = null
    private var provisionStartedAt = 0L

    override fun isAvailable(): Boolean = layout.isInstalled && File(layout.rootfs, RG_RELATIVE_PATH).isFile

    /**
     * Installs ripgrep once when search is first used. The install belongs to
     * the gateway scope, not a typed query, so replacing a query does not kill
     * apt half-way through its package database update.
     */
    override suspend fun ensureAvailable(): RipgrepAvailability {
        if (isAvailable()) return RipgrepAvailability.Ready
        if (!layout.isInstalled) {
            return RipgrepAvailability.Failed("Install the Linux runtime before using workspace search")
        }
        val now = System.nanoTime()
        val task = synchronized(provisionLock) {
            val existing = provisionTask
            if (existing != null && (existing.isActive || now - provisionStartedAt < RETRY_COOLDOWN_NANOS)) {
                existing
            } else {
                provisionStartedAt = now
                scope.async(Dispatchers.IO) {
                    try {
                        provision()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        val detail = error.message?.takeIf(String::isNotBlank)
                        RipgrepAvailability.Failed(
                            "Could not install ripgrep${detail?.let { ": $it" }.orEmpty()}",
                        )
                    }
                }.also { provisionTask = it }
            }
        }
        return task.await()
    }

    override suspend fun index(rootLocation: String): RipgrepIndexOutcome {
        val parser = RipgrepFileParser(rootLocation, MAX_INDEX_FILES)
        val result = guest.streamRecords(
            workspaceHostPath = rootLocation,
            argv = RipgrepCommand.files(),
            stderrName = "rg-files",
            delimiter = 0,
            onRecord = parser::accept,
        )
        val warning = result.warning(
            operation = "Some workspace files could not be indexed",
            acceptedExitCodes = setOf(0, 1),
        )
        val outcome = parser.outcome(warning)
        return outcome.copy(truncated = outcome.truncated || result.stoppedEarly)
    }

    override suspend fun search(
        rootLocation: String,
        query: String,
        matchCase: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
    ): RipgrepSearchOutcome {
        val parser = RipgrepJsonParser(rootLocation, MAX_MATCHES_PER_FILE, MAX_TOTAL_MATCHES)
        val result = guest.stream(
            workspaceHostPath = rootLocation,
            argv = RipgrepCommand.content(
                query = query,
                matchCase = matchCase,
                wholeWord = wholeWord,
                regex = regex,
                // One extra line proves a per-file cap really omitted something.
                maxMatchingLinesPerFile = MAX_MATCHES_PER_FILE + 1,
            ),
            stderrName = "rg-search",
            onLine = parser::accept,
        )
        val parsed = parser.outcome()
        val detail = result.failureDetail()
        val warning = when {
            result.stoppedEarly -> null
            result.exitCode == 0 || result.exitCode == 1 -> null
            regex && result.stderr.contains("regex parse error", ignoreCase = true) ->
                "Invalid regular expression: ${detail ?: "check the pattern"}"
            else -> "Some files could not be searched${detail?.let { ": $it" }.orEmpty()}"
        }
        return parsed.copy(
            truncated = parsed.truncated || result.stoppedEarly,
            warning = warning,
        )
    }

    private suspend fun provision(): RipgrepAvailability {
        val output = ArrayDeque<String>()
        fun remember(line: String): Boolean {
            if (line.isNotBlank()) {
                if (output.size == MAX_PROVISION_LINES) output.removeFirst()
                output.addLast(line.trim())
            }
            return true
        }
        val environment = mapOf("DEBIAN_FRONTEND" to "noninteractive")
        val update = guest.stream(
            workspaceHostPath = null,
            argv = listOf("apt-get", "update"),
            stderrName = "rg-apt-update",
            environment = environment,
            onLine = ::remember,
        )
        if (update.exitCode != 0) return RipgrepAvailability.Failed(
            installFailure("Could not update runtime packages", update, output),
        )
        val install = guest.stream(
            workspaceHostPath = null,
            argv = listOf("apt-get", "install", "-y", "--no-install-recommends", "ripgrep"),
            stderrName = "rg-apt-install",
            environment = environment,
            onLine = ::remember,
        )
        return if (install.exitCode == 0 && isAvailable()) {
            RipgrepAvailability.Ready
        } else {
            RipgrepAvailability.Failed(installFailure("Could not install ripgrep", install, output))
        }
    }

    private fun installFailure(
        fallback: String,
        result: GuestCommand.Result,
        output: ArrayDeque<String>,
    ): String {
        val detail = result.failureDetail() ?: output.lastOrNull()
        return if (detail == null) fallback else "$fallback: $detail"
    }

    private fun GuestCommand.Result.warning(operation: String, acceptedExitCodes: Set<Int>): String? {
        if (stoppedEarly || exitCode in acceptedExitCodes) return null
        return "$operation${failureDetail()?.let { ": $it" }.orEmpty()}"
    }

    private fun GuestCommand.Result.failureDetail(): String? {
        val lines = stderr.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val explanatory = lines.lastOrNull { it.startsWith("error:", ignoreCase = true) }
            ?: lines.lastOrNull { line ->
                listOf("permission denied", "not found", "failed", "unable", "error").any {
                    line.contains(it, ignoreCase = true)
                }
            }
            ?: lines.lastOrNull()
        return explanatory?.removePrefix("error: ")?.take(MAX_ERROR_CHARS)
    }

    private companion object {
        const val RG_RELATIVE_PATH = "usr/bin/rg"
        const val MAX_INDEX_FILES = 5_000
        const val MAX_MATCHES_PER_FILE = 200
        const val MAX_TOTAL_MATCHES = 1_000
        const val MAX_PROVISION_LINES = 20
        const val MAX_ERROR_CHARS = 300
        const val RETRY_COOLDOWN_NANOS = 30_000_000_000L
    }
}

internal sealed interface RipgrepAvailability {
    data object Ready : RipgrepAvailability
    data class Failed(val reason: String) : RipgrepAvailability
}
