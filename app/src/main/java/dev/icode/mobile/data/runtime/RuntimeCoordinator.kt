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
 * allows iCode (storage, notifications, battery).
 */
internal class RuntimeCoordinator(
    private val appContext: Context,
    private val workbench: WorkbenchStateHolder,
    private val scope: CoroutineScope,
    private val layout: RuntimeLayout,
    private val systemAccess: AndroidSystemAccess,
    /** A rebuild would pull the runtime out from under a running terminal or language server. */
    private val sessionsRunning: () -> Boolean,
    /** Whether a recent workspace can be opened follows storage access. */
    private val onStorageAccessChanged: () -> Unit,
) {
    private val inspector = RuntimeInspector(layout, RuntimeInstaller(appContext, layout).prootVersion())
    private var installJob: Job? = null

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

    /** Reading the runtime's own files changes nothing, so it never waits on the mutation lock. */
    fun refreshDetails() {
        scope.launch(Dispatchers.IO) {
            val details = runCatching { inspector.inspect() }
                .onFailure { BackendLog.w("runtime", "Runtime inspection failed", it) }
                .getOrNull() ?: return@launch
            workbench.update { it.copy(runtimeDetails = details) }
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
        installJob = scope.launch {
            try {
                // Android kills a backgrounded app mid-install otherwise.
                RuntimeWork.installStarted()
                RuntimeForegroundService.ensureRunning(appContext)
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
            } finally {
                RuntimeWork.installEnded()
            }
        }
        return true
    }

    fun refreshSystemAccess(): SystemAccess {
        val access = systemAccess.read()
        val storageChanged = workbench.current.systemAccess?.storage != access.storage
        workbench.update { it.copy(systemAccess = access) }
        if (storageChanged) onStorageAccessChanged()
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
