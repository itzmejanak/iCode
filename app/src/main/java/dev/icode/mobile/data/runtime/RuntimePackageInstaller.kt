package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.AptPhase
import dev.icode.mobile.domain.AptProgress
import dev.icode.mobile.domain.RuntimeInventory
import dev.icode.mobile.domain.RuntimeTool
import dev.icode.mobile.domain.RuntimeToolAction
import dev.icode.mobile.domain.RuntimeToolDownload
import dev.icode.mobile.domain.RuntimeToolPlan
import dev.icode.mobile.domain.RuntimeToolRules
import dev.icode.mobile.domain.RuntimeToolWork
import dev.icode.mobile.domain.ToolSetupProgress
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one way iCode installs and removes runtime tools (docs/runtime-tools-plan.md §3.4).
 *
 * dpkg has one database and one lock, so apt runs one at a time. A request for a tool already
 * being installed waits for that install instead of starting apt again. The run belongs to [scope],
 * not to the request: a search replaced by the next keystroke, or a screen left, must not kill apt
 * half-way through its package database.
 *
 * An install is two apt runs: the first only downloads (`-d`) and may be cancelled — nothing is
 * installed yet; the second installs what was downloaded, and is never stopped. A tool with a setup
 * ([RuntimeToolSetup], docs/android-toolchain-plan.md §4.1) has it run next, unless its receipt
 * already matches; a removal takes the setup away before apt removes the packages.
 */
internal class RuntimePackageInstaller(
    private val scope: CoroutineScope,
    private val runtimeReady: () -> Boolean,
    /** The packages dpkg has installed now. */
    private val readInstalled: () -> Set<String>,
    /** Runs one non-interactive apt command in the guest, reporting its output line by line. */
    private val apt: suspend (argv: List<String>, onLine: (String) -> Boolean) -> GuestCommand.Result,
    /** Each tool's setup, by [RuntimeTool.setup], with its receipt. */
    private val setups: ToolSetups,
) {
    constructor(layout: RuntimeLayout, scope: CoroutineScope, setups: List<RuntimeToolSetup>) : this(
        scope,
        runtimeReady = { layout.isInstalled },
        readInstalled = DpkgStatus(layout.rootfs)::installed,
        apt = GuestCommand(layout).let { guest ->
            { argv, onLine -> guest.stream(null, argv, "apt", mapOf("DEBIAN_FRONTEND" to "noninteractive"), onLine = onLine) }
        },
        setups = ToolSetups(setups, layout.toolReceipts),
    )

    sealed interface Outcome {
        data object Ready : Outcome
        data object Cancelled : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /** What each tool would download; [failure] says why one could not be measured. */
    data class Measured(val downloads: Map<String, RuntimeToolDownload>, val failure: String?)

    private val dpkg = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<Outcome>>()
    private val runs = ToolRuns()

    /** The run in hand, or the page's last failure until it is dismissed. */
    val work: StateFlow<RuntimeToolWork?> = runs.work

    private val mutableInstalled = MutableStateFlow<RuntimeInventory?>(null)
    /** What is installed as last read — after every run, and on [refresh]; null before the first read. */
    val installed: StateFlow<RuntimeInventory?> = mutableInstalled.asStateFlow()

    fun refresh() {
        mutableInstalled.value = inventory()
    }

    fun isInstalled(tool: RuntimeTool): Boolean = RuntimeToolRules.isInstalled(tool, inventory())

    /** dpkg's packages, and each setup whose receipt says it installed what this build pins. */
    private fun inventory() = RuntimeInventory(readInstalled(), setups.installed())

    /**
     * Installs [tool] unless it is installed; a request while it is being installed waits for that
     * install. Its failure is the caller's to say, and is not kept. A tool with a licence is
     * installed from Runtime Tools, where it is accepted, never from here.
     */
    suspend fun ensure(tool: RuntimeTool): Outcome {
        if (isInstalled(tool)) return Outcome.Ready
        if (!runtimeReady()) return Outcome.Failed("Install the Linux runtime first")
        val run = synchronized(inFlight) {
            inFlight[tool.id]?.takeIf(Deferred<Outcome>::isActive)
                ?: queue(listOf(tool)) { settle(RuntimeToolAction.Install, listOf(tool), keepFailure = false) { install(it, emptySet()) } }
        }
        return run.await()
    }

    /**
     * The tools page's Apply: what is unticked goes first, then what is ticked comes, with [accepted]
     * the licences the user accepted on its License Agreement. A failure stays until [dismiss].
     */
    suspend fun apply(plan: RuntimeToolPlan, accepted: Set<String>): Outcome {
        if (plan.isEmpty) return Outcome.Ready
        if (!runtimeReady()) return Outcome.Failed("Install the Linux runtime first")
        val run = synchronized(inFlight) {
            queue(plan.install) {
                val removed = if (plan.remove.isEmpty()) Outcome.Ready
                else settle(RuntimeToolAction.Remove, plan.remove, keepFailure = true) { remove(it, plan.removePackages) }
                if (removed != Outcome.Ready || plan.install.isEmpty()) removed
                else settle(RuntimeToolAction.Install, plan.install, keepFailure = true) { install(it, accepted) }
            }
        }
        return run.await()
    }

    /** Stops the run in hand while it only reads lists or downloads, or its setup's step allows. */
    fun cancel() = runs.cancel()

    /** The page has shown a kept failure: it goes. */
    fun dismiss() = runs.dismiss()

    /**
     * What installing each of [tools] would download now: apt's archives, as apt computes them
     * (`--print-uris`), and its setup's pinned files unless its receipt matches. The lists are read
     * once first when apt has none that name a package.
     */
    suspend fun measure(tools: List<RuntimeTool>): Measured = dpkg.withLock {
        var listsRead = false
        var failure: String? = null
        val downloads = buildMap {
            tools.forEach { tool ->
                // A tool apt has nothing for (CMake, an NDK) is its setup's files alone.
                if (tool.packages.isEmpty()) {
                    put(tool.id, RuntimeToolDownload(setups.download(tool)))
                    return@forEach
                }
                val output = mutableListOf<String>()
                val command = listOf("apt-get", "install", "--print-uris", "-qq", "--no-install-recommends") + tool.packages
                var result = apt(command) { line -> output += line; true }
                if (result.exitCode != 0 && !listsRead) {
                    listsRead = true
                    val update = apt(listOf("apt-get", "update")) { true }
                    if (update.exitCode != 0) throw IOException(failure("Could not read the package lists", update, emptyList()))
                    output.clear()
                    result = apt(command) { line -> output += line; true }
                }
                if (result.exitCode == 0) put(tool.id, RuntimeToolDownload(output.mapNotNull(::archive).toMap() + setups.download(tool)))
                else failure = failure ?: failure("Could not measure ${tool.name}", result, output)
            }
        }
        Measured(downloads, failure)
    }

    /** A run of its own, queued behind any other; each of [tools] waits on it while it lasts. */
    private fun queue(tools: List<RuntimeTool>, body: suspend () -> Outcome): Deferred<Outcome> =
        scope.async(Dispatchers.IO) { dpkg.withLock { body() } }.also { run -> tools.forEach { inFlight[it.id] = run } }

    /** Publishes [tools]' run as it goes, and settles it: gone when it succeeds, kept when it fails and [keepFailure]. */
    private suspend fun settle(
        action: RuntimeToolAction,
        tools: List<RuntimeTool>,
        keepFailure: Boolean,
        body: suspend (ToolRuns.ToolRun) -> Outcome,
    ): Outcome {
        // The run that held the lock before this one may have brought them.
        if (action == RuntimeToolAction.Install && tools.all(::isInstalled)) return Outcome.Ready
        val run = runs.begin(RuntimeToolWork(action, tools))
        val outcome = try {
            body(run)
        } catch (cancelled: CancellationException) {
            if (!runs.stopRequested) throw cancelled
            Outcome.Cancelled
        } catch (error: Throwable) {
            Outcome.Failed(error.message?.takeIf(String::isNotBlank) ?: "Could not change the runtime's tools")
        } finally {
            refresh()
        }
        runs.end(run, (outcome as? Outcome.Failed)?.takeIf { keepFailure }?.reason)
        return outcome
    }

    private suspend fun install(run: ToolRuns.ToolRun, accepted: Set<String>): Outcome {
        run.work.tools.firstOrNull { it.licence != null && it.licence !in accepted }?.let { tool ->
            return Outcome.Failed("${tool.name} installs only once its licence is accepted, in Runtime Tools")
        }
        val packages = run.work.tools.flatMap { it.packages }.distinct()
        if (packages.isNotEmpty()) installPackages(run, packages)?.let { return it }
        run.work.tools.mapNotNull { it.setup }.distinct().map(setups::of).forEach { setUp(run, it) }
        return if (run.work.tools.all(::isInstalled)) Outcome.Ready else Outcome.Failed("Could not install ${run.names}")
    }

    /** apt downloads [packages], then installs them; null when it did, else why not. */
    private suspend fun installPackages(run: ToolRuns.ToolRun, packages: List<String>): Outcome? {
        val download = listOf("apt-get", "install", "-d", "-y", "--no-install-recommends") + packages
        var fetched = run.apt(download, stoppable = true)
        if (fetched.exitCode != 0) {
            // A fresh runtime has no package lists, and old ones name archives the mirror no longer
            // has: read them again, then try once more.
            run.progress = AptProgress(phase = AptPhase.Updating)
            val update = run.apt(listOf("apt-get", "update"), stoppable = true)
            if (update.exitCode != 0) return Outcome.Failed(failure("Could not update runtime packages", update, run.output))
            run.progress = AptProgress()
            fetched = run.apt(download, stoppable = true)
            if (fetched.exitCode != 0) return Outcome.Failed(failure("Could not download ${run.names}", fetched, run.output))
        }
        run.progress = AptProgress(phase = AptPhase.Installing, total = run.progress.total)
        val installed = run.apt(listOf("apt-get", "install", "-y", "--no-install-recommends") + packages, stoppable = false)
        if (installed.exitCode != 0 || !readInstalled().containsAll(packages)) {
            return Outcome.Failed(failure("Could not install ${run.names}", installed, run.output))
        }
        return null
    }

    /**
     * Removes [packages] and what only they brought, and nothing a package that stays needs: they
     * are marked as no longer wanted, and apt says which of them, and of what they brought, it would
     * now remove. What it would have removed before the mark (another cause's leftovers) stays.
     */
    private suspend fun remove(run: ToolRuns.ToolRun, packages: List<String>): Outcome {
        run.work.tools.forEach { tool ->
            val setup = tool.setup?.let(setups::of) ?: return@forEach
            run.publish(run.work.copy(setup = ToolSetupProgress("Removing ${tool.name}", cancellable = false)))
            setup.remove()
            setups.forget(setup)
        }
        // Tools apt has nothing for (CMake, an NDK): their setups were all there was.
        if (packages.isEmpty()) return Outcome.Ready
        run.publish(run.work.copy(setup = null, progress = AptProgress(phase = AptPhase.Removing)))
        val before = simulatedRemovals()
        val mark = apt(listOf("apt-mark", "auto") + packages) { true }
        if (mark.exitCode != 0) return Outcome.Failed(failure("Could not remove ${run.names}", mark, emptyList()))
        val after = simulatedRemovals()
        val targets = after - before + packages.filter { it in after }
        val needed = packages.filterNot { it in targets }
        if (needed.isNotEmpty()) apt(listOf("apt-mark", "manual") + needed) { true }
        if (targets.isEmpty()) return Outcome.Failed("${run.names} cannot be removed: other installed packages need it")
        val removed = run.apt(listOf("apt-get", "remove", "-y") + targets.sorted(), stoppable = false)
        return if (removed.exitCode == 0) Outcome.Ready
        else Outcome.Failed(failure("Could not remove ${run.names}", removed, run.output))
    }

    /** What `apt-get autoremove` would remove now, from its simulation: `Remv libcurl4t64 [8.5.0-2ubuntu10.15]`. */
    private suspend fun simulatedRemovals(): Set<String> {
        val names = mutableSetOf<String>()
        val result = apt(listOf("apt-get", "autoremove", "-s")) { line ->
            REMOVAL.find(line)?.let { names += it.groupValues[1].substringBefore(':') }
            true
        }
        if (result.exitCode != 0) throw IOException(failure("Could not read what apt would remove", result, emptyList()))
        return names
    }

    /** One apt command of [this] run, its lines read into the run's progress as they come. */
    private suspend fun ToolRuns.ToolRun.apt(argv: List<String>, stoppable: Boolean): GuestCommand.Result = stoppably(stoppable) {
        this@RuntimePackageInstaller.apt(argv) { line ->
            printed(line) { it.copy(progress = it.progress.next(line)) }
            true
        }
    }

    /**
     * Runs [setup] unless its receipt matches, then configures it either way; Cancel stops it where
     * its step says it may. Its receipt is written once both are done.
     */
    private suspend fun setUp(run: ToolRuns.ToolRun, setup: RuntimeToolSetup) {
        if (setups.matches(setup)) return setup.configure()
        run.stoppably(true) { setup.install(onProgress = { run.publish(run.work.copy(setup = it)) }, onLine = run::printed) }
        setup.configure()
        setups.record(setup)
    }

    private fun failure(fallback: String, result: GuestCommand.Result, output: Collection<String>): String {
        val detail = result.failureDetail() ?: output.lastOrNull()
        return if (detail == null) fallback else "$fallback: $detail"
    }

    private companion object {
        /** `'http://…/ripgrep_14.1.0-1_arm64.deb' ripgrep_14.1.0-1_arm64.deb 1455428 MD5Sum:…`. */
        val URI = Regex("""^'[^']+' (\S+) (\d+) """)
        val REMOVAL = Regex("""^Remv (\S+)""")

        fun archive(line: String): Pair<String, Long>? = URI.find(line)?.let { it.groupValues[1] to it.groupValues[2].toLong() }
    }
}
