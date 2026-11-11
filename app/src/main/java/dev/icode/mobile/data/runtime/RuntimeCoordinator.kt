package dev.icode.mobile.data.runtime

import android.content.Context
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.system.AndroidSystemAccess
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.notices.NoticeRules
import dev.icode.mobile.domain.notices.RuntimeNotices
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.RuntimeInstallStep
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
    private val stateHolder: ICodeStateHolder,
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

    /** At iCode's start: its own files in an installed runtime brought to this build ([RuntimeLayout.writeOwnFiles]), then the state. */
    fun start() {
        if (layout.isInstalled) layout.writeOwnFiles()
        reportState()
    }

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
        stateHolder.update {
            it.copy(
                runtime = state,
                runtimeHealthy = state is RuntimeState.Ready,
                runtimeInstallReason = if (preservedHome && state !is RuntimeState.Ready)
                    RuntimeInstallReason.Repair else RuntimeInstallReason.Initial,
                systemAccess = systemAccess.read(),
                runtimeTools = it.runtimeTools.copy(offerPending = state is RuntimeState.Ready && layout.toolsOffer.exists()),
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
                stateHolder.update { it.copy(runtimeDetails = facts.copy(rootfsSize = it.runtimeDetails.rootfsSize)) }
                val size = inspector.rootfsSize()
                stateHolder.update { it.copy(runtimeDetails = it.runtimeDetails.copy(rootfsSize = size)) }
            }.onFailure { BackendLog.w("runtime", "Runtime inspection failed", it) }
        }
    }

    /** One state-driven installer for first install, repair and reset. */
    suspend fun install(reason: RuntimeInstallReason): Boolean {
        if (installing || stateHolder.current.runtime is RuntimeState.Downloading ||
            stateHolder.current.runtime is RuntimeState.Installing) {
            stateHolder.tell(RuntimeNotices.busy())
            return false
        }
        if (reason != RuntimeInstallReason.Initial && sessionsRunning()) {
            stateHolder.tell(RuntimeNotices.closeFirst())
            return false
        }
        val label = when (reason) {
            RuntimeInstallReason.Initial -> "Preparing runtime installation"
            RuntimeInstallReason.Repair -> "Preparing runtime repair"
            RuntimeInstallReason.Reset -> "Preparing runtime reset"
        }
        stateHolder.update { it.copy(
            runtime = RuntimeState.Installing(label, RuntimeInstallStep.Preparing),
            runtimeHealthy = false,
            runtimeInstallReason = reason,
        ) }
        // The Installing state above is background work: the foreground service keeps the
        // process while it lasts (docs/long-operations-plan.md §6).
        val began = System.currentTimeMillis()
        installJob = scope.launch {
            try {
                RuntimeInstaller(appContext, layout).install(reason).collect { state ->
                    // Ready and its Set Up Tools offer arrive together: the screen that routes on
                    // Ready must see both.
                    val built = state is RuntimeState.Ready
                    stateHolder.update {
                        it.copy(
                            runtime = state,
                            runtimeHealthy = built,
                            runtimeTools = it.runtimeTools.copy(offerPending = built && layout.toolsOffer.exists()),
                        )
                    }
                    if (state is RuntimeState.Failed) stateHolder.tell(RuntimeNotices.notInstalled(reason, state.reason))
                    if (state is RuntimeState.Ready) {
                        refreshDetails()
                        stateHolder.tell(RuntimeNotices.installed(reason, away = System.currentTimeMillis() - began >= NoticeRules.LONG_WORK_MILLIS))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = stateHolder.failureText("runtime", INSTALL_FAILED, error)
                stateHolder.update { it.copy(runtime = RuntimeState.Failed(message), runtimeHealthy = false) }
                stateHolder.tell(RuntimeNotices.notInstalled(reason, message))
            }
        }
        return true
    }

    fun refreshSystemAccess(): SystemAccess {
        val access = systemAccess.read()
        stateHolder.update { it.copy(systemAccess = access) }
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

    private companion object {
        const val INSTALL_FAILED = "Runtime installation failed"
    }
}
