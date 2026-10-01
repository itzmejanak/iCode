package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessRulesTest {
    @Test fun `an exit status is said in words, a signal by its name`() {
        assertEquals("succeeded", ProcessRules.exitMeaning(0))
        assertEquals("failed", ProcessRules.exitMeaning(1))
        assertEquals("found but not executable", ProcessRules.exitMeaning(126))
        assertEquals("command not found", ProcessRules.exitMeaning(127))
        assertEquals("ended by SIGTERM", ProcessRules.exitMeaning(143))
        assertEquals("ended by SIGKILL", ProcessRules.exitMeaning(137))
        assertEquals("ended by SIGHUP", ProcessRules.exitMeaning(129))
        assertEquals("ended by signal 31", ProcessRules.exitMeaning(159))
        assertEquals("failed", ProcessRules.exitMeaning(255))
    }

    @Test fun `ports are the addresses on this phone an output names, from 1024, each once, in order`() {
        val output = """
            INFO:     Uvicorn running on http://127.0.0.1:8000 (Press CTRL+C to quit)
              ➜  Local:   http://localhost:5173/
            Listening on 0.0.0.0:8000 and [::1]:3000
            Redis at 10.0.0.5:6379, an old port localhost:80, not.localhost:9000, 127.0.0.1:123456
        """.trimIndent()
        assertEquals(listOf(8000, 5173, 3000), ProcessRules.ports(output))
    }

    @Test fun `a process's state reads as the page says it`() {
        val start = 1_000_000L
        assertEquals("Running · 4m", ProcessRules.stateLabel(ManagedProcessState.Running, start + 4 * 60_000 + 5_000, start))
        assertEquals("Exited 0", ProcessRules.stateLabel(ManagedProcessState.Ended(0, start, stopped = false), start, start))
        assertEquals("Stopped", ProcessRules.stateLabel(ManagedProcessState.Ended(143, start, stopped = true), start, start))
        assertEquals("Ended by SIGKILL", ProcessRules.stateLabel(ManagedProcessState.Ended(137, start, stopped = false), start, start))
    }

    @Test fun `a span is said in two units at most`() {
        assertEquals("12s", RelativeTime.span(12_400))
        assertEquals("4m", RelativeTime.span(4 * 60_000 + 59_000))
        assertEquals("1h", RelativeTime.span(3_600_000))
        assertEquals("1h 5m", RelativeTime.span(3_900_000))
        assertEquals("2d 3h", RelativeTime.span((2 * 24 + 3) * 3_600_000L))
        assertEquals("0s", RelativeTime.span(-5))
    }
}
