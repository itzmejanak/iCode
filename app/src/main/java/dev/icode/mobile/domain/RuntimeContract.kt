package dev.icode.mobile.domain

/** Why installation is running; retry and return navigation must keep this distinction. */
enum class RuntimeInstallReason { Initial, Repair, Reset }

/**
 * Observable state of the Linux runtime.
 *
 * Features wait on this rather than bootstrapping the runtime themselves
 * (architecture.md section 14.2). Every state is derived from a verified fact:
 * [Ready] means a health check passed, not that installation was attempted.
 */
sealed interface RuntimeState {
    /** Nothing has been checked yet. */
    data object Unknown : RuntimeState

    /** No root filesystem is installed. [reason] explains what is missing. */
    data class NotInstalled(val reason: String) : RuntimeState

    data class Downloading(
        val receivedBytes: Long,
        val totalBytes: Long,
        val label: String,
    ) : RuntimeState {
        /** Null when the server did not report a length. */
        val fraction: Float? = if (totalBytes > 0) (receivedBytes.toFloat() / totalBytes) else null
    }

    data class Installing(val label: String) : RuntimeState

    /** A health check has run and the runtime answered. */
    data class Ready(val release: String) : RuntimeState

    data class Failed(val reason: String, val recoverable: Boolean = true) : RuntimeState

    /**
     * Whether onboarding still has runtime work to show the user.
     *
     * A failure is included: the user is told it failed and offered a retry,
     * rather than being dropped at a launcher that quietly cannot run anything.
     * [Unknown] is excluded because nothing has been checked yet.
     */
    val needsInstallStep: Boolean
        get() = when (this) {
            is NotInstalled, is Downloading, is Installing, is Failed -> true
            is Ready, Unknown -> false
        }
}
