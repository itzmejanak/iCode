package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.runtime.FakeSpawnedProcess
import dev.icode.mobile.data.runtime.GuestEnvironment
import dev.icode.mobile.data.runtime.PosixSignal
import dev.icode.mobile.data.runtime.ProcessLogs
import dev.icode.mobile.data.runtime.RuntimeProcesses
import dev.icode.mobile.data.runtime.TerminalRuns
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A chat's processes start where its terminal tab's shell is, with the login environment (docs/assistant-and-mcp.md). */
class AssistantShellTest {
    @get:Rule val temp = TemporaryFolder()

    private val runtime by lazy { TestRuntime(temp.root) }
    private val rootfs by lazy { runtime.rootfs }
    private val workspace by lazy { runtime.workspace }
    private var profile = "\u0000icode-environment\u0000PATH=/root/.nvm/bin:/usr/bin\u0000NVM_DIR=/root/.nvm\u0000"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val spawned = mutableListOf<FakeSpawnedProcess>()
    private val environments = mutableListOf<Map<String, String>>()
    private val processes by lazy {
        RuntimeProcesses(scope, { start -> environments += start.environment; FakeSpawnedProcess().also { spawned += it } }, { }, ProcessLogs.of(rootfs), grace = 200)
    }
    private val fake = FakeTerminals()
    private val terminals = TerminalRuns(fake, main = EmptyCoroutineContext, readyMillis = 300)
    private val shell by lazy { AssistantShell(GuestEnvironment(rootfs, { "/bin/bash" }) { profile }, processes, terminals) }

    @After fun stop() = scope.cancel()

    private fun start(line: String) = runBlocking {
        StartProcessTool(workspace, "chat-1", shell, processes, { true }, answers = { false }).run(JSONObject().put("command", line).put("wait_seconds", 0))
    }

    @Test fun `a process starts where the chat's tab is, with the login environment, and is read, written to and stopped by its ID`() = runBlocking {
        File(workspace.root, "backend").mkdirs()
        fake.answer = { FakeTerminals.Answer(directory = "/workspace/app/backend") }
        RunCommandTool(workspace, "chat-1", shell, terminals, AssistantStep(), { true }) { }.run(JSONObject().put("command", "cd backend"))
        val starting = StartProcessTool(workspace, "chat-1", shell, processes, { true }, answers = { it == 8000 })
        val started = runAsync { starting.run(JSONObject().put("command", "uvicorn app.main:app").put("wait_seconds", 1)) }
        while (spawned.isEmpty()) Thread.sleep(5)
        spawned.single().say("INFO: Uvicorn running on http://127.0.0.1:8000\n")
        val text = started.await()
        assertTrue(text, text.startsWith("Started p1 · uvicorn · Running · "))
        assertTrue(text, text.contains("`uvicorn app.main:app` in /workspace/app/backend · workspace “app”"))
        assertTrue(text, text.contains("Ports its output names: 8000 (answers)"))
        assertTrue(text, text.contains("INFO: Uvicorn running on http://127.0.0.1:8000"))
        // The login environment, and no colour: a model reads its output.
        assertEquals(mapOf("PATH" to "/root/.nvm/bin:/usr/bin", "NVM_DIR" to "/root/.nvm", "NO_COLOR" to "1"), environments.single().filterKeys { it in setOf("PATH", "NVM_DIR", "NO_COLOR") })

        val status = ProcessStatusTool(processes, runtime.paths, answers = { false })
        assertTrue(status.run(JSONObject()).startsWith("p1 · uvicorn · Running"))
        assertTrue(status.run(JSONObject().put("id", "p1")).endsWith("No new output"))
        spawned.single().say("GET / 200\n")
        Thread.sleep(100)
        assertTrue(status.run(JSONObject().put("id", "p1")).endsWith("GET / 200\n"))

        assertTrue(SendProcessInputTool(processes, runtime.paths).run(JSONObject().put("id", "p1").put("text", "y")).startsWith("Sent to p1."))
        assertEquals("y\n", spawned.single().input.toString())

        assertEquals("Stopped p1", StopProcessTool(processes, runtime.paths).run(JSONObject().put("id", "p1")))
        assertEquals(listOf(PosixSignal.TERM), spawned.single().signals)
    }

    @Test fun `a process of a chat without a tab starts at the top, and a login environment that could not be read is said`() {
        profile = "bash: exec zsh"
        val text = start("node server.js")
        assertTrue(text, text.contains("`node server.js` in /workspace/app · workspace “app”"))
        assertTrue(text, text.contains("\nYour login environment could not be read (your shell profile ended before it was read through (an `exec` in it?)): this ran with iCode's default environment"))
    }

    @Test fun `every workspace's processes are reached, each said with its workspace, and a ninth starts`() = runBlocking {
        repeat(9) { processes.start("/w/other", "/workspace/other", "vite $it", null, emptyMap()).getOrThrow() }
        val listed = ProcessStatusTool(processes, runtime.paths, answers = { false }).run(JSONObject())
        assertTrue(listed, listed.startsWith("p1 · vite · Running"))
        assertTrue(listed, listed.contains("`vite 8` in /workspace/other · workspace “other”"))
        assertEquals("Stopped p9", StopProcessTool(processes, runtime.paths).run(JSONObject().put("id", "p9")))
        try {
            StopProcessTool(processes, runtime.paths).run(JSONObject().put("id", "p10"))
            fail("expected a refusal")
        } catch (refused: AssistantToolException) {
            assertEquals("There is no process p10", refused.message)
        }
    }

    @Test fun `no process is said so`() = runBlocking {
        assertEquals("No processes", ProcessStatusTool(processes, runtime.paths).run(JSONObject()))
    }

    private fun <T> runAsync(block: suspend () -> T) = scope.async { block() }
}
