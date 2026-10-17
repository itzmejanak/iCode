package dev.icode.mobile.data.search

import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimePackageInstaller
import dev.icode.mobile.domain.RuntimeTools

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
    private val packages: RuntimePackageInstaller,
) : WorkspaceSearchEngine {
    private val guest = GuestCommand(layout)

    /** When the last install failed, and why: typing must not start apt again on every key. */
    @Volatile private var lastFailure: Pair<Long, String>? = null

    override fun isAvailable(): Boolean = layout.isInstalled && packages.isInstalled(RuntimeTools.Ripgrep)

    /** Installs ripgrep when search is first used, through the runtime's one package installer. */
    override suspend fun ensureAvailable(): RipgrepAvailability {
        if (isAvailable()) return RipgrepAvailability.Ready
        if (!layout.isInstalled) {
            return RipgrepAvailability.Failed("Install the Linux runtime before using workspace search")
        }
        lastFailure?.let { (failedAt, reason) ->
            if (System.nanoTime() - failedAt < RETRY_COOLDOWN_NANOS) return RipgrepAvailability.Failed(reason)
        }
        return when (val outcome = packages.ensure(RuntimeTools.Ripgrep)) {
            RuntimePackageInstaller.Outcome.Ready -> {
                lastFailure = null
                RipgrepAvailability.Ready
            }
            is RuntimePackageInstaller.Outcome.Failed -> {
                lastFailure = System.nanoTime() to outcome.reason
                RipgrepAvailability.Failed(outcome.reason)
            }
            // Stopped by the user: asked again at the next index, without the cooldown.
            RuntimePackageInstaller.Outcome.Cancelled -> RipgrepAvailability.Failed("Installing ripgrep was cancelled")
        }
    }

    override suspend fun index(rootLocation: String): RipgrepIndexOutcome {
        val parser = RipgrepFileParser(rootLocation, MAX_INDEX_FILES)
        val result = guest.streamRecords(
            location = rootLocation,
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
            location = rootLocation,
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

    private fun GuestCommand.Result.warning(operation: String, acceptedExitCodes: Set<Int>): String? {
        if (stoppedEarly || exitCode in acceptedExitCodes) return null
        return "$operation${failureDetail()?.let { ": $it" }.orEmpty()}"
    }

    private companion object {
        const val MAX_INDEX_FILES = 5_000
        const val MAX_MATCHES_PER_FILE = 200
        const val MAX_TOTAL_MATCHES = 1_000
        const val RETRY_COOLDOWN_NANOS = 30_000_000_000L
    }
}

internal sealed interface RipgrepAvailability {
    data object Ready : RipgrepAvailability
    data class Failed(val reason: String) : RipgrepAvailability
}
