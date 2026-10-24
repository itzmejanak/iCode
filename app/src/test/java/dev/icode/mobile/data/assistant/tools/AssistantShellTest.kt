package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.runtime.FakeSpawnedProcess
import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.GuestEnvironment
import dev.icode.mobile.data.runtime.PosixSignal
import dev.icode.mobile.data.runtime.ProcessLogs
import dev.icode.mobile.data.runtime.RuntimeProcesses
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
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

class AssistantShellTest {
    @get:Rule val temp = TemporaryFolder()

    private val runtime by lazy { TestRuntime(temp.root) }
    private val rootfs by lazy { runtime.rootfs }
    private val workspace by lazy { runtime.workspace }
    private val scripts = mutableListOf<String>()
    private val environments = mutableListOf<Map<String, String>>()
    /** What the next command does: prints, ends somewhere, exits with a code — or never ends. */
    private var next: suspend (onLine: (String) -> Unit) -> Pair<Int, String?> = { 0 to null }
    private var stderr = ""
    private var profile = "\u0000icode-environment\u0000PATH=/root/.nvm/bin:/usr/bin\u0000NVM_DIR=/root/.nvm\u0000"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val spawned = mutableListOf<FakeSpawnedProcess>()
    private val processes by lazy { RuntimeProcesses(scope, { FakeSpawnedProcess().also { spawned += it } }, { }, ProcessLogs.of(rootfs), grace = 200) }
    private val shell by lazy {
        AssistantShell(
            rootfs,
            { _, script, variables, onLine ->
                scripts += script
                environments += variables
                val (code, endedIn) = next(onLine)
                // As the script's EXIT trap would: where it ended, in the file it names.
                val marker = Regex("""pwd -P > /tmp/(\S+)'""").find(script)!!.groupValues[1]
                endedIn?.let { File(rootfs, "tmp/$marker").writeText("$it\n") }
                GuestCommand.Result(code, stderr, stoppedEarly = false)
            },
            GuestEnvironment(rootfs, { "/bin/bash" }) { profile },
            processes,
        )
    }

    @After fun stop() = scope.cancel()

    private fun command(line: String, timeout: Int? = null) = runBlocking {
        RunCommandTool(workspace, "chat-1", shell, { true }, { }).run(JSONObject().put("command", line).apply { timeout?.let { put("timeout_seconds", it) } })
    }

    @Test fun `a command starts at the top, its cd carries to the next, and the result says where it ended`() {
        File(workspace.root, "backend").mkdirs()
        next = { onLine -> onLine("moved"); 0 to "/workspace/app/backend" }
        assertEquals("Exit code 0 (succeeded)\nin /workspace/app/backend\n\nmoved", command("cd backend && echo moved"))
        assertTrue(scripts[0].startsWith("cd -- '/workspace/app' || exit 1\ntrap 'pwd -P > /tmp/icode-cwd-"))
        assertTrue(scripts[0].endsWith("' EXIT\n{ cd backend && echo moved\n} </dev/null"))
        next = { 0 to "/workspace/app/backend" }
        command("ls")
        assertTrue(scripts[1].startsWith("cd -- '/workspace/app/backend' || exit 1"))
        // Over the `command` context's variables (the login environment, no terminal): no colour, a model reads it.
        assertEquals(mapOf("NO_COLOR" to "1"), environments[1])
        // Another chat starts at the top.
        runBlocking { RunCommandTool(workspace, "chat-2", shell, { true }, { }).run(JSONObject().put("command", "pwd")) }
        assertTrue(scripts[2].startsWith("cd -- '/workspace/app' || exit 1"))
    }

    @Test fun `the next command starts where the last ended, anywhere in the runtime, or at the top once that is gone`() {
        File(rootfs, "root/projects").mkdirs()
        next = { 0 to "/root/projects" }
        command("cd /root/projects")
        next = { 0 to null }
        val kept = command("ls")
        assertTrue(scripts[1].startsWith("cd -- '/root/projects' || exit 1"))
        assertTrue(kept, kept.startsWith("Exit code 0 (succeeded)\nin /root/projects"))
        File(rootfs, "root/projects").delete()
        assertTrue(command("ls").contains("/root/projects is gone: this ran at the workspace's top"))
        assertTrue(scripts[2].startsWith("cd -- '/workspace/app' || exit 1"))
    }

    @Test fun `a command reaches where it starts and what it names, from there`() {
        File(rootfs, "root/projects").mkdirs()
        assertEquals(listOf(File(runtime.workspaces, "app")), shell.reach(workspace, "chat-1", "npm test"))
        assertEquals(
            listOf(File(runtime.workspaces, "app"), File(rootfs, "etc/hosts"), File(runtime.workspaces, "portofolio"), File(rootfs, "root/notes")),
            shell.reach(workspace, "chat-1", "cat /etc/hosts ../portofolio ~/notes"),
        )
        next = { 0 to "/root/projects" }
        command("cd /root/projects")
        assertEquals(listOf(File(rootfs, "root/projects"), File(rootfs, "root/projects/src")), shell.reach(workspace, "chat-1", "ls ./src"))
    }

    @Test fun `a status is said in words, a command not found with the PATH searched`() {
        next = { 127 to "/workspace/app" }
        stderr = "bash: line 3: uvicorn: command not found"
        assertEquals(
            "Exit code 127 (command not found)\nin /workspace/app\nPATH: /root/.nvm/bin:/usr/bin\n\nstderr:\nbash: line 3: uvicorn: command not found",
            command("uvicorn app:app"),
        )
        stderr = ""
        next = { 143 to null }
        assertTrue(command("sleep 9").startsWith("Exit code 143 (ended by SIGTERM)\nin /workspace/app"))
    }

    @Test(timeout = 10_000)
    fun `a command past its time is stopped, keeps the directory it started in, and is told to use start_process`() {
        next = { 0 to "/workspace/app" }
        command("pwd")
        next = { awaitCancellation() }
        val result = command("npm run dev", timeout = 1)
        assertTrue(result, result.startsWith("Stopped after 1 s, still running: it ended with everything it started. Run a server, a watcher or anything that must keep running with start_process.\nin /workspace/app"))
    }

    @Test fun `a login environment that could not be read is said, and iCode's PATH is the one searched`() {
        profile = "bash: exec zsh"
        next = { 127 to null }
        val result = command("node -v")
        assertTrue(result, result.contains("Your login environment could not be read (your shell profile ended before it was read through (an `exec` in it?)): this ran with iCode's default environment"))
        assertTrue(result.contains("PATH: /root/.local/bin:/usr/local/sbin"))
    }

    @Test fun `a process starts where the chat's commands run, and is read, written to and stopped by its ID`() = runBlocking {
        File(workspace.root, "backend").mkdirs()
        next = { 0 to "/workspace/app/backend" }
        command("cd backend")
        val start = StartProcessTool(workspace, "chat-1", shell, processes, { true }, answers = { it == 8000 })
        val started = runAsync { start.run(JSONObject().put("command", "uvicorn app.main:app").put("wait_seconds", 1)) }
        while (spawned.isEmpty()) Thread.sleep(5)
        spawned.single().say("INFO: Uvicorn running on http://127.0.0.1:8000\n")
        val text = started.await()
        assertTrue(text, text.startsWith("Started p1 · uvicorn · Running · "))
        assertTrue(text, text.contains("`uvicorn app.main:app` in /workspace/app/backend · workspace “app”"))
        assertTrue(text, text.contains("Ports its output names: 8000 (answers)"))
        assertTrue(text, text.contains("INFO: Uvicorn running on http://127.0.0.1:8000"))

        val status = ProcessStatusTool(processes, answers = { false })
        assertTrue(status.run(JSONObject()).startsWith("p1 · uvicorn · Running"))
        assertTrue(status.run(JSONObject().put("id", "p1")).endsWith("No new output"))
        spawned.single().say("GET / 200\n")
        while (processes.info("p1")?.let { true } != true) Thread.sleep(5)
        Thread.sleep(100)
        assertTrue(status.run(JSONObject().put("id", "p1")).endsWith("GET / 200\n"))

        assertTrue(SendProcessInputTool(processes).run(JSONObject().put("id", "p1").put("text", "y")).startsWith("Sent to p1."))
        assertEquals("y\n", spawned.single().input.toString())

        assertEquals("Stopped p1", StopProcessTool(processes).run(JSONObject().put("id", "p1")))
        assertEquals(listOf(PosixSignal.TERM), spawned.single().signals)
    }

    @Test fun `every workspace's processes are reached, each said with its workspace, and a ninth starts`() = runBlocking {
        repeat(9) { processes.start("/w/other", "/workspace/other", "vite $it", null, emptyMap()).getOrThrow() }
        val listed = ProcessStatusTool(processes, answers = { false }).run(JSONObject())
        assertTrue(listed, listed.startsWith("p1 · vite · Running"))
        assertTrue(listed, listed.contains("`vite 8` in /workspace/other · workspace “other”"))
        assertEquals("Stopped p9", StopProcessTool(processes).run(JSONObject().put("id", "p9")))
        try {
            StopProcessTool(processes).run(JSONObject().put("id", "p10"))
            fail("expected a refusal")
        } catch (refused: AssistantToolException) {
            assertEquals("There is no process p10", refused.message)
        }
    }

    @Test fun `no process is said so`() = runBlocking {
        assertEquals("No processes", ProcessStatusTool(processes).run(JSONObject()))
    }

    private fun <T> runAsync(block: suspend () -> T) = scope.async { block() }
}
