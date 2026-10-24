package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.runtime.RuntimeProcesses
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.ManagedProcess
import dev.icode.mobile.domain.ManagedProcessState
import dev.icode.mobile.domain.ProcessRules
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Managed processes, as the assistant drives them (docs/runtime-foundation-plan.md §3.5): what must
// keep running — a server, a watcher, a worker — started, read, written to and stopped by its ID.

/** A port on this phone accepts a connection: something listens there. */
internal suspend fun localPortAnswers(port: Int): Boolean = withContext(Dispatchers.IO) {
    runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), CONNECT_MILLIS) } }.isSuccess
}

private const val CONNECT_MILLIS = 300

/** What the model reads of a process: its ID and name, its command, where and how it runs, and its ports. */
private suspend fun describe(process: ManagedProcess, answers: suspend (Int) -> Boolean, now: Long): String = buildString {
    append("${process.id} · ${process.name} · ${ProcessRules.stateLabel(process.state, now, process.startedAt)}")
    (process.state as? ManagedProcessState.Ended)?.let { append(" (exit code ${it.code}: ${ProcessRules.exitMeaning(it.code)})") }
    append("\n`${process.command}` in ${process.directory}")
    if (process.ports.isNotEmpty()) {
        val ports = process.ports.map { port -> "$port " + if (process.running && answers(port)) "(answers)" else "(does not answer)" }
        append("\nPorts its output names: ").append(ports.joinToString())
    }
}

/** Starts a process that keeps running; asked about first. */
internal class StartProcessTool(
    private val workspace: AssistantWorkspace,
    private val chat: String,
    private val shell: AssistantShell,
    private val processes: RuntimeProcesses,
    private val runtimeInstalled: () -> Boolean,
    private val answers: suspend (Int) -> Boolean = ::localPortAnswers,
    private val clock: () -> Long = System::currentTimeMillis,
) : AssistantTool {
    override val name = "start_process"
    override val description = "Starts a command that keeps running — a development server, a watcher, a worker — in bash, " +
        "where run_command is, with the login environment, seeing the whole runtime. It runs until stop_process, until it " +
        "ends, or until iCode's process ends; ${ProcessRules.MAX_RUNNING} at most at once. Its output (stderr with stdout) is kept " +
        "for process_status, and its input stays open for send_process_input. Waits wait_seconds (less if it ends), then " +
        "answers its ID, its state, the ports its output names and what it printed. A server must listen on a port from 1024; " +
        "the user opens http://127.0.0.1:<port> in their browser."
    override val parameters = listOf(
        AssistantParameter("command", "The command line, as typed in a terminal"),
        AssistantParameter("name", "A short name for it, shown to the user", required = false),
        AssistantParameter(
            "wait_seconds", "Seconds to wait before answering: default $DEFAULT_WAIT, at most $MAX_WAIT",
            AssistantParameterType.Integer, required = false,
        ),
    )
    override val kind = AssistantToolKind.Command

    override fun summary(arguments: JSONObject): String = "Start `${arguments.textOrNull("command").orEmpty().lineSequence().first()}`"

    override fun command(arguments: JSONObject): String? = arguments.textOrNull("command")

    override fun detail(arguments: JSONObject): String = arguments.textOrNull("command").orEmpty()

    override suspend fun run(arguments: JSONObject): String {
        val command = arguments.text("command").ifBlank { throw AssistantToolException("The command is empty") }
        if (!runtimeInstalled()) throw AssistantToolException("The Linux runtime is not installed")
        val wait = arguments.intOr("wait_seconds", DEFAULT_WAIT).coerceIn(0, MAX_WAIT)
        val (started, notes) = shell.start(workspace, chat, command, arguments.textOrNull("name"))
        val process = started.getOrElse { throw AssistantToolException(it.message ?: "Unable to start it") }
        // The time asked for, or its end, whichever comes first.
        for (poll in 0 until wait * POLLS_PER_SECOND) {
            if (processes.info(process.id)?.running != true) break
            delay(1_000L / POLLS_PER_SECOND)
        }
        val read = processes.read(process.id) ?: throw AssistantToolException("${process.id} is gone")
        return buildString {
            append("Started ").append(describe(read.process, answers, clock()))
            notes.forEach { append("\n").append(it) }
            append("\n\n").append(read.output.ifBlank { "No output yet" }.let { bounded(it, MAX_CHARACTERS) })
        }
    }

    private companion object {
        const val DEFAULT_WAIT = 5
        const val MAX_WAIT = 30
        const val POLLS_PER_SECOND = 5
        const val MAX_CHARACTERS = 16_000
    }
}

/** This workspace's processes, or one's state and its output since the assistant last looked: read-only. */
internal class ProcessStatusTool(
    private val workspace: AssistantWorkspace,
    private val processes: RuntimeProcesses,
    private val answers: suspend (Int) -> Boolean = ::localPortAnswers,
    private val clock: () -> Long = System::currentTimeMillis,
) : AssistantTool {
    override val name = "process_status"
    override val description = "Without an id, lists this workspace's processes started with start_process. With one, " +
        "answers its state, whether the ports its output names answer, and the output it printed since you last looked."
    override val parameters = listOf(AssistantParameter("id", "A process's ID, as start_process answered it", required = false))
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = arguments.textOrNull("id")?.let { "Check $it" } ?: "List processes"

    override suspend fun run(arguments: JSONObject): String {
        val id = arguments.textOrNull("id")
        if (id == null) {
            val mine = processes.of(workspace.root.path)
            if (mine.isEmpty()) return "No processes in this workspace"
            return mine.map { describe(it, answers, clock()) }.joinToString("\n\n")
        }
        val read = processes.read(id)?.takeIf { it.process.workspace == workspace.root.path }
            ?: throw AssistantToolException("There is no process $id in this workspace")
        return buildString {
            append(describe(read.process, answers, clock()))
            append("\n\n")
            if (read.missed) append("(Earlier output was dropped: only the last of it is kept.)\n")
            append(read.output.ifBlank { "No new output" }.let { bounded(it, MAX_CHARACTERS) })
        }
    }

    private companion object {
        const val MAX_CHARACTERS = 16_000
    }
}

/** Writes to a running process's input; asked about first. */
internal class SendProcessInputTool(
    private val workspace: AssistantWorkspace,
    private val processes: RuntimeProcesses,
) : AssistantTool {
    override val name = "send_process_input"
    override val description = "Writes text to the input of a process started with start_process — an answer to its " +
        "prompt, a line for its console — then answers what it printed in the next second."
    override val parameters = listOf(
        AssistantParameter("id", "The process's ID"),
        AssistantParameter("text", "What to write"),
        AssistantParameter("newline", "End it with a newline, as Enter does: default true", AssistantParameterType.Boolean, required = false),
    )
    override val kind = AssistantToolKind.Command

    override fun summary(arguments: JSONObject): String = "Send input to ${arguments.textOrNull("id").orEmpty()}"

    override fun detail(arguments: JSONObject): String = arguments.textOrNull("text").orEmpty()

    override suspend fun run(arguments: JSONObject): String {
        val id = arguments.text("id")
        if (processes.of(workspace.root.path).none { it.id == id }) throw AssistantToolException("There is no process $id in this workspace")
        val newline = !arguments.has("newline") || arguments.flag("newline")
        val text = arguments.text("text") + if (newline) "\n" else ""
        processes.write(id, text)?.let { throw AssistantToolException(it) }
        delay(REPLY_MILLIS)
        val read = processes.read(id) ?: return "Sent"
        return "Sent to $id. " + read.output.ifBlank { "It printed nothing in the next second" }.let { bounded(it, MAX_CHARACTERS) }
    }

    private companion object {
        const val REPLY_MILLIS = 1_000L
        const val MAX_CHARACTERS = 16_000
    }
}

/** Stops a process; asked about first. */
internal class StopProcessTool(
    private val workspace: AssistantWorkspace,
    private val processes: RuntimeProcesses,
    private val clock: () -> Long = System::currentTimeMillis,
) : AssistantTool {
    override val name = "stop_process"
    override val description = "Stops a process started with start_process: SIGTERM, then SIGKILL if it is still running after " +
        "${RuntimeProcesses.GRACE_MILLIS / 1000} s, with everything it started."
    override val parameters = listOf(AssistantParameter("id", "The process's ID"))
    override val kind = AssistantToolKind.Command

    override fun summary(arguments: JSONObject): String = "Stop ${arguments.textOrNull("id").orEmpty()}"

    override suspend fun run(arguments: JSONObject): String {
        val id = arguments.text("id")
        val process = processes.of(workspace.root.path).firstOrNull { it.id == id }
            ?: throw AssistantToolException("There is no process $id in this workspace")
        if (!process.running) return "$id had ended already: ${ProcessRules.stateLabel(process.state, clock(), process.startedAt)}"
        processes.stop(id)
        // Its end, within the grace and a moment more.
        for (poll in 0 until ((RuntimeProcesses.GRACE_MILLIS + EXTRA_MILLIS) / POLL_MILLIS).toInt()) {
            if (processes.info(id)?.running != true) return "Stopped $id"
            delay(POLL_MILLIS)
        }
        return "Asked $id to stop; it is still ending"
    }

    private companion object {
        const val POLL_MILLIS = 200L
        const val EXTRA_MILLIS = 1_000L
    }
}
