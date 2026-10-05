package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.ManagedProcess
import dev.icode.mobile.domain.ManagedProcessState
import dev.icode.mobile.domain.ProcessRules
import dev.icode.mobile.domain.ICodeIntent
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A started process, as [RuntimeProcesses] drives it: [RuntimeProcess] on a phone. */
internal interface SpawnedProcess {
    val output: InputStream
    val input: OutputStream
    fun signal(number: Int)
    /** Blocks until it ends: its exit code, or 128 + the signal that ended it. */
    fun waitFor(): Int
    fun close()
}

/**
 * Managed processes (docs/runtime-foundation-plan.md §3.4): what must run beyond the command that
 * started it — a development server, a watcher, a worker — each in a PRoot of its own, kept until
 * it is stopped, until it ends, or until iCode's process does. Their state reaches [publish] as it
 * changes — started, a port found, ended — never their output: that is in [logs], for the terminal
 * (§6), and here, for the assistant.
 */
internal class RuntimeProcesses(
    private val scope: CoroutineScope,
    private val launch: (Launch) -> SpawnedProcess,
    private val publish: (List<ManagedProcess>) -> Unit,
    private val logs: ProcessLogs,
    private val clock: () -> Long = System::currentTimeMillis,
    /** How long a stopped process has between SIGTERM and SIGKILL. */
    private val grace: Long = GRACE_MILLIS,
) {
    /** What to start: [command] in bash, in [directory] (the guest's), seeing only [workspace]. */
    data class Launch(val workspace: String, val directory: String, val command: String, val environment: Map<String, String>)

    /** What the assistant reads of a process: its state, and its output since it last looked. */
    data class Read(val process: ManagedProcess, val output: String, val missed: Boolean)

    private class Entry(var info: ManagedProcess, val process: SpawnedProcess) {
        val output = ProcessOutput()
        var stopRequested = false
        var agentOffset = 0L

        /** The line being written, until it ends: a port is read from whole lines. */
        var portLine = ""
    }

    private val lock = Any()
    private val entries = linkedMapOf<String, Entry>()
    private var counter = 0

    // An earlier iCode's processes ended with it; their logs would be shown under their IDs again.
    init {
        logs.clear()
    }

    /** Starts [command]; the process, or why it was not started. */
    fun start(workspace: String, directory: String, command: String, name: String?, environment: Map<String, String>): Result<ManagedProcess> {
        val entry = synchronized(lock) {
            if (entries.values.count { it.info.running } >= ProcessRules.MAX_RUNNING) {
                return Result.failure(
                    IOException("${ProcessRules.MAX_RUNNING} processes run already: stop one first. Android ends apps' processes past a limit"),
                )
            }
            val spawned = try {
                launch(Launch(workspace, directory, command, environment))
            } catch (failure: Exception) {
                return Result.failure(IOException("Unable to start it: ${failure.message ?: "the runtime refused"}"))
            }
            counter += 1
            val info = ManagedProcess(
                id = "p$counter",
                workspace = workspace,
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: nameOf(command),
                command = command,
                directory = directory,
                startedAt = clock(),
                state = ManagedProcessState.Running,
            )
            Entry(info, spawned).also { entries[info.id] = it }
        }
        // There from the start, so the tab that follows it never opens on a missing file.
        logs.append(entry.info.id, "")
        follow(entry)
        publishNow()
        return Result.success(entry.info)
    }

    /**
     * Ends [id]: SIGTERM to its process group, which PRoot also passes on to every process it runs,
     * killing those still running two seconds later (third_party/patches/proot/0002); then SIGKILL
     * after [grace] if PRoot itself is still running, which ends it and, through
     * `PTRACE_O_EXITKILL`, everything it runs.
     */
    fun stop(id: String): Boolean {
        val entry = synchronized(lock) { entries[id]?.takeIf { it.info.running }?.also { it.stopRequested = true } } ?: return false
        entry.process.signal(PosixSignal.TERM)
        scope.launch {
            delay(grace)
            if (synchronized(lock) { entry.info.running }) entry.process.signal(PosixSignal.KILL)
        }
        return true
    }

    fun stopAll() = synchronized(lock) { entries.keys.toList() }.forEach(::stop)

    /** An ended process leaves the list, and its log goes. */
    fun forget(id: String) {
        val forgotten = synchronized(lock) { entries[id]?.takeIf { !it.info.running }?.also { entries.remove(id) } }
        forgotten?.let { logs.remove(id) }
        publishNow()
    }

    /** Where [id]'s output is written, in the runtime: what its terminal tab follows. */
    fun logOf(id: String): String = logs.guestPath(id)

    /** Writes [text] to [id]'s input; null, or why it could not. */
    fun write(id: String, text: String): String? {
        val entry = synchronized(lock) { entries[id] } ?: return "There is no process $id"
        if (!entry.info.running) return "$id has ended"
        return try {
            entry.process.input.write(text.toByteArray(Charsets.UTF_8))
            entry.process.input.flush()
            null
        } catch (closed: IOException) {
            "$id no longer reads its input"
        }
    }

    /** [id] as it is now; null when there is no such process. Reading it moves nothing. */
    fun info(id: String): ManagedProcess? = synchronized(lock) { entries[id]?.info }

    /** [workspace]'s processes, oldest first. */
    fun of(workspace: String): List<ManagedProcess> = synchronized(lock) { entries.values.map { it.info }.filter { it.workspace == workspace } }

    /** [id], with what it printed since the assistant last read it; null when there is no such process. */
    fun read(id: String): Read? = synchronized(lock) {
        val entry = entries[id] ?: return null
        val (output, missed) = entry.output.since(entry.agentOffset)
        entry.agentOffset = entry.output.end
        Read(entry.info, output, missed)
    }

    /** Any is running: the runtime is not rebuilt, nor iCode's cache cleared, under it. */
    fun anyRunning(): Boolean = synchronized(lock) { entries.values.any { it.info.running } }

    /** Running in [workspace]: its rename and delete wait (WorkspaceUsage). */
    fun runsIn(workspace: String): Boolean = synchronized(lock) { entries.values.any { it.info.running && it.info.workspace == workspace } }

    fun handle(intent: ICodeIntent.ProcessIntent) {
        when (intent) {
            is ICodeIntent.StopProcess -> stop(intent.id)
            ICodeIntent.StopAllProcesses -> stopAll()
            is ICodeIntent.ForgetProcess -> forget(intent.id)
        }
    }

    /** A workspace renamed: what ended there is listed under its new location. */
    fun workspaceMoved(from: String, to: String) {
        synchronized(lock) { entries.values.filter { it.info.workspace == from }.forEach { it.info = it.info.copy(workspace = to) } }
        publishNow()
    }

    /** A workspace deleted: what ran there leaves the list, and its logs go. */
    fun workspaceForgotten(location: String) {
        val gone = synchronized(lock) {
            entries.values.filter { it.info.workspace == location && !it.info.running }.map { it.info.id }.onEach(entries::remove)
        }
        gone.forEach(logs::remove)
        publishNow()
    }

    /** Reads [entry]'s output as it comes, then records how it ended. */
    private fun follow(entry: Entry) = scope.launch(Dispatchers.IO) {
        runCatching {
            InputStreamReader(entry.process.output, Charsets.UTF_8).use { reader ->
                val buffer = CharArray(BUFFER)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    val chunk = String(buffer, 0, count)
                    val portsChanged = synchronized(lock) {
                        entry.output.append(chunk)
                        // Whole lines only: a line says whether its address is one the server listens on.
                        val text = entry.portLine + chunk
                        val ended = text.substringBeforeLast('\n', missingDelimiterValue = "")
                        entry.portLine = text.substringAfterLast('\n').takeLast(PORT_LINE)
                        val ports = (entry.info.ports + ProcessRules.ports(ended)).distinct().take(MAX_PORTS)
                        (ports != entry.info.ports).also { if (it) entry.info = entry.info.copy(ports = ports) }
                    }
                    logs.append(entry.info.id, chunk)
                    if (portsChanged) publishNow()
                }
            }
        }
        val code = runCatching { entry.process.waitFor() }.getOrDefault(EXIT_UNKNOWN)
        synchronized(lock) { entry.info = entry.info.copy(state = ManagedProcessState.Ended(code, clock(), entry.stopRequested)) }
        entry.process.close()
        publishNow()
    }

    private fun publishNow() {
        publish(synchronized(lock) { entries.values.map { it.info } })
    }

    companion object {
        const val GRACE_MILLIS = 3_000L
        private const val BUFFER = 8 * 1024
        private const val MAX_PORTS = 5

        /** How much of a line still being written is kept to find a port in, once it ends. */
        private const val PORT_LINE = 1_024

        /** The code a process whose end could not be read is given: the shell's "failed". */
        private const val EXIT_UNKNOWN = 1

        /** "uvicorn" for `uvicorn app.main:app`, "npm" for `PORT=3000 npm run dev`, "vite" for `./node_modules/.bin/vite`. */
        internal fun nameOf(command: String): String =
            command.trim().split(Regex("\\s+")).firstOrNull { !it.contains('=') }?.substringAfterLast('/')?.ifEmpty { null } ?: "process"
    }
}

/**
 * [launch] in a PRoot of its own that sees only its workspace, its stderr joined to its stdout as a
 * terminal shows them, its stdin open for [SpawnedProcess.input].
 */
internal fun RuntimeLayout.spawn(launch: RuntimeProcesses.Launch): SpawnedProcess {
    val process = RuntimeProcess.start(
        argv = prootCommand(
            guestCommand = listOf("/bin/bash", "-c", "exec 2>&1\n${launch.command}"),
            guestWorkingDirectory = launch.directory,
            killOnExit = true,
            workspace = launch.workspace,
        ),
        environment = prootEnvironment(launch.environment),
        workingDirectory = runtimeRoot.absolutePath,
    )
    return object : SpawnedProcess {
        override val output: InputStream = process.output
        override val input: OutputStream = process.input
        override fun signal(number: Int) = process.sendSignal(number)
        override fun waitFor(): Int = process.waitFor()
        override fun close() = process.destroy()
    }
}
