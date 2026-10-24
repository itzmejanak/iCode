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
/** The steps of building the runtime once its base system is downloaded, in order. */
enum class RuntimeInstallStep { Preparing, Extracting, Configuring, Verifying }

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

        /** "12 MB / 480 MB", or what arrived when the length is unknown. */
        val transferred: String get() =
            if (totalBytes > 0) "${ByteSize.format(receivedBytes)} / ${ByteSize.format(totalBytes)}"
            else ByteSize.format(receivedBytes)
    }

    /**
     * Building the runtime from its base system: [step], said as [label], and how far it has got
     * where the step counts — [done] of the pinned archive's [total] entries while extracting
     * (docs/onboarding-polish-plan.md §3.2).
     */
    data class Installing(
        val label: String,
        val step: RuntimeInstallStep,
        val done: Long? = null,
        val total: Long? = null,
    ) : RuntimeState {
        /** 0..1 where the step counts; null where it does not. */
        val fraction: Float? get() = if (done != null && total != null && total > 0) (done.toFloat() / total).coerceAtMost(1f) else null

        /** "2048 / 3413" where the step counts. */
        val counts: String? get() = if (done != null && total != null) "$done / $total" else null
    }

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
