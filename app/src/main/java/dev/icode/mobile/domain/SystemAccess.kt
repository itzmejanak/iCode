package dev.icode.mobile.domain

/** A permission iCode asks Android for, in the order onboarding asks. */
enum class SystemPermission {
    /** All files access: workspaces are ordinary folders on shared storage. */
    Storage,

    /** Running terminal sessions are listed in the notification drawer. */
    Notifications,

    /** Battery optimisation leaves running sessions alone. */
    Battery,
}

/**
 * What Android allows iCode right now.
 *
 * Read from the system each time it may have changed, never stored: the user
 * can change any of it in Android's settings while iCode is away.
 */
data class SystemAccess(
    val storage: Boolean,
    val notifications: Boolean,
    val unrestrictedBattery: Boolean,
    /** Optional permissions onboarding has already offered; each is offered once. */
    val offered: Set<SystemPermission> = emptySet(),
) {
    fun granted(permission: SystemPermission): Boolean = when (permission) {
        SystemPermission.Storage -> storage
        SystemPermission.Notifications -> notifications
        SystemPermission.Battery -> unrestrictedBattery
    }

    /**
     * What onboarding still has to ask, in order.
     *
     * Storage is asked for while it is missing — nothing that opens a folder
     * works without it. The others are optional, so a refusal is final: they
     * are asked once, and Terminal Settings is the way back.
     */
    val onboardingSteps: List<SystemPermission>
        get() = SystemPermission.entries.filter { permission ->
            !granted(permission) && (permission == SystemPermission.Storage || permission !in offered)
        }
}
