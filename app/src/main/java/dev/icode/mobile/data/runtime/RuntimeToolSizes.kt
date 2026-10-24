package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.RuntimeTool
import dev.icode.mobile.domain.RuntimeToolDownload
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What installing each runtime tool would download now (docs/onboarding-polish-plan.md §3.4): apt's
 * archives, as apt computes them (`--print-uris`), and its setup's pinned files unless its receipt
 * matches. apt takes no lock to measure, and neither does this, so a feature's install never waits
 * on a size: tools naming the same packages are measured once, [AT_ONCE] at a time. Only the lists
 * are read under dpkg's lock ([readLists]), once, when apt has none that name a package. Measuring
 * stops when an install or a removal runs ([runInHand]): it changes what is measured.
 */
internal class RuntimeToolSizes(
    /** apt's `--print-uris` for some packages, its output line by line ([RuntimePackageInstaller], apt's one owner). */
    private val printUris: suspend (packages: List<String>, onLine: (String) -> Boolean) -> GuestCommand.Result,
    private val setups: ToolSetups,
    private val readLists: suspend () -> Unit,
    private val runInHand: () -> Boolean,
) {
    /**
     * Measures [tools] in their order, saying each to [onMeasured] as it lands and [onLists] before the
     * lists are read; a size that failed before they were read is measured again. Returns why a tool
     * could not be measured; null when none failed.
     */
    suspend fun measure(
        tools: List<RuntimeTool>,
        onLists: () -> Unit,
        onMeasured: (Map<String, RuntimeToolDownload>) -> Unit,
    ): String? = coroutineScope {
        // A tool apt has nothing for (CMake) is its setup's files alone.
        val (setupsOnly, viaApt) = tools.partition { it.packages.isEmpty() }
        if (setupsOnly.isNotEmpty()) onMeasured(setupsOnly.associate { it.id to RuntimeToolDownload(setups.download(it)) })
        val queue = Channel<List<RuntimeTool>>(Channel.UNLIMITED)
        viaApt.groupBy { it.packages }.values.forEach(queue::trySend)
        queue.close()
        val lists = Mutex()
        val listsRead = AtomicBoolean(false)
        val failure = AtomicReference<String?>(null)
        List(AT_ONCE) {
            launch {
                for (sharing in queue) {
                    if (runInHand()) break
                    val packages = sharing.first().packages
                    val readBefore = listsRead.get()
                    var (result, output) = archivesOf(packages)
                    if (result.exitCode != 0 && !readBefore) {
                        lists.withLock {
                            if (!listsRead.get()) {
                                onLists()
                                readLists()
                                listsRead.set(true)
                            }
                        }
                        if (runInHand()) break
                        archivesOf(packages).let { (again, printed) -> result = again; output = printed }
                    }
                    if (result.exitCode == 0) {
                        val archives = output.mapNotNull(::archive).toMap()
                        onMeasured(sharing.associate { it.id to RuntimeToolDownload(archives + setups.download(it)) })
                    } else {
                        failure.compareAndSet(null, aptFailure("Could not measure ${sharing.first().name}", result, output))
                    }
                }
            }
        }.joinAll()
        failure.get()
    }

    /** [printUris] for [packages], with what it printed: each archive it would fetch. */
    private suspend fun archivesOf(packages: List<String>): Pair<GuestCommand.Result, List<String>> {
        val output = mutableListOf<String>()
        val result = printUris(packages) { line -> output += line; true }
        return result to output
    }

    private companion object {
        /**
         * Sizes measured at once: each is one CPU's work under PRoot (measured 2026-10-24 on a 4-core
         * host: nine tools in 10.9 s one at a time, 4.4 s three at a time, 5.1 s eight at a time), and
         * three are six processes, far inside Android's limit on an app's children.
         */
        const val AT_ONCE = 3

        /** `'http://…/ripgrep_14.1.0-1_arm64.deb' ripgrep_14.1.0-1_arm64.deb 1455428 MD5Sum:…`. */
        val URI = Regex("""^'[^']+' (\S+) (\d+) """)

        fun archive(line: String): Pair<String, Long>? = URI.find(line)?.let { it.groupValues[1] to it.groupValues[2].toLong() }
    }
}
