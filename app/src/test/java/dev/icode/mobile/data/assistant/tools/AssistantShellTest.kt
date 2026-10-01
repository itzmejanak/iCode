package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.GuestEnvironment
import dev.icode.mobile.data.runtime.PosixSignal
import dev.icode.mobile.data.runtime.RuntimeProcesses
import dev.icode.mobile.data.runtime.SpawnedProcess
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
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

    private val rootfs by lazy { temp.newFolder("rootfs") }
    private val workspace by lazy { AssistantWorkspace(temp.newFolder("app"), "/workspace/app") }
    private val scripts = mutableListOf<String>()
    private val environments = mutableListOf<Map<String, String>>()
    /** What the next command does: prints, ends somewhere, exits with a code — or never ends. */
    private var next: suspend (onLine: (String) -> Unit) -> Pair<Int, String?> = { 0 to null }
    private var stderr = ""
    private var profile = "\u0000icode-environment\u0000PATH=/root/.nvm/bin:/usr/bin\u0000NVM_DIR=/root/.nvm\u0000"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val spawned = mutableListOf<FakeProcess>()
    private val processes = RuntimeProcesses(scope, { FakeProcess().also { spawned += it } }, { }, grace = 200)
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
            GuestEnvironment(rootfs) { profile },
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
        // Its login environment, then what makes output plain.
        assertEquals("/root/.nvm", environments[1]["NVM_DIR"])
        assertEquals("dumb", environments[1]["TERM"])
        assertEquals("cat", environments[1]["PAGER"])
        // Another chat starts at the top.
        runBlocking { RunCommandTool(workspace, "chat-2", shell, { true }, { }).run(JSONObject().put("command", "pwd")) }
        assertTrue(scripts[2].startsWith("cd -- '/workspace/app' || exit 1"))
    }

    @Test fun `a directory outside the workspace, or gone, sends the next command to the top, and says so`() {
        next = { 0 to "/tmp" }
        command("cd /tmp")
        next = { 0 to null }
        val result = command("ls")
        assertTrue(scripts[1].startsWith("cd -- '/workspace/app' || exit 1"))
        assertTrue(result, result.contains("The last command ended in /tmp, outside this workspace: this one ran at its top"))
        File(workspace.root, "gone").mkdirs()
        next = { 0 to "/workspace/app/gone" }
        command("cd gone")
        File(workspace.root, "gone").delete()
        next = { 0 to null }
        assertTrue(command("ls").contains("/workspace/app/gone is gone: this ran at the workspace's top"))
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
        assertTrue(text, text.contains("`uvicorn app.main:app` in /workspace/app/backend"))
        assertTrue(text, text.contains("Ports its output names: 8000 (answers)"))
        assertTrue(text, text.contains("INFO: Uvicorn running on http://127.0.0.1:8000"))

        val status = ProcessStatusTool(workspace, processes, answers = { false })
        assertTrue(status.run(JSONObject()).startsWith("p1 · uvicorn · Running"))
        assertTrue(status.run(JSONObject().put("id", "p1")).endsWith("No new output"))
        spawned.single().say("GET / 200\n")
        while (processes.info("p1")?.let { true } != true) Thread.sleep(5)
        Thread.sleep(100)
        assertTrue(status.run(JSONObject().put("id", "p1")).endsWith("GET / 200\n"))

        assertTrue(SendProcessInputTool(workspace, processes).run(JSONObject().put("id", "p1").put("text", "y")).startsWith("Sent to p1."))
        assertEquals("y\n", spawned.single().input.toString())

        assertEquals("Stopped p1", StopProcessTool(workspace, processes).run(JSONObject().put("id", "p1")))
        assertEquals(listOf(PosixSignal.TERM), spawned.single().signals)
    }

    @Test fun `another workspace's processes are not this one's`() = runBlocking {
        processes.start("/w/other", "/workspace/other", "vite", null, emptyMap())
        assertEquals("No processes in this workspace", ProcessStatusTool(workspace, processes).run(JSONObject()))
        try {
            StopProcessTool(workspace, processes).run(JSONObject().put("id", "p1"))
            fail("expected a refusal")
        } catch (refused: AssistantToolException) {
            assertEquals("There is no process p1 in this workspace", refused.message)
        }
    }

    private fun <T> runAsync(block: suspend () -> T) = scope.async { block() }

    private class FakeProcess : SpawnedProcess {
        private val writer = PipedOutputStream()
        override val output = PipedInputStream(writer, 64 * 1024)
        override val input = ByteArrayOutputStream()
        val signals = mutableListOf<Int>()
        private val ended = CountDownLatch(1)
        @Volatile private var code = 0

        fun say(text: String) {
            writer.write(text.toByteArray())
            writer.flush()
        }

        override fun signal(number: Int) {
            signals += number
            if (ended.count > 0) {
                code = 128 + number
                writer.close()
                ended.countDown()
            }
        }

        override fun waitFor(): Int {
            ended.await()
            return code
        }

        override fun close() = Unit
    }
}
