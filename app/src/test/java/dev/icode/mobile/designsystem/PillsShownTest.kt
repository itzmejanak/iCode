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
 * A told wake counts only while the floating stack is shown ([shownFor]): the bell's and the undo
 * pill's time, the dock's time to close. Real time, so only lower bounds and generous upper ones are
 * asserted.
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

    @Test fun `hidden part way, the rest of the time runs once the pills are back`() = runBlocking {
        val hidden = MutableStateFlow(false)
        val start = System.nanoTime()
        val waited = async { hidden.shownFor(TIME * 4) }
        delay(TIME * 3)
        hidden.value = true
        delay(TIME)
        hidden.value = false
        // TIME * 3 shown, TIME hidden, then the TIME left: never the TIME * 4 a plain wait takes.
        val back = System.nanoTime()
        withTimeout(LONG) { waited.await() }
        assertTrue(elapsed(start) >= TIME * 5)
        // Not the whole TIME * 4 again once the pills are back.
        assertTrue(elapsed(back) < TIME * 3)
    }

    @Test fun `hidden again and again, the time still ends once it has all been shown`() = runBlocking {
        val hidden = MutableStateFlow(false)
        val waited = async { hidden.shownFor(TIME * 3) }
        repeat(4) {
            delay(TIME / 2)
            hidden.value = true
            delay(TIME / 2)
            hidden.value = false
        }
        // TIME * 2 shown so far; restarting in full would leave TIME * 3 to go, resuming only TIME.
        val back = System.nanoTime()
        withTimeout(LONG) { waited.await() }
        assertTrue(elapsed(back) < TIME * 5 / 2)
    }

    private fun elapsed(start: Long) = (System.nanoTime() - start) / 1_000_000

    private companion object {
        const val TIME = 100L
        const val LONG = 10_000L
    }
}
