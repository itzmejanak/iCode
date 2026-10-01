package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_000_000_000_000L
    private val minute = 60_000L

    @Test fun `under a minute is just now, and so is a clock that went backwards`() {
        assertEquals("just now", RelativeTime.since(now - 59_999, now))
        assertEquals("just now", RelativeTime.since(now + 5 * minute, now))
    }

    @Test fun `the largest whole unit`() {
        assertEquals("1m ago", RelativeTime.since(now - minute, now))
        assertEquals("59m ago", RelativeTime.since(now - 59 * minute, now))
        assertEquals("1h ago", RelativeTime.since(now - 60 * minute, now))
        assertEquals("23h ago", RelativeTime.since(now - 23 * 60 * minute, now))
        assertEquals("2d ago", RelativeTime.since(now - 48 * 60 * minute, now))
        val day = 24 * 60 * minute
        assertEquals("6d ago", RelativeTime.since(now - 6 * day, now))
        assertEquals("1w ago", RelativeTime.since(now - 7 * day, now))
        assertEquals("4w ago", RelativeTime.since(now - 29 * day, now))
        assertEquals("2mo ago", RelativeTime.since(now - 65 * day, now))
        assertEquals("12mo ago", RelativeTime.since(now - 364 * day, now))
        assertEquals("1y ago", RelativeTime.since(now - 365 * day, now))
    }
}
