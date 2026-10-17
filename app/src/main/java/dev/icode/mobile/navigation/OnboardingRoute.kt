package dev.icode.mobile.navigation

import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.SystemAccess

/**
 * The single resolver for where a launch leads.
 *
 * Two screens act on this decision — the splash, and the runtime installer
 * once it finishes — and a second copy of the rule in either of them is how
 * onboarding steps get skipped or repeated. It is pure so it can be tested
 * without Android.
 */
object OnboardingRoute {

    /**
     * The next onboarding destination, or null while the answer is still
     * unknown and nothing should move.
     *
     * The runtime comes first: a terminal, Git and builds all depend on it,
     * and it is the only step that takes real time. What Android allows
     * decides the rest, and until it has been read there is nothing honest to
     * show.
     */
    fun next(runtime: RuntimeState, access: SystemAccess?, toolsPending: Boolean): Route? = when {
        access == null -> null
        runtime.needsInstallStep -> Route.RuntimeInstalling
        else -> afterRuntime(access, toolsPending)
    }

    /**
     * Where to go once the runtime is settled.
     *
     * Skips the runtime step, which is the one just completed — otherwise a
     * finished install would route straight back into itself.
     */
    fun afterRuntime(access: SystemAccess?, toolsPending: Boolean): Route? = when {
        access == null -> null
        // A runtime just built has no tools yet: Set Up Tools comes first, once.
        toolsPending -> Route.RuntimeTools
        else -> afterTools(access)
    }

    /** Where to go once Set Up Tools is answered: what Android allows decides. */
    fun afterTools(access: SystemAccess?): Route? = when {
        access == null -> null
        access.onboardingSteps.isEmpty() -> Route.LauncherHome
        else -> Route.PermissionExplanation
    }
}
