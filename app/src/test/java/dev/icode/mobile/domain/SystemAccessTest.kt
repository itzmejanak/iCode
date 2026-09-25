package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemAccessTest {

    private val nothing = SystemAccess(storage = false, notifications = false, unrestrictedBattery = false)

    @Test
    fun `a fresh install is asked for everything, storage first`() {
        assertEquals(
            listOf(SystemPermission.Storage, SystemPermission.Notifications, SystemPermission.Battery),
            nothing.onboardingSteps,
        )
    }

    @Test
    fun `what is already allowed is not asked for`() {
        assertEquals(
            listOf(SystemPermission.Battery),
            nothing.copy(storage = true, notifications = true).onboardingSteps,
        )
    }

    @Test
    fun `an optional permission is offered once`() {
        val offered = nothing.copy(offered = setOf(SystemPermission.Notifications, SystemPermission.Battery))
        assertEquals(listOf(SystemPermission.Storage), offered.onboardingSteps)
    }

    @Test
    fun `storage is asked for while missing, whatever was offered before`() {
        val offered = nothing.copy(notifications = true, unrestrictedBattery = true, offered = SystemPermission.entries.toSet())
        assertEquals(listOf(SystemPermission.Storage), offered.onboardingSteps)
    }

    @Test
    fun `granted answers each permission`() {
        val access = SystemAccess(storage = true, notifications = false, unrestrictedBattery = true)
        assertEquals(
            listOf(true, false, true),
            SystemPermission.entries.map(access::granted),
        )
    }
}
