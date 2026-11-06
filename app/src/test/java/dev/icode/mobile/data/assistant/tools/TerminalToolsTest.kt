package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.tools.FakeTerminals.Answer
import dev.icode.mobile.data.runtime.FakeSpawnedProcess
import dev.icode.mobile.data.runtime.GuestEnvironment
import dev.icode.mobile.data.runtime.ProcessLogs
import dev.icode.mobile.data.runtime.RuntimeProcesses
import dev.icode.mobile.data.runtime.TerminalRuns
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantElsewhere
import dev.icode.mobile.domain.AssistantToolKind
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The agent's commands in terminal tabs (docs/agent-control-plan.md §4): its own, a new one, or one of the user's. */
class TerminalToolsTest {
    @get:Rule val temp = TemporaryFolder()

    private val runtime by lazy { TestRuntime(temp.root) }
    private val workspace by lazy { runtime.workspace }
    private val fake = FakeTerminals()
    private val terminals = TerminalRuns(fake, main = EmptyCoroutineContext, readyMillis = 300)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processes by lazy { RuntimeProcesses(scope, { FakeSpawnedProcess() }, { }, ProcessLogs.of(runtime.rootfs), grace = 200) }
    private val profile = "\u0000icode-environment\u0000PATH=/root/.nvm/bin:/usr/bin\u0000"
    private val shell by lazy { AssistantShell(GuestEnvironment(runtime.rootfs, { "/bin/bash" }) { profile }, processes, terminals) }
    private val shown = mutableListOf<Pair<String, String>>()
    private val step = AssistantStep(terminal = { callId, session -> shown += callId to session })
    private var changes = 0

    private fun tool(chat: String = "chat-1") = RunCommandTool(workspace, chat, shell, terminals, step, { true }) { changes++ }

    private fun command(line: String, timeout: Int? = null, terminal: String? = null, chat: String = "chat-1"): String = runBlocking {
        step.current = AssistantCall("c${shown.size + 1}", "run_command", "{}", "Run", AssistantToolKind.Command)
        tool(chat).run(JSONObject().put("command", line).apply { timeout?.let { put("timeout_seconds", it) }; terminal?.let { put("terminal", it) } })
    }

    private fun refusal(block: () -> Unit): String = try {
        block()
        fail("expected a refusal")
        ""
    } catch (refused: AssistantToolException) {
        refused.message.orEmpty()
    }

    @After fun stop() = scope.cancel()

    @Test fun `a command runs in the chat's own tab, pasted and entered, and its step shows that tab`() {
        fake.answer = { Answer(output = "hello\r\n") }
        assertEquals("Exit code 0 (succeeded)\nin /workspace/app\nterminal Assistant (session-1)\n\nhello", command("echo hello"))
        assertEquals(listOf("\u001B[200~echo hello\u001B[201~\r"), fake.typed)
        assertEquals(listOf("c1" to "session-1"), shown)
        // The replay keeps what it printed, as printed; the workspace refreshes.
        assertEquals("hello\r\n", step.takePrinted("c1"))
        assertEquals(1, changes)
        // The same tab, again; another chat gets its own.
        command("ls")
        command("pwd", chat = "chat-2")
        assertEquals(listOf("session-1", "session-2"), fake.opened)
        assertEquals("${workspace.place}#chat-2", fake.snapshot.value.sessions.last().assistant)
    }

    @Test fun `a cd carries to the next command and process, as in any terminal, and the reach is from there`() {
        File(workspace.root, "backend").mkdirs()
        fake.answer = { Answer(directory = "/workspace/app/backend") }
        assertTrue(command("cd backend").startsWith("Exit code 0 (succeeded)\nin /workspace/app/backend\n"))
        assertEquals("/workspace/app/backend" to null, shell.directory(workspace, "chat-1"))
        assertEquals(listOf(File(workspace.root, "backend"), File(workspace.root, "backend/src")), shell.reach(workspace, "chat-1", "ls ./src"))
        // Another chat starts at the top, and names paths from there.
        assertEquals(
            listOf(runtime.root, File(runtime.rootfs, "etc/hosts"), File(runtime.workspaces, "portofolio"), File(runtime.rootfs, "root/notes")),
            shell.reach(workspace, "chat-2", "cat /etc/hosts ../portofolio ~/notes"),
        )
        // A folder since gone: the top, and why.
        File(workspace.root, "backend").delete()
        assertEquals("/workspace/app" to "/workspace/app/backend is gone: this ran at the workspace's top", shell.directory(workspace, "chat-1"))
    }

    @Test fun `a code is said in words, a command not found with the PATH searched`() {
        fake.answer = { Answer(output = "bash: uvicorn: command not found\r\n", code = 127) }
        assertEquals(
            "Exit code 127 (command not found)\nin /workspace/app\nterminal Assistant (session-1)\nPATH: /root/.nvm/bin:/usr/bin\n\nbash: uvicorn: command not found",
            command("uvicorn app:app"),
        )
        fake.answer = { Answer(code = 143) }
        assertTrue(command("sleep 9").startsWith("Exit code 143 (ended by SIGTERM)\nin /workspace/app"))
    }

    @Test(timeout = 20_000)
    fun `a command past its time gets Ctrl-C, and one that keeps running is said to be`() {
        fake.answer = { Answer(output = "Listening on 5173\r\n", hang = true) }
        val stopped = command("npm run dev", timeout = 1)
        assertTrue(stopped, stopped.startsWith("Stopped with Ctrl-C at 1 s: exit code 130 (ended by SIGINT)\nin /workspace/app\nterminal Assistant (session-1)\n\nListening on 5173\n^C"))
        assertEquals("\u0003", fake.typed.last())
        fake.answer = { Answer(output = "ignoring\r\n", hang = true, stop = false) }
        val running = command("trap '' INT; sleep 99", timeout = 1)
        assertTrue(running, running.startsWith("Still running after Ctrl-C at 1 s: read it with read_terminal, answer or stop it with send_terminal_input\n"))
        assertTrue(running, running.endsWith("\n\nignoring\n^C"))
    }

    @Test fun `a busy tab or one with something typed is refused, and new opens another of the chat's`() {
        val own = runBlocking { terminals.tabOf(workspace.place, AssistantShell.chatOf(workspace, "chat-1"), fresh = false) }
        fake.answer = { Answer(hang = true) }
        fake.write(own, "\u001B[200~vim notes\u001B[201~\r")
        assertEquals(
            "A command is still running in that tab: wait for it, stop it with send_terminal_input ctrl-c, or use terminal \"new\"",
            refusal { command("ls") },
        )
        fake.answer = { Answer(output = "a.txt\r\n") }
        assertEquals("Exit code 0 (succeeded)\nin /workspace/app\nterminal Assistant 2 (session-2)\n\na.txt", command("ls", terminal = "new"))
        fake.write("session-2", "rm -rf")
        assertEquals("Something is typed at that tab's prompt: use your own tab, or terminal \"new\"", refusal { command("ls", terminal = "session-2") })
    }

    @Test fun `a tab of the user's is a place to ask about, and a shell without marks is refused`() {
        val bash = fake.userTab("bash")
        val zsh = fake.userTab("zsh", marks = false)
        val own = runBlocking { terminals.tabOf(workspace.place, AssistantShell.chatOf(workspace, "chat-1"), fresh = false) }
        assertEquals(AssistantElsewhere("bash", "terminal:$bash", AssistantElsewhere.Kind.Terminal), tool().place(JSONObject().put("terminal", bash)))
        // The chat's own, a new one of its own, and no such tab: nowhere to ask about.
        assertNull(tool().place(JSONObject().put("terminal", own)))
        assertNull(tool().place(JSONObject().put("terminal", "new")))
        assertNull(tool().place(JSONObject()))
        assertNull(tool().place(JSONObject().put("terminal", "session-9")))
        // The chat's own tab is another chat's place.
        assertEquals(AssistantElsewhere.Kind.Terminal, tool("chat-2").place(JSONObject().put("terminal", own))?.kind)
        fake.answer = { Answer(output = "ok\r\n") }
        assertEquals("Exit code 0 (succeeded)\nin /workspace/app\nterminal bash ($bash)\n\nok", command("make", terminal = bash))
        assertEquals(
            "That tab's shell does not say where a command ends (only bash tabs iCode starts do): run it in your own tab instead",
            refusal { command("make", terminal = zsh) },
        )
        assertEquals("There is no terminal tab session-9 running: list_terminals lists them", refusal { command("make", terminal = "session-9") })
        fake.end(bash)
        assertNull(tool().place(JSONObject().put("terminal", bash)))
    }

    @Test fun `the tabs are listed with whose each is and its state, read, and typed into`() = runBlocking {
        val tools = Triple(ListTerminalsTool(workspace, "chat-1", terminals), ReadTerminalTool(workspace, "chat-1", terminals), SendTerminalInputTool(workspace, "chat-1", terminals))
        assertEquals("No terminal tab is open. run_command opens this chat's own.", tools.first.run(JSONObject()))
        val user = fake.userTab("bash")
        fake.answer = { Answer(output = "Continue? [y/N] ", hang = true) }
        val asking = scope.async { runCatching { command("apt install tree", timeout = 30) } }
        while (fake.opened.isEmpty() || fake.commands(fake.opened.single())?.running != true) Thread.sleep(5)
        val own = fake.opened.single()
        assertEquals(
            "$user · bash · the user's · in app · ready\n$own · Assistant · this chat's · in app · running a command",
            tools.first.run(JSONObject()),
        )
        assertTrue(tools.second.run(JSONObject().put("id", own)).endsWith("$ apt install tree\nContinue? [y/N] "))
        assertEquals(AssistantElsewhere.Kind.Terminal, tools.second.place(JSONObject().put("id", user))?.kind)
        assertNull(tools.third.place(JSONObject().put("id", own)))
        // The answer, with a key after it, shown on the tab; then Ctrl-C ends what runs.
        val typed = tools.third.run(JSONObject().put("id", own).put("text", "y").put("keys", JSONArray().put("enter")))
        assertTrue(typed, typed.startsWith("Typed into Assistant. It shows:\n\n") && typed.contains("Continue? [y/N] y"))
        tools.third.run(JSONObject().put("id", own).put("keys", JSONArray().put("ctrl-c")))
        assertEquals(listOf("y\r", "\u0003"), fake.typed.takeLast(2))
        assertTrue(asking.await().getOrThrow().startsWith("Exit code 130 (ended by SIGINT)"))
        assertEquals(
            "home is not a key this can press: ${TerminalRuns.KEYS.keys.joinToString()}",
            refusal { runBlocking { tools.third.run(JSONObject().put("id", own).put("keys", JSONArray().put("home"))) } },
        )
        assertEquals("Give text, keys or both", refusal { runBlocking { tools.third.run(JSONObject().put("id", own)) } })
    }

    @Test fun `a deleted chat's tabs close, and no one else's`() {
        val user = fake.userTab("bash")
        command("ls")
        command("ls", terminal = "new")
        command("pwd", chat = "chat-2")
        runBlocking { terminals.close(AssistantShell.chatOf(workspace, "chat-1")) }
        assertEquals(listOf(user, "session-4"), fake.snapshot.value.sessions.map { it.id })
    }

    @Test fun `what is typed loses its control characters but its new lines and tabs`() {
        assertEquals("a\tb\nc", TerminalRuns.typed("a\tb\nc\u0003\u007F"))
        assertEquals("echo [201~ done", TerminalRuns.typed("echo \u001B[201~ done"))
    }
}
