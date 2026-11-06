package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.GuestEnvironment
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeProcesses
import dev.icode.mobile.data.runtime.TerminalRuns
import dev.icode.mobile.domain.ManagedProcess
import java.io.File

/**
 * Where a chat's commands and processes start (docs/agent-control-plan.md §4.2): the directory its own
 * terminal tab's shell is in, as the shell last said — so a `cd` there carries over, as in any
 * terminal — else the workspace's top. Its commands run in that tab ([TerminalRuns]); its processes
 * are managed ([RuntimeProcesses]), started from the same directory with the login environment.
 */
internal class AssistantShell(
    private val environment: GuestEnvironment,
    private val processes: RuntimeProcesses,
    private val terminals: TerminalRuns,
) {
    /** Starts [command] as a managed process, in [chat]'s directory, with the login environment. */
    suspend fun start(workspace: AssistantWorkspace, chat: String, command: String, name: String?): Pair<Result<ManagedProcess>, List<String>> {
        val (start, moved) = directory(workspace, chat)
        val login = environment.of(GuestContext.Command)
        val started = processes.start(workspace.place, start, command, name, login.variables + PROCESS_ENVIRONMENT)
        return started to listOfNotNull(moved, login.failure?.let(GuestEnvironment::withoutProfile))
    }

    /** The PATH a command is looked up in, as the login environment sets it. */
    suspend fun path(): String = environment.of(GuestContext.Command).variables["PATH"] ?: RuntimeLayout.GUEST_PATH

    /**
     * Where [chat]'s next command or process starts: its tab's directory, anywhere in the runtime, or
     * the workspace's top without a tab, or once that folder is gone — and then why.
     */
    fun directory(workspace: AssistantWorkspace, chat: String): Pair<String, String?> {
        val last = terminals.directoryOf(chatOf(workspace, chat)) ?: return workspace.guestPath to null
        if (isFolder(workspace, last)) return last to null
        return workspace.guestPath to "$last is gone: this ran at the workspace's top"
    }

    /**
     * What [command] reaches for [chat], as far as its text shows (docs/assistant-permissions-plan.md
     * §4.5): the directory it starts in, and each path it names ([CommandPaths]), from there.
     */
    fun reach(workspace: AssistantWorkspace, chat: String, command: String): List<File> {
        val start = directory(workspace, chat).first
        val named = CommandPaths.of(command).map { path ->
            when {
                path.startsWith('/') -> path
                path == "~" || path.startsWith("~/") -> RuntimeLayout.GUEST_HOME + path.removePrefix("~")
                else -> "$start/$path"
            }
        }
        return (listOf(start) + named).mapNotNull { runCatching { workspace.resolve(it) }.getOrNull() }
    }

    private fun isFolder(workspace: AssistantWorkspace, path: String): Boolean =
        runCatching { workspace.resolve(path).isDirectory }.getOrDefault(false)

    companion object {
        /** A chat as its terminal tabs know it: its workspace, and its ID there. */
        fun chatOf(workspace: AssistantWorkspace, chat: String): String = "${workspace.place}#$chat"

        /** Over the `command` context's own: a model reads a process's output, so no colour codes. */
        private val PROCESS_ENVIRONMENT = mapOf("NO_COLOR" to "1")
    }
}
