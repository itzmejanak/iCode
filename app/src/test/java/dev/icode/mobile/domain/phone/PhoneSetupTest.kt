package dev.icode.mobile.domain.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSetupTest {

    @Test
    fun `Android 13 and later ask for restricted settings first`() {
        val lines = PhoneSetup.lines(PhoneAccess(), sdk = 33)
        assertEquals(listOf(PhoneSetupStep.Restricted, PhoneSetupStep.Service), lines.map { it.step })
        assertTrue(lines[0].current)
        assertFalse(lines[1].current)
    }

    @Test
    fun `Android 11 and 12 go straight to the service`() {
        val lines = PhoneSetup.lines(PhoneAccess(), sdk = 31)
        assertEquals(listOf(PhoneSetupStep.Service), lines.map { it.step })
        assertTrue(lines.single().current)
    }

    @Test
    fun `with the service on every step is done`() {
        val lines = PhoneSetup.lines(PhoneAccess(service = true), sdk = 34)
        assertTrue(lines.all { it.done && !it.current })
    }

    @Test
    fun `Shizuku ready makes turning on one tap, only while off`() {
        assertTrue(PhoneSetup.oneTap(PhoneAccess(shizuku = ShizukuState.Ready)))
        assertFalse(PhoneSetup.oneTap(PhoneAccess(service = true, shizuku = ShizukuState.Ready)))
        assertFalse(PhoneSetup.oneTap(PhoneAccess(shizuku = ShizukuState.NotAllowed)))
    }

    @Test
    fun `status names the bubble`() {
        assertEquals("Off", PhoneSetup.status(PhoneAccess()))
        assertEquals("On · bubble shown", PhoneSetup.status(PhoneAccess(service = true, bubble = true)))
        assertEquals("On · bubble hidden", PhoneSetup.status(PhoneAccess(service = true, bubble = false)))
    }
}
