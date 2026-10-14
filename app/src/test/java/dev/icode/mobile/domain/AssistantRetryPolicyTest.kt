package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRetryPolicyTest {
    @Test fun `every kind of retry says what failed and when it tries again`() {
        assertEquals("Rate limited — retrying in 30s (1/5)…", AssistantRetryPolicy.stepText(AssistantRetryKind.RateLimited, 1, 30_000))
        assertEquals("Connection lost — retrying in 30s (2/5)…", AssistantRetryPolicy.stepText(AssistantRetryKind.Connection, 2, 30_000))
        assertEquals("Server error — retrying in 7s (1/5)…", AssistantRetryPolicy.stepText(AssistantRetryKind.Server, 1, 7_000))
        assertEquals("Server overloaded — retrying in 6s (3/10)…", AssistantRetryPolicy.stepText(AssistantRetryKind.Overloaded, 3, 6_000))
    }

    @Test fun `a server error waits as an overloaded one does, and retries as often as the rest`() {
        val delay = AssistantRetryPolicy.delayMillis(AssistantRetryKind.Server, 1, null)
        assertTrue("$delay", delay in 5_000L..10_000L)
        assertEquals(5, AssistantRetryPolicy.maxRetries(AssistantRetryKind.Server))
    }
}
