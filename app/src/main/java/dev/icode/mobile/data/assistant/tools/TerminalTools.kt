package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantPlaceActor
import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.runtime.TerminalRuns
import dev.icode.mobile.domain.AssistantElsewhere
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantToolName
import dev.icode.mobile.domain.ProcessRules
import java.io.File
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

// The terminal for the assistant (docs/assistant-and-mcp.md): its commands run in the user's own
// terminal tabs — the chat's own, a new one, or one of the user's, asked about first — through the
// TerminalRuns the Terminal panel's sessions are; the user sees each as it runs and can answer it.

/** Where a call's `terminal` argument points: the chat's own tab, a new one of its own, or a named tab. */
private class TerminalTarget(private val workspace: AssistantWorkspace, chat: String, private val terminals: TerminalRuns) {
    val chat = AssistantShell.chatOf(workspace, chat)

    /** A tab of the user's — or another chat's — that [arguments] name: a place the gate asks about; null for the chat's own. */
    fun place(arguments: JSONObject, key: String): AssistantElsewhere? {
        val id = arguments.textOrNull(key)?.trim()?.takeIf { it.isNotEmpty() && it != NEW } ?: return null
        if (terminals.chatOf(id) == chat) return null
        val title = terminals.titleOf(id) ?: return null
        return AssistantElsewhere(title, AssistantPermissions.terminalRoot(id), AssistantElsewhere.Kind.Terminal)
    }

    /** The tab [named] names, the chat's own when null, a new one of its own for "new". Throws [AssistantToolException]. */
    suspend fun resolve(named: String?): String = try {
        when (val id = named?.trim()?.takeIf(String::isNotEmpty)) {
            null -> terminals.tabOf(workspace.place, chat, fresh = false)
            NEW -> terminals.tabOf(workspace.place, chat, fresh = true)
            else -> id.takeIf { terminals.titleOf(it) != null } ?: throw AssistantToolException("There is no terminal tab $id running: list_terminals lists them")
        }
    } catch (refused: TerminalRuns.Refused) {
        throw AssistantToolException(refused.message ?: "No terminal")
    }

    companion object {
        const val NEW = "new"
    }
}

/** A command in a terminal tab, as typed at its prompt; asked about first, as a command. */
internal class RunCommandTool(
    private val workspace: AssistantWorkspace,
    private val chat: String,
    private val shell: AssistantShell,
    private val terminals: TerminalRuns,
    private val step: AssistantStep,
    private val runtimeInstalled: () -> Boolean,
    /** The command may have changed files: the workspace refreshes as after a terminal command. */
    private val changed: () -> Unit,
) : AssistantTool, AssistantPlaceActor {
    private val target = TerminalTarget(workspace, chat, terminals)

    override val name = AssistantToolName.RunCommand.id
    override val description = "Runs a command in a terminal tab of iCode's Terminal, as typed at its bash prompt, and answers its exit " +
        "code, the directory it ended in and its output. By default it runs in this chat's own tab, opened in the workspace on " +
        "first use: the user watches it there and can answer a prompt it shows; `cd` and `export` carry over to the next command, " +
        "as in any terminal. terminal \"new\" opens another tab of this chat's, for a command while one still runs; a tab's id " +
        "from list_terminals runs it there (the user is asked before one of theirs is used). It runs in the Ubuntu runtime " +
        "(ARM64, under PRoot: uid 0 without root's powers, no systemd) with the user's login environment, and nothing pages. " +
        "After timeout_seconds it gets Ctrl-C. Run a server, a watcher or anything that must keep running with start_process. " +
        "A command that waits for input is answered with send_terminal_input."
    override val parameters = listOf(
        AssistantParameter("command", "The command line, as typed in a terminal"),
        AssistantParameter("timeout_seconds", "Ctrl-C after this many seconds: default $DEFAULT_TIMEOUT", AssistantParameterType.Integer, required = false),
        AssistantParameter("terminal", "Where: omitted, this chat's own tab; \"new\", another of its own; or a tab's id from list_terminals", required = false),
    )
    override val kind = AssistantToolKind.Command

    override fun summary(arguments: JSONObject): String = "Run `${arguments.textOrNull("command").orEmpty().lineSequence().first()}`"

    override fun command(arguments: JSONObject): String? = arguments.textOrNull("command")

    override fun reach(arguments: JSONObject): List<File> = arguments.textOrNull("command")?.let { shell.reach(workspace, chat, it) }.orEmpty()

    override fun detail(arguments: JSONObject): String = arguments.textOrNull("command").orEmpty()

    override fun place(arguments: JSONObject): AssistantElsewhere? = target.place(arguments, "terminal")

    /** In the chat's own tab, a command asks as a command does. */
    override val placeless get() = kind

    override suspend fun run(arguments: JSONObject): String {
        val command = arguments.text("command").ifBlank { throw AssistantToolException("The command is empty") }
        if (!runtimeInstalled()) throw AssistantToolException("The Linux runtime is not installed")
        val seconds = arguments.intOr("timeout_seconds", DEFAULT_TIMEOUT).coerceAtLeast(1)
        val id = target.resolve(arguments.textOrNull("terminal"))
        val callId = step.current?.id
        val ran = try {
            terminals.run(id, command, seconds * 1_000L) { if (callId != null) step.terminal(callId, id) }
        } catch (refused: TerminalRuns.Refused) {
            throw AssistantToolException(refused.message ?: "It did not run")
        } finally {
            changed()
        }
        val printed = ran.command
        if (callId != null) step.leavePrinted(callId, printed.printed())
        return buildString {
            val code = printed.exitCode
            when {
                ran is TerminalRuns.Ran.StillRunning -> append("Still running after Ctrl-C at $seconds s: read it with read_terminal, answer or stop it with send_terminal_input")
                ran is TerminalRuns.Ran.Ended && ran.stopped -> append("Stopped with Ctrl-C at $seconds s").append(code?.let { ": exit code $it (${ProcessRules.exitMeaning(it)})" }.orEmpty())
                code == null -> append("It ended, and its shell gave no exit code")
                else -> append("Exit code $code (${ProcessRules.exitMeaning(code)})")
            }
            printed.directory?.let { append("\nin ").append(it) }
            append("\nterminal ").append(terminals.titleOf(id) ?: "tab").append(" (").append(id).append(")")
            if (code == 127) append("\nPATH: ").append(shell.path())
            printed.text().trimEnd().takeIf(String::isNotEmpty)?.let { append("\n\n").append(bounded(it, MAX_CHARACTERS)) }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT = 120
        const val MAX_CHARACTERS = 32_000
    }
}

/** The terminal's tabs, whose and in what state: read-only. */
internal class ListTerminalsTool(private val workspace: AssistantWorkspace, chat: String, private val terminals: TerminalRuns) : AssistantTool {
    private val chat = AssistantShell.chatOf(workspace, chat)

    override val name = AssistantToolName.ListTerminals.id
    override val description = "Lists the tabs of iCode's Terminal: each tab's id, its name, whose it is (this chat's, another chat's, the " +
        "user's, or a process's output), its workspace, and whether a command can be run in it now."
    override val parameters = emptyList<AssistantParameter>()
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "List the terminals"

    override suspend fun run(arguments: JSONObject): String {
        val tabs = terminals.tabs()
        if (tabs.isEmpty()) return "No terminal tab is open. run_command opens this chat's own."
        return tabs.joinToString("\n") { tab ->
            val whose = when {
                tab.chat == chat -> "this chat's"
                tab.chat != null -> "another chat's"
                tab.follows != null -> "a process's output"
                else -> "the user's"
            }
            listOfNotNull(tab.id, tab.title, whose, tab.workspace?.let { "in ${File(it).name}" }, tab.state).joinToString(" · ")
        }
    }
}

/** What a tab shows now: read-only, but a tab of the user's is asked about first. */
internal class ReadTerminalTool(workspace: AssistantWorkspace, chat: String, private val terminals: TerminalRuns) : AssistantTool, AssistantPlaceActor {
    private val target = TerminalTarget(workspace, chat, terminals)

    override val name = AssistantToolName.ReadTerminal.id
    override val description = "Reads what a terminal tab shows now: its last lines of screen and scrollback, as text. Use it for a command " +
        "still running, a prompt waiting, or what the user ran."
    override val parameters = listOf(
        AssistantParameter("id", "The tab's id, from list_terminals or run_command's answer"),
        AssistantParameter("lines", "How many of its last lines: default $DEFAULT_LINES, at most $MAX_LINES", AssistantParameterType.Integer, required = false),
    )
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "Read terminal ${terminals.titleOf(arguments.textOrNull("id").orEmpty()) ?: arguments.textOrNull("id").orEmpty()}"

    override fun place(arguments: JSONObject): AssistantElsewhere? = target.place(arguments, "id")

    override suspend fun run(arguments: JSONObject): String {
        val id = target.resolve(arguments.text("id").ifBlank { throw AssistantToolException("Name the tab: its id, from list_terminals") })
        val lines = arguments.intOr("lines", DEFAULT_LINES).coerceIn(1, MAX_LINES)
        val text = try {
            terminals.read(id, lines)
        } catch (refused: TerminalRuns.Refused) {
            throw AssistantToolException(refused.message ?: "It could not be read")
        }
        return bounded(text.ifBlank { "Nothing on its screen" }, MAX_CHARACTERS)
    }

    private companion object {
        const val DEFAULT_LINES = 100
        const val MAX_LINES = 2_000
        const val MAX_CHARACTERS = 32_000
    }
}

/** Text and keys typed into a tab; asked about first, as a command, and a tab of the user's as a place. */
internal class SendTerminalInputTool(workspace: AssistantWorkspace, chat: String, private val terminals: TerminalRuns) : AssistantTool, AssistantPlaceActor {
    private val target = TerminalTarget(workspace, chat, terminals)

    override val name = AssistantToolName.SendTerminalInput.id
    override val description = "Types into a terminal tab: text, then keys — ${TerminalRuns.KEYS.keys.joinToString()} — as the user would: " +
        "an answer to a prompt (text \"y\", keys [\"enter\"]), Ctrl-C to stop what runs. Answers what the tab shows a moment later."
    override val parameters = listOf(
        AssistantParameter("id", "The tab's id, from list_terminals or run_command's answer"),
        AssistantParameter("text", "What to type, as it is", required = false),
        AssistantParameter("keys", "Keys to press after it, in order", AssistantParameterType.Items(AssistantParameterType.Text), required = false),
    )
    override val kind = AssistantToolKind.Command

    override fun summary(arguments: JSONObject): String {
        val what = listOfNotNull(arguments.textOrNull("text")?.let { "“${it.lineSequence().first()}”" }, keys(arguments).joinToString(" ").ifEmpty { null })
        return "Type ${what.joinToString(", then ").ifEmpty { "nothing" }}"
    }

    override fun place(arguments: JSONObject): AssistantElsewhere? = target.place(arguments, "id")

    /** In the chat's own tab, what is typed asks as a command does. */
    override val placeless get() = kind

    override suspend fun run(arguments: JSONObject): String {
        val id = target.resolve(arguments.text("id").ifBlank { throw AssistantToolException("Name the tab: its id, from list_terminals") })
        val text = arguments.textOrNull("text").orEmpty()
        val keys = keys(arguments)
        if (text.isEmpty() && keys.isEmpty()) throw AssistantToolException("Give text, keys or both")
        return try {
            terminals.send(id, text, keys)
            delay(SETTLE_MILLIS)
            "Typed into ${terminals.titleOf(id) ?: id}. It shows:\n\n" + bounded(terminals.read(id, SHOWN_LINES).ifBlank { "Nothing on its screen" }, MAX_CHARACTERS)
        } catch (refused: TerminalRuns.Refused) {
            throw AssistantToolException(refused.message ?: "Nothing was typed")
        }
    }

    private fun keys(arguments: JSONObject): List<String> =
        (arguments.opt("keys") as? JSONArray)?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty().filter(String::isNotBlank)

    private companion object {
        /** A moment for what was typed to be answered. */
        const val SETTLE_MILLIS = 500L
        const val SHOWN_LINES = 30
        const val MAX_CHARACTERS = 16_000
    }
}
