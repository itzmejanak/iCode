package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.notices.Notice
import java.util.UUID
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
 * The iCode app's one snapshot and one effect stream.
 *
 * The gateway and every part split out of it read and change state here, take
 * the same lock for a state transition, and report a failure the same way:
 * one log line for whoever diagnoses it, one notice in the user's words.
 */
internal class ICodeStateHolder {
    private val mutableSnapshot = MutableStateFlow(emptySnapshot())
    private val mutableEffects = MutableSharedFlow<ICodeEffect>(extraBufferCapacity = 1)

    val snapshot: StateFlow<ICodeSnapshot> = mutableSnapshot.asStateFlow()
    val effects: SharedFlow<ICodeEffect> = mutableEffects.asSharedFlow()

    val current: ICodeSnapshot get() = mutableSnapshot.value

    /**
     * Held for one state transition at a time. Keystrokes and read-only
     * refreshes are routed around it, so they never queue behind slow work.
     */
    val mutations = Mutex()

    fun update(change: (ICodeSnapshot) -> ICodeSnapshot) = mutableSnapshot.update(change)

    suspend fun emit(effect: ICodeEffect) = mutableEffects.emit(effect)

    /**
     * Tells the user [notice] (docs/notices-redesign-plan.md D4): on the bell, the Notifications page
     * and Android's shade as its kind says. It is given an id and the time, unless it brings its own.
     */
    suspend fun tell(notice: Notice) = emit(
        ICodeEffect.Told(notice.copy(id = notice.id.ifEmpty { UUID.randomUUID().toString() }, at = notice.at.takeIf { it > 0 } ?: System.currentTimeMillis())),
    )

    /** Tells [notice], a failure: [error] logged once under [subsystem], its words — never a token — the notice's output. */
    suspend fun tell(notice: Notice, subsystem: String, error: Throwable) =
        tell(notice.copy(output = failureText(subsystem, notice.title, error).takeIf { it != notice.title }))

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
        fun emptySnapshot() = ICodeSnapshot(
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
