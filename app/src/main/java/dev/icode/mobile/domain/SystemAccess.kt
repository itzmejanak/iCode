package dev.icode.mobile.domain

/**
 * A permission iCode asks Android for, in the order onboarding asks. Both are optional: workspaces
 * live in iCode's own storage and need none (docs/workspace-storage-plan.md §4.8).
 */
enum class SystemPermission {
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
    val notifications: Boolean,
    val unrestrictedBattery: Boolean,
    /** Optional permissions onboarding has already offered; each is offered once. */
    val offered: Set<SystemPermission> = emptySet(),
) {
    fun granted(permission: SystemPermission): Boolean = when (permission) {
        SystemPermission.Notifications -> notifications
        SystemPermission.Battery -> unrestrictedBattery
    }

    /**
     * What onboarding still has to ask, in order. Every permission is
     * optional, so a refusal is final: each is asked once, and Terminal
     * Settings is the way back.
     */
    val onboardingSteps: List<SystemPermission>
        get() = SystemPermission.entries.filter { permission -> !granted(permission) && permission !in offered }
}
