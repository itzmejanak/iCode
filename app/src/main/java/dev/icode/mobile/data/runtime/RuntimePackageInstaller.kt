package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.AptPhase
import dev.icode.mobile.domain.AptProgress
import dev.icode.mobile.domain.ComponentAction
import dev.icode.mobile.domain.ComponentDownload
import dev.icode.mobile.domain.ComponentPlan
import dev.icode.mobile.domain.ComponentRules
import dev.icode.mobile.domain.ComponentWork
import dev.icode.mobile.domain.RuntimeComponent
import java.io.IOException
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one way iCode installs and removes runtime components (docs/runtime-components-plan.md §3.4).
 *
 * dpkg has one database and one lock, so apt runs one at a time. A request for a component already
 * being installed waits for that install instead of starting apt again. The run belongs to [scope],
 * not to the request: a search replaced by the next keystroke, or a screen left, must not kill apt
 * half-way through its package database.
 *
 * An install is two apt runs: the first only downloads (`-d`) and may be cancelled — nothing is
 * installed yet; the second installs what was downloaded, and is never stopped.
 */
internal class RuntimePackageInstaller(
    private val scope: CoroutineScope,
    private val runtimeReady: () -> Boolean,
    /** The packages dpkg has installed now. */
    private val readInstalled: () -> Set<String>,
    /** Runs one non-interactive apt command in the guest, reporting its output line by line. */
    private val apt: suspend (argv: List<String>, onLine: (String) -> Boolean) -> GuestCommand.Result,
) {
    constructor(layout: RuntimeLayout, scope: CoroutineScope) : this(
        scope,
        runtimeReady = { layout.isInstalled },
        readInstalled = DpkgStatus(layout.rootfs)::installed,
        apt = GuestCommand(layout).let { guest ->
            { argv, onLine -> guest.stream(null, argv, "apt", mapOf("DEBIAN_FRONTEND" to "noninteractive"), onLine) }
        },
    )

    sealed interface Outcome {
        data object Ready : Outcome
        data object Cancelled : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /** What each component would download; [failure] says why one could not be measured. */
    data class Measured(val downloads: Map<String, ComponentDownload>, val failure: String?)

    private val dpkg = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<Outcome>>()

    private val mutableWork = MutableStateFlow<ComponentWork?>(null)
    /** The run in hand, or the page's last failure until it is dismissed. */
    val work: StateFlow<ComponentWork?> = mutableWork.asStateFlow()

    private val mutableInstalled = MutableStateFlow<Set<String>?>(null)
    /** dpkg's packages as last read — after every run, and on [refresh]; null before the first read. */
    val installed: StateFlow<Set<String>?> = mutableInstalled.asStateFlow()

    /** The apt command in hand, while stopping it leaves nothing half done. */
    @Volatile private var stoppable: Job? = null
    @Volatile private var stopRequested = false

    fun refresh() {
        mutableInstalled.value = readInstalled()
    }

    fun isInstalled(component: RuntimeComponent): Boolean = ComponentRules.isInstalled(component, readInstalled())

    /**
     * Installs [component] unless dpkg has it; a request while it is being installed waits for that
     * install. Its failure is the caller's to say, and is not kept.
     */
    suspend fun ensure(component: RuntimeComponent): Outcome {
        if (isInstalled(component)) return Outcome.Ready
        if (!runtimeReady()) return Outcome.Failed("Install the Linux runtime first")
        val run = synchronized(inFlight) {
            inFlight[component.id]?.takeIf(Deferred<Outcome>::isActive)
                ?: queue(listOf(component)) { settle(ComponentAction.Install, listOf(component), keepFailure = false, ::install) }
        }
        return run.await()
    }

    /** The Components page's Apply: what is unticked goes first, then what is ticked comes. A failure stays until [dismiss]. */
    suspend fun apply(plan: ComponentPlan): Outcome {
        if (plan.isEmpty) return Outcome.Ready
        if (!runtimeReady()) return Outcome.Failed("Install the Linux runtime first")
        val run = synchronized(inFlight) {
            queue(plan.install) {
                val removed = if (plan.remove.isEmpty()) Outcome.Ready
                else settle(ComponentAction.Remove, plan.remove, keepFailure = true) { remove(it, plan.removePackages) }
                if (removed != Outcome.Ready || plan.install.isEmpty()) removed
                else settle(ComponentAction.Install, plan.install, keepFailure = true, ::install)
            }
        }
        return run.await()
    }

    /** Stops the run in hand while it only reads lists or downloads. */
    fun cancel() {
        if (mutableWork.value?.cancellable != true) return
        stopRequested = true
        stoppable?.cancel()
    }

    /** The page has shown a kept failure: it goes. */
    fun dismiss() {
        if (mutableWork.value?.failure != null) mutableWork.value = null
    }

    /**
     * What installing each of [components] would download now, as apt computes it
     * (`--print-uris`); the lists are read once first when apt has none that name a package.
     */
    suspend fun measure(components: List<RuntimeComponent>): Measured = dpkg.withLock {
        var listsRead = false
        var failure: String? = null
        val downloads = buildMap {
            components.forEach { component ->
                val output = mutableListOf<String>()
                val command = listOf("apt-get", "install", "--print-uris", "-qq", "--no-install-recommends") + component.packages
                var result = apt(command) { line -> output += line; true }
                if (result.exitCode != 0 && !listsRead) {
                    listsRead = true
                    val update = apt(listOf("apt-get", "update")) { true }
                    if (update.exitCode != 0) throw IOException(failure("Could not read the package lists", update, emptyList()))
                    output.clear()
                    result = apt(command) { line -> output += line; true }
                }
                if (result.exitCode == 0) put(component.id, ComponentDownload(output.mapNotNull(::archive).toMap()))
                else failure = failure ?: failure("Could not measure ${component.name}", result, output)
            }
        }
        Measured(downloads, failure)
    }

    /** A run of its own, queued behind any other; each of [components] waits on it while it lasts. */
    private fun queue(components: List<RuntimeComponent>, body: suspend () -> Outcome): Deferred<Outcome> =
        scope.async(Dispatchers.IO) { dpkg.withLock { body() } }.also { run -> components.forEach { inFlight[it.id] = run } }

    /** Publishes [components]' run as it goes, and settles it: gone when it succeeds, kept when it fails and [keepFailure]. */
    private suspend fun settle(
        action: ComponentAction,
        components: List<RuntimeComponent>,
        keepFailure: Boolean,
        body: suspend (ComponentRun) -> Outcome,
    ): Outcome {
        // The run that held the lock before this one may have brought them.
        if (action == ComponentAction.Install && components.all(::isInstalled)) return Outcome.Ready
        stopRequested = false
        val run = ComponentRun(ComponentWork(action, components))
        mutableWork.value = run.work
        val outcome = try {
            body(run)
        } catch (cancelled: CancellationException) {
            if (!stopRequested) throw cancelled
            Outcome.Cancelled
        } catch (error: Throwable) {
            Outcome.Failed(error.message?.takeIf(String::isNotBlank) ?: "Could not change runtime components")
        } finally {
            stoppable = null
            refresh()
        }
        mutableWork.value = (outcome as? Outcome.Failed)?.takeIf { keepFailure }?.let { run.work.copy(failure = it.reason) }
        return outcome
    }

    private suspend fun install(run: ComponentRun): Outcome {
        val packages = run.work.components.flatMap { it.packages }.distinct()
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
        return if (installed.exitCode == 0 && run.work.components.all(::isInstalled)) Outcome.Ready
        else Outcome.Failed(failure("Could not install ${run.names}", installed, run.output))
    }

    /**
     * Removes [packages] and what only they brought, and nothing a package that stays needs: they
     * are marked as no longer wanted, and apt says which of them, and of what they brought, it would
     * now remove. What it would have removed before the mark (another cause's leftovers) stays.
     */
    private suspend fun remove(run: ComponentRun, packages: List<String>): Outcome {
        run.progress = AptProgress(phase = AptPhase.Removing)
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

    /** One published run: its progress and apt's last lines, as they come. */
    private inner class ComponentRun(var work: ComponentWork) {
        val output = ArrayDeque<String>()
        val names: String get() = work.components.joinToString { it.name }

        var progress: AptProgress
            get() = work.progress
            set(value) = publish(work.copy(progress = value))

        suspend fun apt(argv: List<String>, stoppable: Boolean): GuestCommand.Result = coroutineScope {
            val command = async {
                this@RuntimePackageInstaller.apt(argv) { line ->
                    if (line.isNotBlank()) {
                        if (output.size == MAX_OUTPUT_LINES) output.removeFirst()
                        output.addLast(line.trim())
                        publish(work.copy(progress = work.progress.next(line), log = output.toList()))
                    }
                    true
                }
            }
            this@RuntimePackageInstaller.stoppable = command.takeIf { stoppable }
            if (stoppable && stopRequested) command.cancel()
            command.await()
        }

        private fun publish(next: ComponentWork) {
            work = next
            mutableWork.value = next
        }
    }

    private fun failure(fallback: String, result: GuestCommand.Result, output: Collection<String>): String {
        val detail = result.failureDetail() ?: output.lastOrNull()
        return if (detail == null) fallback else "$fallback: $detail"
    }

    private companion object {
        const val MAX_OUTPUT_LINES = 20

        /** `'http://…/ripgrep_14.1.0-1_arm64.deb' ripgrep_14.1.0-1_arm64.deb 1455428 MD5Sum:…`. */
        val URI = Regex("""^'[^']+' (\S+) (\d+) """)
        val REMOVAL = Regex("""^Remv (\S+)""")

        fun archive(line: String): Pair<String, Long>? = URI.find(line)?.let { it.groupValues[1] to it.groupValues[2].toLong() }
    }
}
