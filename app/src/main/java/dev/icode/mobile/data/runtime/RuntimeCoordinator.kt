package dev.icode.mobile.data.runtime

import android.content.Context
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.system.AndroidSystemAccess
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.SystemAccess
import dev.icode.mobile.domain.SystemPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Onboarding and the runtime (S1): whether the Linux runtime is there, its
 * install, repair and reset, what is measured about it, and what Android
 * allows iCode (notifications, battery).
 */
internal class RuntimeCoordinator(
    private val appContext: Context,
    private val workbench: WorkbenchStateHolder,
    private val scope: CoroutineScope,
    private val layout: RuntimeLayout,
    private val systemAccess: AndroidSystemAccess,
    /** A rebuild would pull the runtime out from under a running terminal or language server. */
    private val sessionsRunning: () -> Boolean,
) {
    private val inspector = RuntimeInspector(layout, RuntimeInstaller(appContext, layout).prootVersion())
    private var installJob: Job? = null
    private var detailsJob: Job? = null

    /** Whether an install, repair or reset is running now. */
    val installing: Boolean get() = installJob?.isActive == true

    /** Publishes what is actually on disk; never claims readiness it has not checked. */
    fun reportState() {
        val preservedHome = RuntimeHomeBackup(layout.rootfs, layout.runtimeRoot).hasPreservedHome
        val state = when {
            !layout.binariesPresent ->
                RuntimeState.Failed("The runtime binaries are missing from this build", recoverable = false)
            layout.isInstalled -> RuntimeState.Ready(layout.installedRelease() ?: "Linux runtime")
            preservedHome -> RuntimeState.NotInstalled("A runtime repair was interrupted; your home is preserved for retry")
            else -> RuntimeState.NotInstalled("The Linux runtime is not installed yet")
        }
        workbench.update {
            it.copy(
                runtime = state,
                runtimeHealthy = state is RuntimeState.Ready,
                runtimeInstallReason = if (preservedHome && state !is RuntimeState.Ready)
                    RuntimeInstallReason.Repair else RuntimeInstallReason.Initial,
                systemAccess = systemAccess.read(),
            )
        }
    }

    /**
     * Reading the runtime's own files changes nothing, so it never waits on the
     * mutation lock. The quick facts are published first and the size after;
     * a size already measured stays shown until the new one replaces it.
     */
    fun refreshDetails() {
        if (detailsJob?.isActive == true) return
        detailsJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val facts = inspector.facts()
                workbench.update { it.copy(runtimeDetails = facts.copy(rootfsSize = it.runtimeDetails.rootfsSize)) }
                val size = inspector.rootfsSize()
                workbench.update { it.copy(runtimeDetails = it.runtimeDetails.copy(rootfsSize = size)) }
            }.onFailure { BackendLog.w("runtime", "Runtime inspection failed", it) }
        }
    }

    /** One state-driven installer for first install, repair and reset. */
    suspend fun install(reason: RuntimeInstallReason): Boolean {
        if (installing || workbench.current.runtime is RuntimeState.Downloading ||
            workbench.current.runtime is RuntimeState.Installing) {
            workbench.message("Runtime installation is already running")
            return false
        }
        if (reason != RuntimeInstallReason.Initial && sessionsRunning()) {
            workbench.message("Close running terminal and language service sessions before rebuilding the runtime")
            return false
        }
        val label = when (reason) {
            RuntimeInstallReason.Initial -> "Preparing runtime installation"
            RuntimeInstallReason.Repair -> "Preparing runtime repair"
            RuntimeInstallReason.Reset -> "Preparing runtime reset"
        }
        workbench.update { it.copy(
            runtime = RuntimeState.Installing(label),
            runtimeHealthy = false,
            runtimeInstallReason = reason,
        ) }
        // The Installing state above is background work: the foreground service keeps the
        // process while it lasts (docs/long-operations-plan.md §6).
        installJob = scope.launch {
            try {
                RuntimeInstaller(appContext, layout).install(reason).collect { state ->
                    workbench.update { it.copy(runtime = state, runtimeHealthy = state is RuntimeState.Ready) }
                    if (state is RuntimeState.Failed) {
                        workbench.message(state.reason)
                    }
                    if (state is RuntimeState.Ready) refreshDetails()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = workbench.failureText("runtime", "Runtime installation failed", error)
                workbench.update { it.copy(runtime = RuntimeState.Failed(message), runtimeHealthy = false) }
                workbench.message(message)
            }
        }
        return true
    }

    fun refreshSystemAccess(): SystemAccess {
        val access = systemAccess.read()
        workbench.update { it.copy(systemAccess = access) }
        return access
    }

    /** Android's prompt closed: onboarding has now offered [permission], whatever the answer. */
    fun permissionAnswered(permission: SystemPermission): Boolean {
        systemAccess.markOffered(permission)
        return refreshSystemAccess().granted(permission)
    }

    fun declinePermission(permission: SystemPermission) {
        systemAccess.markOffered(permission)
        refreshSystemAccess()
    }
}
