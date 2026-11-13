package dev.icode.mobile.data.runtime

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Commands run in a terminal tab as the user would type them (docs/agent-control-plan.md §4.2): the
 * tab's prompt waits empty; the command goes in as a paste, then Enter; its output is what its marks
 * bound; at its time limit comes Ctrl-C. The tabs are [terminals]' — the user's own sessions — so what
 * runs here shows in the Terminal panel as it runs, and the user can answer it or stop it there.
 */
internal class TerminalRuns(
    private val terminals: TerminalSessions,
    private val main: CoroutineContext = Dispatchers.Main.immediate,
    private val readyMillis: Long = READY_MILLIS,
) {
    /** Why a command was not run, in the words the model reads. */
    class Refused(message: String) : Exception(message)

    /** How a command ended: by itself, by the Ctrl-C at its limit, or not even then. */
    sealed interface Ran {
        val command: TerminalCommands.Command

        data class Ended(override val command: TerminalCommands.Command, val stopped: Boolean) : Ran

        data class StillRunning(override val command: TerminalCommands.Command) : Ran
    }

    /** A tab as the agent lists it: whose it is, where, and whether a command may be written now. */
    data class Tab(val id: String, val title: String, val chat: String?, val follows: String?, val workspace: String?, val state: String)

    /** Every tab, in the panel's order. */
    suspend fun tabs(): List<Tab> = withContext(main) {
        terminals.snapshot.value.sessions.map { session ->
            val commands = terminals.commands(session.id)
            val state = when {
                !session.isRunning || commands == null -> "ended"
                commands.running -> "running a command"
                commands.ready -> "ready"
                commands.integrated -> "something typed at its prompt"
                session.follows != null -> "showing a process's output"
                else -> "its shell says no command's end (not a bash tab iCode started), or it is starting"
            }
            Tab(session.id, session.title, session.assistant, session.follows, terminals.workspaceOf(session.id), state)
        }
    }

    /** [id]'s tab name while it runs; null for no such tab. */
    fun titleOf(id: String): String? = terminals.snapshot.value.sessions.firstOrNull { it.id == id && it.isRunning }?.title

    /** The chat whose tab [id] is; null for the user's own, or no such tab. */
    fun chatOf(id: String): String? = terminals.snapshot.value.sessions.firstOrNull { it.id == id }?.assistant

    /** [chat]'s own tab in [workspace]: its running one, unless [fresh], else a new one. Throws [Refused]. */
    suspend fun tabOf(workspace: String?, chat: String, fresh: Boolean): String =
        terminals.openAssistant(workspace, chat, fresh) ?: throw Refused("The Linux runtime is not installed")

    /** [chat]'s running tab, if it has one. */
    fun runningTabOf(chat: String): String? =
        terminals.snapshot.value.sessions.firstOrNull { it.assistant == chat && it.isRunning }?.id

    /** [chat]'s tabs closed, as the user closes one: the chat is deleted (docs/agent-control-plan.md §3 A4). */
    suspend fun close(chat: String) = withContext(main) {
        terminals.snapshot.value.sessions.filter { it.assistant == chat }.forEach { terminals.close(it.id) }
    }

    /** Where [chat]'s own tab's shell is now, as it last said; null without one. */
    fun directoryOf(chat: String): String? = runningTabOf(chat)?.let(terminals::commands)?.directory

    /**
     * Runs [command] in the tab [id], [onStarted] told when its output starts, and stops it with Ctrl-C
     * after [timeoutMillis]. A turn stopped while it runs stops it the same way. Throws [Refused].
     */
    suspend fun run(id: String, command: String, timeoutMillis: Long, onStarted: (TerminalCommands.Command) -> Unit = {}): Ran {
        val commands = ready(id)
        val next = commands.nextCommand()
        if (!type(id, PASTE_START + typed(command) + PASTE_END + ENTER)) throw Refused(ENDED)
        val started = withTimeoutOrNull(START_MILLIS) { next.await() } ?: run {
            // Not taken whole: an open quote or a here-document, and the shell waits for more.
            type(id, CTRL_C)
            throw Refused("The shell waited for more of the command — an open quote or a here-document? — and it was cleared with Ctrl-C")
        }
        onStarted(started)
        try {
            if (withTimeoutOrNull(timeoutMillis) { started.await() } != null || !started.running) return Ran.Ended(started, stopped = false)
        } catch (stopped: CancellationException) {
            withContext(NonCancellable) { type(id, CTRL_C) }
            throw stopped
        }
        type(id, CTRL_C)
        withTimeoutOrNull(STOP_MILLIS) { started.await() }
        return if (started.running) Ran.StillRunning(started) else Ran.Ended(started, stopped = true)
    }

    /** [text] typed into [id], then each of [keys] ([KEYS]' names). Throws [Refused]. */
    suspend fun send(id: String, text: String, keys: List<String>) {
        val sequences = keys.map { key -> KEYS[key.lowercase()] ?: throw Refused("$key is not a key this can press: ${KEYS.keys.joinToString()}") }
        if (!type(id, typed(text) + sequences.joinToString(""))) throw Refused(ENDED)
    }

    /** What [id] shows, its last [lines]. Throws [Refused]. */
    suspend fun read(id: String, lines: Int): String = withContext(main) { terminals.screen(id, lines) } ?: throw Refused(ENDED)

    /** [id]'s commands once its prompt waits empty, a new tab given time to start. Throws [Refused]. */
    private suspend fun ready(id: String): TerminalCommands {
        val commands = terminals.commands(id) ?: throw Refused(ENDED)
        withTimeoutOrNull(readyMillis) {
            while (!commands.ready) {
                if (terminals.commands(id) == null) throw Refused(ENDED)
                delay(POLL_MILLIS)
            }
        } ?: throw Refused(
            when {
                !commands.integrated -> "That tab's shell does not say where a command ends (only bash tabs iCode starts do): run it in your own tab instead"
                commands.running -> "A command is still running in that tab: wait for it, stop it with send_terminal_input ctrl-c, or use terminal \"new\""
                else -> "Something is typed at that tab's prompt: use your own tab, or terminal \"new\""
            },
        )
        return commands
    }

    private suspend fun type(id: String, text: String): Boolean = withContext(main) { terminals.write(id, text) }

    internal companion object {
        /** A new tab's shell reads the profile before its first prompt. */
        const val READY_MILLIS = 15_000L
        private const val START_MILLIS = 5_000L
        private const val STOP_MILLIS = 5_000L
        private const val POLL_MILLIS = 50L
        private const val ENDED = "That terminal tab has ended"
        private const val PASTE_START = "\u001B[200~"
        private const val PASTE_END = "\u001B[201~"
        private const val ENTER = "\r"
        private const val CTRL_C = "\u0003"

        /** The keys a tool may press, as a terminal sends them. */
        val KEYS = mapOf(
            "enter" to ENTER,
            "tab" to "\t",
            "esc" to "\u001B",
            "backspace" to "\u007F",
            "ctrl-c" to CTRL_C,
            "ctrl-d" to "\u0004",
            "ctrl-z" to "\u001A",
            "ctrl-l" to "\u000C",
            "up" to "\u001B[A",
            "down" to "\u001B[B",
            "right" to "\u001B[C",
            "left" to "\u001B[D",
            "page-up" to "\u001B[5~",
            "page-down" to "\u001B[6~",
        )

        /** What is typed: the text, without control characters but its new lines and tabs, so nothing ends a paste early. */
        fun typed(text: String): String = text.filter { it == '\n' || it == '\t' || it >= ' ' && it != '\u007F' }
    }
}
