package dev.icode.mobile.navigation

import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.SystemAccess
import dev.icode.mobile.domain.SystemPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingRouteTest {

    private val ready = RuntimeState.Ready("Ubuntu 24.04.5 LTS")
    private val everything = SystemAccess(notifications = true, unrestrictedBattery = true)
    private val noNotifications = everything.copy(notifications = false)

    @Test
    fun `nothing moves until what Android allows has been read`() {
        assertNull(OnboardingRoute.next(ready, access = null, componentsPending = false))
        assertNull(OnboardingRoute.next(RuntimeState.NotInstalled("missing"), access = null, componentsPending = false))
    }

    @Test
    fun `a new install goes to the runtime step before anything else`() {
        assertEquals(Route.RuntimeInstalling, OnboardingRoute.next(RuntimeState.NotInstalled("missing"), everything, componentsPending = false))
    }

    @Test
    fun `the runtime step comes first even when permission is still outstanding`() {
        assertEquals(Route.RuntimeInstalling, OnboardingRoute.next(RuntimeState.NotInstalled("missing"), noNotifications, componentsPending = false))
    }

    @Test
    fun `an install already under way is not restarted from the splash`() {
        val downloading = RuntimeState.Downloading(receivedBytes = 10, totalBytes = 100, label = "rootfs")
        assertEquals(Route.RuntimeInstalling, OnboardingRoute.next(downloading, everything, componentsPending = false))
        assertEquals(Route.RuntimeInstalling, OnboardingRoute.next(RuntimeState.Installing("extracting"), everything, componentsPending = false))
    }

    @Test
    fun `a failed runtime is shown, not skipped past to the launcher`() {
        assertEquals(Route.RuntimeInstalling, OnboardingRoute.next(RuntimeState.Failed("no network"), everything, componentsPending = false))
    }

    @Test
    fun `a ready runtime with everything allowed opens the launcher`() {
        assertEquals(Route.LauncherHome, OnboardingRoute.next(ready, everything, componentsPending = false))
    }

    @Test
    fun `an optional permission not yet offered is asked for`() {
        assertEquals(Route.PermissionExplanation, OnboardingRoute.next(ready, everything.copy(notifications = false), componentsPending = false))
        assertEquals(Route.PermissionExplanation, OnboardingRoute.next(ready, everything.copy(unrestrictedBattery = false), componentsPending = false))
    }

    @Test
    fun `an optional permission already offered does not hold up the launcher`() {
        val refused = SystemAccess(
            notifications = false,
            unrestrictedBattery = false,
            offered = setOf(SystemPermission.Notifications, SystemPermission.Battery),
        )
        assertEquals(Route.LauncherHome, OnboardingRoute.next(ready, refused, componentsPending = false))
    }

    @Test
    fun `an unchecked runtime is not an install step`() {
        assertEquals(Route.LauncherHome, OnboardingRoute.next(RuntimeState.Unknown, everything, componentsPending = false))
    }

    @Test
    fun `after the runtime finishes it never routes back into itself`() {
        assertEquals(Route.LauncherHome, OnboardingRoute.afterRuntime(everything, componentsPending = false))
        assertEquals(Route.PermissionExplanation, OnboardingRoute.afterRuntime(noNotifications, componentsPending = false))
        assertNull(OnboardingRoute.afterRuntime(null, componentsPending = false))
    }

    @Test
    fun `a runtime just built offers its components first, before any permission`() {
        assertEquals(Route.RuntimeComponents, OnboardingRoute.next(ready, noNotifications, componentsPending = true))
        assertEquals(Route.RuntimeComponents, OnboardingRoute.afterRuntime(everything, componentsPending = true))
        assertEquals(Route.RuntimeInstalling, OnboardingRoute.next(RuntimeState.NotInstalled("missing"), everything, componentsPending = true))
    }

    @Test
    fun `once the components are answered, what Android allows decides`() {
        assertEquals(Route.PermissionExplanation, OnboardingRoute.afterComponents(noNotifications))
        assertEquals(Route.LauncherHome, OnboardingRoute.afterComponents(everything))
        assertNull(OnboardingRoute.afterComponents(null))
    }
}
