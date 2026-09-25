package dev.icode.mobile.domain

/**
 * Lifecycle of one terminal session, derived from the real process.
 *
 * A tab lives exactly as long as its shell: when the shell exits by itself the
 * session is removed, so only the two endings the user must see remain.
 */
enum class TerminalStatus {
    Running,

    /** The shell was killed by a signal; [TerminalSessionState.exitSignal] says which. */
    Exited,

    /** The session could not start; [TerminalSessionState.failure] says why. */
    Failed,
}

/**
 * One terminal session as the UI sees it.
 *
 * [status], [exitCode] and [exitSignal] come from `waitpid`, never from the
 * fact that a command was launched (architecture.md section 18: screens must
 * not infer process state from button clicks). The screen contents are not
 * here: they belong to the terminal emulator, which the view reads directly.
 */
data class TerminalSessionState(
    val id: String,
    val title: String,
    val status: TerminalStatus,
    val exitCode: Int? = null,
    val exitSignal: Int? = null,
    val failure: String? = null,
) {
    val isRunning: Boolean get() = status == TerminalStatus.Running

    /** What ended the session, for the UI. */
    val exitDescription: String?
        get() = when (status) {
            TerminalStatus.Running -> null
            TerminalStatus.Failed -> failure ?: "The terminal could not start"
            TerminalStatus.Exited -> when {
                exitSignal != null -> "Ended by ${PosixSignals.name(exitSignal)}"
                exitCode == 0 -> "Process exited normally"
                exitCode != null -> "Process exited with code $exitCode"
                else -> null
            }
        }
}

/** Names for the signals a user is likely to meet; others keep their number. */
object PosixSignals {
    private val names = mapOf(
        1 to "SIGHUP", 2 to "SIGINT", 3 to "SIGQUIT", 4 to "SIGILL", 6 to "SIGABRT",
        7 to "SIGBUS", 8 to "SIGFPE", 9 to "SIGKILL", 11 to "SIGSEGV", 13 to "SIGPIPE",
        14 to "SIGALRM", 15 to "SIGTERM",
    )

    fun name(signal: Int): String = names[signal] ?: "signal $signal"
}

/** Every terminal session plus which one is selected. */
data class TerminalSnapshot(
    val sessions: List<TerminalSessionState> = emptyList(),
    val selectedId: String? = null,
) {
    val selected: TerminalSessionState?
        get() = sessions.firstOrNull { it.id == selectedId } ?: sessions.firstOrNull()
}

/** Moves one tab to another tab's position without changing session identity. */
internal fun reorderTerminalIds(ids: List<String>, movingId: String, targetId: String): List<String> {
    val from = ids.indexOf(movingId)
    val to = ids.indexOf(targetId)
    if (from < 0 || to < 0 || from == to) return ids
    return ids.toMutableList().apply { add(to, removeAt(from)) }
}

/** Stable labels for open tabs; closed tabs free their number without reusing process IDs. */
internal class TerminalSessionNames {
    private val ordinalById = mutableMapOf<String, Int>()

    fun allocate(id: String): String {
        require(id !in ordinalById) { "Session already has a tab label: $id" }
        val used = ordinalById.values.toSet()
        var ordinal = 1
        while (ordinal in used) ordinal++
        ordinalById[id] = ordinal
        return if (ordinal == 1) "bash" else "bash $ordinal"
    }

    fun release(id: String) {
        ordinalById.remove(id)
    }
}
