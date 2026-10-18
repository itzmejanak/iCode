package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.ManagedProcess
import dev.icode.mobile.domain.ManagedProcessState
import dev.icode.mobile.domain.ProcessRules
import dev.icode.mobile.domain.ICodeIntent
import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeProcessesTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val launched = Collections.synchronizedList(mutableListOf<Pair<RuntimeProcesses.Launch, FakeSpawnedProcess>>())
    @Volatile private var published: List<ManagedProcess> = emptyList()
    private var next: () -> FakeSpawnedProcess = { FakeSpawnedProcess() }
    private val logDirectory = Files.createTempDirectory("logs").toFile()
    private val logs = ProcessLogs(logDirectory)
    private val processes = RuntimeProcesses(scope, { launch -> next().also { launched += launch to it } }, { published = it }, logs, grace = 200)

    @After fun stop() {
        scope.cancel()
        logDirectory.deleteRecursively()
    }

    private fun log(id: String) = File(logDirectory, "$id.log").takeIf { it.isFile }?.readText().orEmpty()

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; published: $published" }
            Thread.sleep(5)
        }
    }

    private fun start(command: String = "uvicorn app.main:app", workspace: String = "/w/app", name: String? = null) =
        processes.start(workspace, "/workspace/app", command, name, mapOf("PATH" to "/usr/bin")).getOrThrow()

    @Test fun `a process runs in its workspace and directory, its output logged as it came and its ports found`() {
        val started = start()
        assertEquals("p1", started.id)
        assertEquals("uvicorn", started.name)
        val (launch, fake) = launched.single()
        assertEquals(RuntimeProcesses.Launch("/w/app", "/workspace/app", "uvicorn app.main:app", mapOf("PATH" to "/usr/bin")), launch)
        fake.say("INFO: Uvicorn running on \u001B[1mhttp://127.0.0.1:8000\u001B[0m (Press CTRL+C to quit)\n")
        until { published.singleOrNull()?.ports == listOf(8000) }
        // As printed, colours and all: the terminal that follows it draws them.
        until { log("p1").contains("Uvicorn running on \u001B[1mhttp://127.0.0.1:8000\u001B[0m") }
        assertEquals("/tmp/icode/processes/p1.log", processes.logOf("p1"))
        assertTrue(processes.runsIn("/w/app"))
        assertFalse(processes.runsIn("/w/other"))
        fake.exit(0)
        until { published.single().state is ManagedProcessState.Ended }
        assertEquals(ManagedProcessState.Ended(0, (published.single().state as ManagedProcessState.Ended).at, stopped = false), published.single().state)
        assertFalse(processes.anyRunning())
    }

    @Test fun `the assistant reads what came since it last looked, and looking at its state moves nothing`() {
        val started = start()
        val fake = launched.single().second
        fake.say("one\n")
        until { log(started.id).contains("one") }
        assertEquals("one\n", processes.read(started.id)?.output)
        processes.info(started.id)
        fake.say("two\nPassword: ")
        until { log(started.id).contains("Password") }
        assertEquals("two\nPassword: ", processes.read(started.id)?.output)
        assertEquals("Password: ", processes.read(started.id)?.output)
        assertNull(processes.read("p9"))
    }

    @Test fun `input reaches a running process and is refused once it ended`() {
        val started = start()
        val fake = launched.single().second
        assertNull(processes.write(started.id, "y\n"))
        assertEquals("y\n", fake.input.toString())
        fake.exit(1)
        until { !processes.anyRunning() }
        assertEquals("${started.id} has ended", processes.write(started.id, "n\n"))
        assertEquals("There is no process p9", processes.write("p9", ""))
    }

    @Test fun `stopping sends SIGTERM, then SIGKILL to one that ignores it, and says it was stopped`() {
        next = { FakeSpawnedProcess(diesBy = setOf(PosixSignal.KILL)) }
        val started = start()
        val fake = launched.single().second
        assertTrue(processes.stop(started.id))
        until { !processes.anyRunning() }
        assertEquals(listOf(PosixSignal.TERM, PosixSignal.KILL), fake.signals.toList())
        val ended = published.single().state as ManagedProcessState.Ended
        assertEquals(137, ended.code)
        assertTrue(ended.stopped)
        assertFalse("an ended one is not stopped again", processes.stop(started.id))
    }

    @Test fun `at most eight run at once, Stop All ends them, and the ended leave the list when forgotten`() {
        repeat(ProcessRules.MAX_RUNNING) { start("worker $it") }
        val refused = processes.start("/w/app", "/workspace/app", "one more", null, emptyMap())
        assertEquals("8 processes run already: stop one first. Android ends apps' processes past a limit", refused.exceptionOrNull()?.message)
        processes.handle(ICodeIntent.StopAllProcesses)
        until { !processes.anyRunning() }
        processes.handle(ICodeIntent.ForgetProcess("p1"))
        until { published.size == ProcessRules.MAX_RUNNING - 1 }
        assertTrue(published.none { it.id == "p1" })
    }

    @Test fun `a running process is never forgotten, and a launch the runtime refuses is said`() {
        val started = start()
        processes.forget(started.id)
        assertEquals(listOf(started.id), processes.of("/w/app").map { it.id })
        next = { throw IllegalArgumentException("/w/x is not in iCode's workspaces") }
        assertEquals(
            "Unable to start it: /w/x is not in iCode's workspaces",
            processes.start("/w/x", "/workspace/x", "ls", null, emptyMap()).exceptionOrNull()?.message,
        )
    }

    @Test fun `what ended in a workspace follows its rename and goes with its delete, its log too`() {
        val started = start()
        launched.single().second.say("done\n")
        until { log(started.id).isNotEmpty() }
        launched.single().second.exit(0)
        until { !processes.anyRunning() }
        processes.workspaceMoved("/w/app", "/w/renamed")
        assertEquals(listOf(started.id), processes.of("/w/renamed").map { it.id })
        processes.workspaceForgotten("/w/renamed")
        assertTrue(processes.of("/w/renamed").isEmpty())
        assertFalse(File(logDirectory, "${started.id}.log").exists())
    }

    @Test fun `a port is read from whole lines, a client's address in a request log is none, and output alone publishes nothing`() {
        start()
        val fake = launched.single().second
        // An address cut between two reads.
        fake.say("INFO: Uvicorn running on http://127.0.0.1:80")
        fake.say("00 (Press CTRL+C to quit)\n")
        until { published.singleOrNull()?.ports == listOf(8000) }
        val before = published
        fake.say("INFO:     127.0.0.1:50744 - \"GET /v1/tags HTTP/1.1\" 404 Not Found\n")
        until { log("p1").contains("50744") }
        assertEquals(listOf(8000), processes.info("p1")?.ports)
        assertTrue("output alone is not published", published === before)
    }

    @Test fun `a forgotten process's log goes, and an earlier iCode's logs go when it starts`() {
        File(logDirectory, "p7.log").writeText("from an earlier iCode")
        RuntimeProcesses(scope, { FakeSpawnedProcess() }, {}, ProcessLogs(logDirectory))
        assertFalse(File(logDirectory, "p7.log").exists())
        val started = start()
        launched.single().second.say("bye\n")
        until { log(started.id).isNotEmpty() }
        launched.single().second.exit(0)
        until { !processes.anyRunning() }
        processes.forget(started.id)
        assertFalse(File(logDirectory, "${started.id}.log").exists())
    }

    @Test fun `a log past its limit moves aside and a new one starts`() {
        val small = ProcessLogs(logDirectory, limit = 10)
        small.append("p1", "0123456789")
        small.append("p1", "after")
        assertEquals("0123456789", File(logDirectory, "p1.log.1").readText())
        assertEquals("after", File(logDirectory, "p1.log").readText())
        small.remove("p1")
        assertTrue(logDirectory.listFiles().isNullOrEmpty())
    }

    @Test fun `a process is named by its command's first word, past its variables and its path`() {
        assertEquals("uvicorn", RuntimeProcesses.nameOf("uvicorn app.main:app --reload"))
        assertEquals("npm", RuntimeProcesses.nameOf("PORT=3000 NODE_ENV=dev npm run dev"))
        assertEquals("vite", RuntimeProcesses.nameOf("./node_modules/.bin/vite"))
        assertEquals("process", RuntimeProcesses.nameOf("  "))
    }
}
