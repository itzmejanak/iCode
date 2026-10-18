package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunRulesTest {
    private val app = RunConfiguration.AndroidApp(AndroidProject("/w/hello", "app"))
    private val dev = RunConfiguration.Command("c1", "dev", "npm run dev")
    private fun process(state: ManagedProcessState, ports: List<Int> = emptyList()) =
        ManagedProcess("p1", "/w/hello", "dev", "npm run dev", "/workspace/hello", startedAt = 0, state = state, ports = ports)
    private fun record(androidStep: AndroidAppStep? = null, failure: String? = null) = RunRecord("/w/hello", "x", "p1", androidStep, failure)

    @Test fun `an Android app is its module's assembleDebug, named by its folder or else the workspace's`() {
        assertEquals("android:app", app.id)
        assertEquals("app", app.name)
        assertEquals("./gradlew :app:assembleDebug", app.command)
        assertEquals("Android app", app.kind)
        assertEquals("hello", RunConfiguration.AndroidApp(AndroidProject("/w/hello", "")).name)
        assertEquals("./gradlew assembleDebug", RunConfiguration.AndroidApp(AndroidProject("/w/hello", "")).command)
        assertEquals("Command", dev.kind)
    }

    @Test fun `apps come first, app before the rest by name, then the saved commands as saved`() {
        val apps = listOf(AndroidProject("/w", "wear"), AndroidProject("/w", "app"), AndroidProject("/w", "demo"))
        val saved = listOf(RunConfiguration.Command("b", "test", "npm test"), RunConfiguration.Command("a", "dev", "npm run dev"))
        assertEquals(listOf("android:app", "android:demo", "android:wear", "b", "a"), RunRules.configurations(apps, saved).map { it.id })
    }

    @Test fun `a row says what its last run did`() {
        val minute = 60_000L
        assertEquals(RunStatus.NeverRun, RunRules.status(dev, null, null, minute))
        assertEquals(RunStatus.Running("Running · 1m", listOf(5173)), RunRules.status(dev, record(), process(ManagedProcessState.Running, listOf(5173)), minute))
        assertEquals(RunStatus.Running("Building · 1m", emptyList()), RunRules.status(app, record(), process(ManagedProcessState.Running), minute))
        assertEquals(RunStatus.Ended("Exited 0", succeeded = true), RunRules.status(dev, record(), process(ManagedProcessState.Ended(0, minute, stopped = false)), minute))
        assertEquals(RunStatus.Ended("Exited 1", succeeded = false), RunRules.status(dev, record(), process(ManagedProcessState.Ended(1, minute, stopped = false)), minute))
        assertEquals(RunStatus.Ended("Stopped", succeeded = null), RunRules.status(dev, record(), process(ManagedProcessState.Ended(143, minute, stopped = true)), minute))
        assertEquals(RunStatus.Ended("Ended", succeeded = null), RunRules.status(dev, record(), null, minute))
    }

    @Test fun `an Android app's build is followed by its install, and why it stopped short says so first`() {
        val built = process(ManagedProcessState.Ended(0, 1, stopped = false))
        assertEquals(RunStatus.Installing, RunRules.status(app, record(AndroidAppStep.Installing), built, 1))
        assertEquals(RunStatus.Opened, RunRules.status(app, record(AndroidAppStep.Opened), built, 1))
        assertEquals(RunStatus.Failed("The install was cancelled"), RunRules.status(app, record(AndroidAppStep.Installing, "The install was cancelled"), built, 1))
        assertEquals(RunStatus.Failed("bash: npx: not found"), RunRules.status(dev, RunRecord("/w/hello", "c1", null, failure = "bash: npx: not found"), null, 1))
    }

    @Test fun `stop shows while it runs or waits on Android's install`() {
        assertTrue(RunRules.stoppable(RunStatus.Running("Running · 1s", emptyList())))
        assertTrue(RunRules.stoppable(RunStatus.Installing))
        assertFalse(RunRules.stoppable(RunStatus.Opened))
        assertFalse(RunRules.stoppable(RunStatus.NeverRun))
        assertFalse(RunRules.stoppable(RunStatus.Ended("Exited 0", true)))
        assertFalse(RunRules.stoppable(RunStatus.Failed("x")))
    }

    @Test fun `a record is found by its workspace and configuration`() {
        val state = RunState(records = listOf(RunRecord("/w/a", "c1", "p1"), RunRecord("/w/b", "c1", "p2")))
        assertEquals("p2", state.recordOf("/w/b", "c1")?.process)
        assertEquals(null, state.recordOf("/w/c", "c1"))
    }
}
