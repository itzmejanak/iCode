package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.GuestEnvironment
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeProcesses
import dev.icode.mobile.data.runtime.shellQuote
import dev.icode.mobile.domain.ManagedProcess
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where and how the assistant's commands run (docs/runtime-foundation-plan.md §3.3): bash with the
 * user's login environment, seeing only its workspace, in the directory its chat's last command
 * ended in. A command's `export`s end with it; its directory does not.
 */
internal class AssistantShell(
    /** The runtime's root filesystem: its `/tmp` takes the file a command leaves its directory in. */
    private val rootfs: File,
    /** Runs bash's [script] seeing only the workspace at its location, each line of output to the callback. */
    private val bash: suspend (workspace: String, script: String, environment: Map<String, String>, onLine: (String) -> Unit) -> GuestCommand.Result,
    private val environment: GuestEnvironment,
    private val processes: RuntimeProcesses,
) {
    constructor(rootfs: File, guest: GuestCommand, environment: GuestEnvironment, processes: RuntimeProcesses) : this(
        rootfs,
        { workspace, script, variables, onLine ->
            guest.stream(workspace, listOf("/bin/bash", "-c", script), "assistant", variables, workspace = workspace) { line ->
                onLine(line)
                true
            }
        },
        environment,
        processes,
    )

    /** A command's end: its exit code (null when it was stopped at its time limit), where it ended, and what to say of how it ran. */
    data class Ran(val exitCode: Int?, val stderr: String, val directory: String, val notes: List<String>, val path: String)

    /** Each chat's directory in the runtime, by workspace and chat. */
    private val directories = ConcurrentHashMap<String, String>()

    /** Runs [command] for [chat] for at most [seconds], each line of its output to [onLine]. */
    suspend fun run(workspace: AssistantWorkspace, chat: String, command: String, seconds: Int, onLine: (String) -> Unit): Ran {
        val (start, moved) = directory(workspace, chat)
        val login = environment.current()
        val variables = login.variables + COMMAND_ENVIRONMENT
        val temp = File(rootfs, "tmp").apply { mkdirs() }
        val ended = File.createTempFile("icode-cwd-", "", temp)
        try {
            // The trap runs on every way out but a kill, `exit` included; the command's own status stands.
            // Its physical directory: a symlink's target is where it ended, inside the workspace or not.
            val script = "cd -- ${shellQuote(start)} || exit 1\ntrap 'pwd -P > /tmp/${ended.name}' EXIT\n{ $command\n} </dev/null"
            val result = withTimeoutOrNull(seconds * 1_000L) { bash(workspace.root.path, script, variables, onLine) }
            val now = ended.readText().trim().takeIf { it.startsWith("/") }
            if (result != null && now != null) directories[keyOf(workspace, chat)] = now
            return Ran(
                exitCode = result?.exitCode,
                stderr = result?.stderr.orEmpty(),
                directory = directories[keyOf(workspace, chat)] ?: start,
                notes = listOfNotNull(moved, login.failure?.let(::environmentNote)),
                path = variables["PATH"] ?: DEFAULT_PATH,
            )
        } finally {
            ended.delete()
        }
    }

    /** Starts [command] as a managed process, in [chat]'s directory, with the login environment. */
    suspend fun start(workspace: AssistantWorkspace, chat: String, command: String, name: String?): Pair<Result<ManagedProcess>, List<String>> {
        val (start, moved) = directory(workspace, chat)
        val login = environment.current()
        val started = processes.start(workspace.root.path, start, command, name, login.variables + COMMAND_ENVIRONMENT)
        return started to listOfNotNull(moved, login.failure?.let(::environmentNote))
    }

    /**
     * Where [chat]'s next command starts: where its last one ended, or the workspace's top when that
     * is outside the workspace or gone — and then why.
     */
    fun directory(workspace: AssistantWorkspace, chat: String): Pair<String, String?> {
        val top = workspace.guestPath
        val last = directories[keyOf(workspace, chat)] ?: return top to null
        val inside = last == top || last.startsWith("$top/")
        val reason = when {
            !inside -> "The last command ended in $last, outside this workspace: this one ran at its top"
            !File(workspace.root, last.removePrefix(top).removePrefix("/")).isDirectory -> "$last is gone: this ran at the workspace's top"
            else -> return last to null
        }
        directories.remove(keyOf(workspace, chat))
        return top to reason
    }

    private fun keyOf(workspace: AssistantWorkspace, chat: String) = "${workspace.root.path}\u0000$chat"

    private fun environmentNote(failure: String) =
        "Your login environment could not be read ($failure): this ran with iCode's default environment"

    companion object {
        /** No pager, prompt or colour codes: the output is read, not watched. */
        val COMMAND_ENVIRONMENT = mapOf(
            "PAGER" to "cat",
            "GIT_PAGER" to "cat",
            "GIT_TERMINAL_PROMPT" to "0",
            "DEBIAN_FRONTEND" to "noninteractive",
            "NO_COLOR" to "1",
            "TERM" to "dumb",
        )

        /** The search path a command gets when the login environment gives none. */
        val DEFAULT_PATH = RuntimeLayout.GUEST_PATH
    }
}
