package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemAccessTest {

    private val nothing = SystemAccess(notifications = false, unrestrictedBattery = false)

    @Test
    fun `a fresh install is asked for each permission, notifications first`() {
        assertEquals(listOf(SystemPermission.Notifications, SystemPermission.Battery), nothing.onboardingSteps)
    }

    @Test
    fun `what is already allowed is not asked for`() {
        assertEquals(listOf(SystemPermission.Battery), nothing.copy(notifications = true).onboardingSteps)
    }

    @Test
    fun `each permission is offered once`() {
        val offered = nothing.copy(offered = setOf(SystemPermission.Notifications))
        assertEquals(listOf(SystemPermission.Battery), offered.onboardingSteps)
        assertEquals(emptyList<SystemPermission>(), offered.copy(offered = SystemPermission.entries.toSet()).onboardingSteps)
    }

    @Test
    fun `granted answers each permission`() {
        val access = SystemAccess(notifications = false, unrestrictedBattery = true, storage = true, installApps = false)
        assertEquals(listOf(false, true, true, false), SystemPermission.entries.map(access::granted))
    }

    @Test
    fun `phone storage and installing apps are never onboarding steps, each is asked where it is needed`() {
        assertEquals(listOf(SystemPermission.Notifications, SystemPermission.Battery), nothing.copy(storage = false, installApps = false).onboardingSteps)
        assertEquals(emptyList<SystemPermission>(), nothing.copy(notifications = true, unrestrictedBattery = true).onboardingSteps)
    }
}
