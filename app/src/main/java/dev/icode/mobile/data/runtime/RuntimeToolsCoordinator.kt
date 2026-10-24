package dev.icode.mobile.data.runtime

import android.content.Context
import dev.icode.mobile.data.android.AndroidNdkSetup
import dev.icode.mobile.data.android.AndroidSdkSetup
import dev.icode.mobile.data.android.CmakeSetup
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.RuntimeToolMeasuring
import dev.icode.mobile.domain.RuntimeToolRules
import dev.icode.mobile.domain.RuntimeToolsState
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
    /** A licence's text, by id ([RuntimeLicences]). */
    private val licenceText: (String) -> String,
) {
    /** The tools catalog's setups ([dev.icode.mobile.domain.RuntimeTool.setup]) and licences, registered in one place. */
    constructor(stateHolder: ICodeStateHolder, scope: CoroutineScope, layout: RuntimeLayout, context: Context) : this(
        stateHolder,
        scope,
        RuntimePackageInstaller(
            layout,
            scope,
            listOf(AndroidSdkSetup(layout, context), CmakeSetup(layout, context)) + RuntimeTools.ndks.keys.map { AndroidNdkSetup(layout, context, it) },
        ),
        layout.toolsOffer,
        licenceText = { RuntimeLicences.text(context, it) },
    )

    private val measureLock = Any()
    private var measureJob: Job? = null
    /** The page asked to measure while a measure ran: it runs again once that one ends. */
    private var askedAgain = false

    /**
     * Reads the licences tools accept, for the License Agreement. Follows what is installed and the
     * run into the snapshot; what is installed is read again whenever the runtime
     * becomes ready — a rebuilt one has other packages. What a tool would download changes
     * with what is installed.
     */
    fun start() {
        scope.launch {
            val licences = RuntimeTools.all.mapNotNull { it.licence }.distinct().associateWith(licenceText)
            stateHolder.update { it.copy(runtimeTools = it.runtimeTools.copy(licences = licences)) }
        }
        scope.launch {
            stateHolder.snapshot.map { it.runtime is RuntimeState.Ready }.distinctUntilChanged().collect { installer.refresh() }
        }
        scope.launch {
            combine(installer.installed, installer.work, ::Pair).collect { (installed, work) ->
                stateHolder.update { snapshot ->
                    val before = snapshot.runtimeTools
                    val changed = installed != before.installed
                    snapshot.copy(
                        runtimeTools = before.copy(
                            installed = installed,
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
            is ICodeIntent.ApplyRuntimeTools -> apply(intent.chosen, intent.accepted).also { answered() }
            ICodeIntent.SkipRuntimeTools -> answered()
            ICodeIntent.CancelRuntimeTools -> installer.cancel()
            ICodeIntent.DismissRuntimeTools -> installer.dismiss()
        }
    }

    /**
     * Measures each tool neither installed nor measured yet, the Standard tools first, each size shown
     * as it lands (docs/onboarding-polish-plan.md §3.4). One measure runs at a time; one asked for
     * while it runs follows it, so the page's request after an install is never lost. A size is kept
     * only while what is installed is what it was measured against.
     */
    private fun measure() = synchronized(measureLock) { measureLocked() }

    private fun measureLocked() {
        if (measureJob?.isActive == true) {
            askedAgain = true
            return
        }
        val tools = stateHolder.current.runtimeTools
        val installed = tools.installed ?: return
        val wanted = RuntimeTools.all.filterNot { tools.isInstalled(it) || it.id in tools.downloads }
            .sortedBy { it.id !in RuntimeTools.standard }
        if (wanted.isEmpty()) return
        fun measured(change: (RuntimeToolsState) -> RuntimeToolsState) = stateHolder.update { snapshot ->
            if (snapshot.runtimeTools.installed != installed) snapshot else snapshot.copy(runtimeTools = change(snapshot.runtimeTools))
        }
        measureJob = scope.launch {
            measured { it.copy(measuring = RuntimeToolMeasuring.Sizes, measureFailure = null) }
            try {
                val failure = installer.sizes.measure(
                    wanted,
                    onLists = { measured { it.copy(measuring = RuntimeToolMeasuring.Lists) } },
                    onMeasured = { found -> measured { it.copy(measuring = RuntimeToolMeasuring.Sizes, downloads = it.downloads + found) } },
                )
                measured { it.copy(measureFailure = failure) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val reason = stateHolder.failureText("runtime", "Could not measure the tools' downloads", error)
                measured { it.copy(measureFailure = reason) }
            } finally {
                stateHolder.update { it.copy(runtimeTools = it.runtimeTools.copy(measuring = null)) }
            }
        }.also { job ->
            job.invokeOnCompletion {
                synchronized(measureLock) {
                    if (askedAgain) {
                        askedAgain = false
                        measureLocked()
                    }
                }
            }
        }
    }

    /** The page offered after a build has been answered; it is not offered again. */
    private fun answered() {
        if (!stateHolder.current.runtimeTools.offerPending) return
        offer.delete()
        stateHolder.update { it.copy(runtimeTools = it.runtimeTools.copy(offerPending = false)) }
    }

    /** An install waits for lists being read, which it needs; sizes stop once it begins ([RuntimeToolSizes]). */
    private fun apply(chosen: Set<String>, accepted: Set<String>) {
        scope.launch {
            installer.apply(RuntimeToolRules.plan(chosen, installer.installed.value ?: return@launch), accepted)
        }
    }
}
