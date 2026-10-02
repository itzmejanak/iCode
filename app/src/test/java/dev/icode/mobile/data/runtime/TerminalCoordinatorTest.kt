package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.ManagedProcess
import dev.icode.mobile.domain.ManagedProcessState
import dev.icode.mobile.domain.TerminalSessionState
import dev.icode.mobile.domain.TerminalSettings
import dev.icode.mobile.domain.TerminalSnapshot
import dev.icode.mobile.domain.TerminalStatus
import dev.icode.mobile.domain.WorkbenchEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TerminalCoordinatorTest {
    private val root = "/storage/emulated/0/app"
    private val originalWriter = BackendLog.writer

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    private class FakeSessions : TerminalSessions {
        override val snapshot = MutableStateFlow(TerminalSnapshot())
        override val endings = MutableSharedFlow<TerminalEnding>(extraBufferCapacity = 8)
        override val commandsFinished = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val calls = mutableListOf<String>()
        val workspaces = mutableMapOf<String, String?>()
        var opened: String? = "session-1"
        var applied: TerminalSettings? = null

        override suspend fun open(workspaceHostPath: String?, reuseExisting: Boolean): String? {
            calls += "open $workspaceHostPath reuse=$reuseExisting"
            return opened
        }
        override suspend fun follow(output: FollowedOutput): String? {
            calls += "follow $output"
            return opened
        }
        override fun select(id: String) { calls += "select $id" }
        override fun reorder(movingId: String, targetId: String) { calls += "reorder $movingId $targetId" }
        override fun close(id: String) { calls += "close $id" }
        override fun applySettings(next: TerminalSettings) { applied = next }
        override fun workspaceOf(id: String): String? = workspaces[id]
    }

    private inner class Harness(installed: Boolean = true) {
        val workbench = WorkbenchStateHolder()
        val sessions = FakeSessions()
        val opened = mutableListOf<String>()
        var changed = 0
        val coordinator = TerminalCoordinator(
            workbench,
            sessions,
            runtimeInstalled = { installed },
            activeRoot = { root },
            openWorkspaceTerminal = { opened += it },
            workspaceChanged = { changed++ },
            main = Dispatchers.Unconfined,
        )

        suspend fun nextEffect(block: suspend () -> Unit): WorkbenchEffect = kotlinx.coroutines.coroutineScope {
            val effect = async { workbench.effects.first() }
            yield()
            block()
            withTimeout(2_000) { effect.await() }
        }
    }

    private fun running(id: String) = TerminalSessionState(id, "bash", TerminalStatus.Running)

    @Test fun `plus always opens a new session in the open workspace`() = runBlocking {
        val harness = Harness()
        harness.coordinator.open()
        assertEquals(listOf("open $root reuse=false"), harness.sessions.calls)
    }

    @Test fun `the panel's own open reuses the workspace's running session`() = runBlocking {
        val harness = Harness()
        harness.coordinator.ensure()
        harness.coordinator.ensure()
        assertEquals(listOf("open $root reuse=true", "open $root reuse=true"), harness.sessions.calls)
    }

    @Test fun `without the runtime nothing is opened`() = runBlocking {
        val harness = Harness(installed = false)
        val effect = harness.nextEffect { harness.coordinator.open() }
        assertEquals(WorkbenchEffect.Message("Install the Linux runtime before opening a terminal"), effect)
        assertTrue(harness.sessions.calls.isEmpty())
    }

    @Test fun `a session that could not start is said`() = runBlocking {
        val harness = Harness().apply { sessions.opened = null }
        val effect = harness.nextEffect { harness.coordinator.open() }
        assertEquals(WorkbenchEffect.Message("Unable to start a terminal session"), effect)
    }

    @Test fun `a workspace opened for its terminal reuses its running session`() = runBlocking {
        val harness = Harness()
        harness.sessions.snapshot.value = TerminalSnapshot(listOf(running("session-1")), "session-1")

        harness.coordinator.openIn(root)

        assertEquals(listOf("open $root reuse=true"), harness.sessions.calls)
        assertEquals("session-1", harness.workbench.current.terminal.selectedId)
    }

    @Test fun `showing a session selects it and opens its workspace`() = runBlocking {
        val harness = Harness()
        harness.sessions.snapshot.value = TerminalSnapshot(listOf(running("session-2")))
        harness.sessions.workspaces["session-2"] = "/storage/emulated/0/other"

        harness.coordinator.show("session-2")

        assertEquals(listOf("select session-2"), harness.sessions.calls)
        assertEquals(listOf("/storage/emulated/0/other"), harness.opened)
    }

    @Test fun `a process's output opens in a tab of its own, in the workspace it runs in`() = runBlocking {
        val harness = Harness()
        val process = ManagedProcess("p1", "/storage/emulated/0/other", "uvicorn", "uvicorn app:app", "/workspace/other", 0L, ManagedProcessState.Running)
        harness.sessions.snapshot.value = TerminalSnapshot(listOf(running("session-1")))
        harness.sessions.workspaces["session-1"] = process.workspace

        harness.coordinator.showOutput(process, "/tmp/icode/processes/p1.log")

        val followed = FollowedOutput("p1", "uvicorn", "/storage/emulated/0/other", "/tmp/icode/processes/p1.log")
        assertEquals(listOf("follow $followed", "select session-1"), harness.sessions.calls)
        assertEquals(listOf(process.workspace), harness.opened)
    }

    @Test fun `a session with no workspace opens the panel where it is`() = runBlocking {
        val harness = Harness()
        harness.sessions.snapshot.value = TerminalSnapshot(listOf(running("session-3")))
        val effect = harness.nextEffect { harness.coordinator.show("session-3") }
        assertEquals(WorkbenchEffect.WorkspaceTerminalOpened(root), effect)
    }

    @Test fun `an ended session is said, not shown`() = runBlocking {
        val harness = Harness()
        val effect = harness.nextEffect { harness.coordinator.show("session-9") }
        assertEquals(WorkbenchEffect.Message("That terminal session has ended"), effect)
        assertTrue(harness.opened.isEmpty())
    }

    @Test fun `following mirrors sessions, says failed exits, applies settings and refreshes after commands`() = runBlocking {
        val harness = Harness()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val settings = TerminalSettings(fontSizeSp = 15)
        try {
            harness.coordinator.follow(scope, flowOf(settings))
            harness.sessions.snapshot.value = TerminalSnapshot(listOf(running("session-1")), "session-1")
            assertEquals("session-1", harness.workbench.current.terminal.selectedId)
            assertEquals(settings, harness.sessions.applied)

            val effect = harness.nextEffect { harness.sessions.endings.emit(TerminalEnding("session-1", "bash", 3)) }
            assertEquals(WorkbenchEffect.Message("bash exited with code 3"), effect)

            harness.sessions.workspaces["session-1"] = root
            harness.sessions.workspaces["session-2"] = "/storage/emulated/0/other"
            harness.sessions.commandsFinished.emit("session-2")
            harness.sessions.commandsFinished.emit("session-1")
            assertEquals(1, harness.changed)
        } finally {
            scope.cancel()
        }
    }
}
