package dev.icode.mobile.domain

/** Why Android ended iCode's previous process, as far as Android recorded it. */
enum class ProcessEndCause {
    /** Force stop, Task Manager or Recents (Android 14 and later). */
    StoppedByUser,

    /** Before Android 14 one reason covered both a stop by the user and an update. */
    StoppedByUserOrUpdated,
    Updated,
    PhoneRestarted,
    LowMemory,
    Crashed,
    NotResponding,
    PermissionChanged,

    /** A signal ended it; who sent it is not recorded. */
    Killed,

    /** Android recorded another reason. */
    Other,

    /** Android kept no record of this process's end. */
    Unrecorded,
}

/** A terminal tab that was running when its process ended. */
data class LostTerminalSession(val title: String, val workspace: String?)

/**
 * Terminal sessions that ended with iCode's previous process.
 *
 * A killed process cannot be brought back; this is what the user is told
 * instead of an empty terminal that pretends nothing happened.
 */
data class LostTerminalSessions(
    val sessions: List<LostTerminalSession>,
    val cause: ProcessEndCause,
    /** When the process ended (epoch millis), if Android recorded it. */
    val endedAt: Long? = null,
)
