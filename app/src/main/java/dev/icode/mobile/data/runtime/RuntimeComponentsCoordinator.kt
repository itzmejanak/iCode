package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.ComponentRules
import dev.icode.mobile.domain.RuntimeComponents
import dev.icode.mobile.domain.WorkbenchIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The runtime's components (docs/runtime-components-plan.md): what dpkg has, what each would
 * download, and the Components page's Apply. Nothing here waits on the mutation lock — apt touches
 * the runtime, not the workspace.
 */
internal class RuntimeComponentsCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val scope: CoroutineScope,
    /** Also the features' way to a component they need: Git, the SSH client, search. */
    val installer: RuntimePackageInstaller,
) {
    private var measureJob: Job? = null

    /** Follows dpkg and the apt run into the snapshot. What a component would download changes with what is installed. */
    fun start() {
        scope.launch { installer.refresh() }
        scope.launch {
            combine(installer.installed, installer.work, ::Pair).collect { (installed, work) ->
                workbench.update { snapshot ->
                    val before = snapshot.components
                    val changed = installed != before.installedPackages
                    snapshot.copy(
                        components = before.copy(
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

    fun handle(intent: WorkbenchIntent.ComponentIntent) {
        when (intent) {
            WorkbenchIntent.MeasureComponents -> measure()
            is WorkbenchIntent.ApplyComponents -> apply(intent.chosen)
            WorkbenchIntent.CancelComponents -> installer.cancel()
            WorkbenchIntent.DismissComponents -> installer.dismiss()
        }
    }

    /** Measures each component neither installed nor measured yet; once at a time. */
    private fun measure() {
        if (measureJob?.isActive == true) return
        val components = workbench.current.components
        val wanted = RuntimeComponents.all.filterNot { components.isInstalled(it) || it.id in components.downloads }
        if (components.installedPackages == null || wanted.isEmpty()) return
        measureJob = scope.launch {
            workbench.update { it.copy(components = it.components.copy(measuring = true, measureFailure = null)) }
            try {
                val measured = installer.measure(wanted)
                workbench.update {
                    it.copy(components = it.components.copy(downloads = it.components.downloads + measured.downloads, measureFailure = measured.failure))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val reason = workbench.failureText("runtime", "Could not measure the components", error)
                workbench.update { it.copy(components = it.components.copy(measureFailure = reason)) }
            } finally {
                workbench.update { it.copy(components = it.components.copy(measuring = false)) }
            }
        }
    }

    /** A measure still reading the lists would hold up the install behind it; the install reads them itself. */
    private fun apply(chosen: Set<String>) {
        measureJob?.cancel()
        scope.launch {
            installer.apply(ComponentRules.plan(chosen, installer.installed.value ?: return@launch))
        }
    }
}
