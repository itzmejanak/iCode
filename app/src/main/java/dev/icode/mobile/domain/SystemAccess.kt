package dev.icode.mobile.domain

/**
 * A permission iCode asks Android for. None is needed for a workspace: they live in iCode's own
 * storage (docs/workspace-storage-plan.md §4.8).
 */
enum class SystemPermission {
    /** Running terminal sessions are listed in the notification drawer. Onboarding offers it once. */
    Notifications,

    /** Battery optimisation leaves running sessions alone. Onboarding offers it once. */
    Battery,

    /**
     * All Files Access: phone storage, the file explorer's Shared place (docs/storage-explorer-plan.md).
     * Asked where it is needed, each time it is needed and missing; never in onboarding.
     */
    Storage,

    /**
     * Install unknown apps: Build & Run installs the app it built (docs/android-toolchain-plan.md
     * §4.5). Asked where it is needed, each time it is needed and missing; never in onboarding.
     */
    InstallApps,
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
    val storage: Boolean = false,
    val installApps: Boolean = false,
    /** Onboarding's permissions it has already offered; each is offered once. */
    val offered: Set<SystemPermission> = emptySet(),
) {
    fun granted(permission: SystemPermission): Boolean = when (permission) {
        SystemPermission.Notifications -> notifications
        SystemPermission.Battery -> unrestrictedBattery
        SystemPermission.Storage -> storage
        SystemPermission.InstallApps -> installApps
    }

    /**
     * What onboarding still has to ask, in order. Every permission is
     * optional, so a refusal is final: each is asked once, and Terminal
     * Settings is the way back.
     */
    val onboardingSteps: List<SystemPermission>
        get() = ONBOARDING.filter { permission -> !granted(permission) && permission !in offered }

    private companion object {
        /** Storage and InstallApps are not among them: each is asked where it is needed. */
        val ONBOARDING = listOf(SystemPermission.Notifications, SystemPermission.Battery)
    }
}
