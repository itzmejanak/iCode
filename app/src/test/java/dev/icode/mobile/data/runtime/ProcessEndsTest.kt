package dev.icode.mobile.data.runtime

import android.app.ApplicationExitInfo
import dev.icode.mobile.domain.ProcessEndCause
import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessEndsTest {

    private val android14 = 34
    private val android13 = 33

    @Test
    fun `a phone restart wins over any record`() {
        assertEquals(ProcessEndCause.PhoneRestarted, ProcessEnds.cause(true, ApplicationExitInfo.REASON_CRASH, android14))
        assertEquals(ProcessEndCause.PhoneRestarted, ProcessEnds.cause(true, null, android14))
    }

    @Test
    fun `no record is reported as such, not guessed`() {
        assertEquals(ProcessEndCause.Unrecorded, ProcessEnds.cause(false, null, android14))
    }

    @Test
    fun `a stop by the user is told apart from an update from Android 14 on`() {
        // Measured on the Android 14 test phone: force stop and Recents -> 10, reinstall -> 16.
        assertEquals(ProcessEndCause.StoppedByUser, ProcessEnds.cause(false, ApplicationExitInfo.REASON_USER_REQUESTED, android14))
        assertEquals(ProcessEndCause.Updated, ProcessEnds.cause(false, ApplicationExitInfo.REASON_PACKAGE_UPDATED, android14))
    }

    @Test
    fun `before Android 14 the user-requested reason also meant an update`() {
        assertEquals(
            ProcessEndCause.StoppedByUserOrUpdated,
            ProcessEnds.cause(false, ApplicationExitInfo.REASON_USER_REQUESTED, android13),
        )
    }

    @Test
    fun `the system's own reasons`() {
        assertEquals(ProcessEndCause.LowMemory, ProcessEnds.cause(false, ApplicationExitInfo.REASON_LOW_MEMORY, android14))
        assertEquals(ProcessEndCause.Crashed, ProcessEnds.cause(false, ApplicationExitInfo.REASON_CRASH, android14))
        assertEquals(ProcessEndCause.Crashed, ProcessEnds.cause(false, ApplicationExitInfo.REASON_CRASH_NATIVE, android14))
        assertEquals(ProcessEndCause.NotResponding, ProcessEnds.cause(false, ApplicationExitInfo.REASON_ANR, android14))
        assertEquals(ProcessEndCause.PermissionChanged, ProcessEnds.cause(false, ApplicationExitInfo.REASON_PERMISSION_CHANGE, android14))
        // Measured: SIGKILL from the app's own uid -> 2 (SIGNALED), status 9.
        assertEquals(ProcessEndCause.Killed, ProcessEnds.cause(false, ApplicationExitInfo.REASON_SIGNALED, android14))
        assertEquals(ProcessEndCause.Other, ProcessEnds.cause(false, ApplicationExitInfo.REASON_OTHER, android14))
    }
}
