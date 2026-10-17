package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.RuntimeToolRules
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.ICodeIntent
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The runtime's tools (docs/runtime-tools-plan.md): what dpkg has, what each would
 * download, and the tools page's Apply. Nothing here waits on the mutation lock — apt touches
 * the runtime, not the workspace.
 */
internal class RuntimeToolsCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    /** Also the features' way to a tool they need: Git, the SSH client, search. */
    val installer: RuntimePackageInstaller,
    /** [RuntimeLayout.toolsOffer]: the page offered after a build, until it is answered. */
    private val offer: File,
) {
    private var measureJob: Job? = null

    /**
     * Follows dpkg and the apt run into the snapshot; dpkg is read again whenever the runtime
     * becomes ready — a rebuilt one has other packages. What a tool would download changes
     * with what is installed.
     */
    fun start() {
        scope.launch {
            stateHolder.snapshot.map { it.runtime is RuntimeState.Ready }.distinctUntilChanged().collect { installer.refresh() }
        }
        scope.launch {
            combine(installer.installed, installer.work, ::Pair).collect { (installed, work) ->
                stateHolder.update { snapshot ->
                    val before = snapshot.runtimeTools
                    val changed = installed != before.installedPackages
                    snapshot.copy(
                        runtimeTools = before.copy(
                            installedPackages = installed,
                            work = work,
                            downloads = if (changed) emptyMap() else before.downloads,
                            measureFailure = if (changed) null else before.measureFailure,
                        ),
                    )
                }
            }
        }
    }

    fun handle(intent: ICodeIntent.RuntimeToolsIntent) {
        when (intent) {
            ICodeIntent.MeasureRuntimeTools -> measure()
            is ICodeIntent.ApplyRuntimeTools -> apply(intent.chosen).also { answered() }
            ICodeIntent.SkipRuntimeTools -> answered()
            ICodeIntent.CancelRuntimeTools -> installer.cancel()
            ICodeIntent.DismissRuntimeTools -> installer.dismiss()
        }
    }

    /** Measures each tool neither installed nor measured yet; once at a time. */
    private fun measure() {
        if (measureJob?.isActive == true) return
        val tools = stateHolder.current.runtimeTools
        val wanted = RuntimeTools.all.filterNot { tools.isInstalled(it) || it.id in tools.downloads }
        if (tools.installedPackages == null || wanted.isEmpty()) return
        measureJob = scope.launch {
            stateHolder.update { it.copy(runtimeTools = it.runtimeTools.copy(measuring = true, measureFailure = null)) }
            try {
                val measured = installer.measure(wanted)
                stateHolder.update {
                    it.copy(runtimeTools = it.runtimeTools.copy(downloads = it.runtimeTools.downloads + measured.downloads, measureFailure = measured.failure))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val reason = stateHolder.failureText("runtime", "Could not measure the tools' downloads", error)
                stateHolder.update { it.copy(runtimeTools = it.runtimeTools.copy(measureFailure = reason)) }
            } finally {
                stateHolder.update { it.copy(runtimeTools = it.runtimeTools.copy(measuring = false)) }
            }
        }
    }

    /** The page offered after a build has been answered; it is not offered again. */
    private fun answered() {
        if (!stateHolder.current.runtimeTools.offerPending) return
        offer.delete()
        stateHolder.update { it.copy(runtimeTools = it.runtimeTools.copy(offerPending = false)) }
    }

    /** A measure still reading the lists would hold up the install behind it; the install reads them itself. */
    private fun apply(chosen: Set<String>) {
        measureJob?.cancel()
        scope.launch {
            installer.apply(RuntimeToolRules.plan(chosen, installer.installed.value ?: return@launch))
        }
    }
}
