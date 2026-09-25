package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * The workbench's one snapshot and one effect stream.
 *
 * The gateway and every part split out of it read and change state here, take
 * the same lock for a state transition, and report a failure the same way:
 * one log line for whoever diagnoses it, one message in the user's words.
 */
internal class WorkbenchStateHolder {
    private val mutableSnapshot = MutableStateFlow(emptySnapshot())
    private val mutableEffects = MutableSharedFlow<WorkbenchEffect>(extraBufferCapacity = 1)

    val snapshot: StateFlow<WorkbenchSnapshot> = mutableSnapshot.asStateFlow()
    val effects: SharedFlow<WorkbenchEffect> = mutableEffects.asSharedFlow()

    val current: WorkbenchSnapshot get() = mutableSnapshot.value

    /**
     * Held for one state transition at a time. Keystrokes and read-only
     * refreshes are routed around it, so they never queue behind slow work.
     */
    val mutations = Mutex()

    fun update(change: (WorkbenchSnapshot) -> WorkbenchSnapshot) = mutableSnapshot.update(change)

    suspend fun emit(effect: WorkbenchEffect) = mutableEffects.emit(effect)

    suspend fun message(text: String) = emit(WorkbenchEffect.Message(text))

    /** A failure the user is told about in a message: logged once, said once. */
    suspend fun report(subsystem: String, fallback: String, error: Throwable) =
        message(failureText(subsystem, fallback, error))

    /**
     * For a failure the user sees inside state instead (a failed runtime, a
     * failed Git operation, a form's error): logs it and returns what to show —
     * the error's own words when it has some, else [fallback]; never a token.
     */
    fun failureText(subsystem: String, fallback: String, error: Throwable): String {
        BackendLog.w(subsystem, fallback, error)
        val text = error.message?.takeIf { it.isNotBlank() } ?: fallback
        return BackendLog.credentialSafe(text)
    }

    /** Terminal sessions and other Android objects are confined to the main thread. */
    suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main.immediate) { block() }

    companion object {
        fun emptySnapshot() = WorkbenchSnapshot(
            workspaces = emptyList(),
            files = emptyList(),
            gitChanges = emptyList(),
            diagnostics = emptyList(),
            languageServices = emptyList(),
            activeWorkspace = "No workspace",
            activePath = "Open a folder to begin",
            activeBranch = "",
            runtimeHealthy = true,
        )
    }
}
