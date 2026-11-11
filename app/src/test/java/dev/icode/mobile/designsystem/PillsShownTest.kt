package dev.icode.mobile.designsystem

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pill's time awake counts only while the floating stack is shown ([shownFor]): the bell's and the
 * undo pill's time, a pill's time to rest, the dock's time to close. Real time, so only lower bounds
 * and generous upper ones are asserted.
 */
class PillsShownTest {
    @Test fun `shown throughout, the time is spent once`() = runBlocking {
        val hidden = MutableStateFlow(false)
        val start = System.nanoTime()
        withTimeout(LONG) { hidden.shownFor(TIME) }
        assertTrue(elapsed(start) >= TIME)
    }

    @Test fun `hidden, the time does not run, and starts once the pills are back`() = runBlocking {
        val hidden = MutableStateFlow(true)
        assertNull(withTimeoutOrNull(TIME * 3) { hidden.shownFor(1) })
        hidden.value = false
        assertNotNull(withTimeoutOrNull(LONG) { hidden.shownFor(1) })
    }

    @Test fun `hidden part way, the time starts again in full once the pills are back`() = runBlocking {
        val hidden = MutableStateFlow(false)
        val start = System.nanoTime()
        val waited = async { hidden.shownFor(TIME * 2) }
        delay(TIME)
        hidden.value = true
        delay(TIME)
        hidden.value = false
        withTimeout(LONG) { waited.await() }
        // TIME shown, TIME hidden, then the whole TIME * 2 again: never the TIME * 2 a plain wait takes.
        assertTrue(elapsed(start) >= TIME * 4)
    }

    private fun elapsed(start: Long) = (System.nanoTime() - start) / 1_000_000

    private companion object {
        const val TIME = 100L
        const val LONG = 10_000L
    }
}
