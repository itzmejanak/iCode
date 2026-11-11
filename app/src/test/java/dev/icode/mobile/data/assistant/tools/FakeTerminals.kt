package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.runtime.FollowedOutput
import dev.icode.mobile.data.runtime.LongCommandEnded
import dev.icode.mobile.data.runtime.TerminalCommands
import dev.icode.mobile.data.runtime.TerminalEnding
import dev.icode.mobile.data.runtime.TerminalSessions
import dev.icode.mobile.domain.TerminalSessionState
import dev.icode.mobile.domain.TerminalSettings
import dev.icode.mobile.domain.TerminalSnapshot
import dev.icode.mobile.domain.TerminalStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Terminal tabs whose bash answers as iCode's does (docs/agent-control-plan.md §4.2): a prompt with its
 * directory (OSC 7) and A and B marks; a pasted command and Enter run as [answer] says — its output,
 * code and directory, between C and D, then the next prompt — or, [Answer.hang] for it, until Ctrl-C
 * ends it with 130, unless it does not [Answer.stop] at Ctrl-C.
 */
internal class FakeTerminals(private val directory: String = "/workspace/app") : TerminalSessions {
    data class Answer(val output: String = "", val code: Int = 0, val directory: String? = null, val hang: Boolean = false, val stop: Boolean = true)

    var answer: (String) -> Answer = { Answer() }
    val typed = mutableListOf<String>()
    val opened = mutableListOf<String>()
    private val records = mutableMapOf<String, TerminalCommands>()
    private val workspaces = mutableMapOf<String, String>()
    private val directories = mutableMapOf<String, String>()
    private var hanging: Pair<String, Answer>? = null
    private var next = 1

    override val snapshot = MutableStateFlow(TerminalSnapshot())
    override val endings = MutableSharedFlow<TerminalEnding>()
    override val commandsFinished = MutableSharedFlow<String>()
    override val longCommandsEnded = MutableSharedFlow<LongCommandEnded>()
    override val lookingAt = MutableStateFlow<String?>(null)

    /** A tab of the user's, at a prompt; [marks] false for a shell that prints none. */
    fun userTab(title: String, marks: Boolean = true): String = add(title, null, "/w/app", marks)

    override suspend fun openAssistant(workspaceHostPath: String, chat: String, fresh: Boolean): String {
        if (!fresh) snapshot.value.sessions.firstOrNull { it.assistant == chat && it.isRunning }?.let { return it.id }
        val count = snapshot.value.sessions.count { it.assistant != null }
        return add(if (count == 0) "Assistant" else "Assistant ${count + 1}", chat, workspaceHostPath, true).also { opened += it }
    }

    private fun add(title: String, chat: String?, workspace: String, marks: Boolean): String {
        val id = "session-${next++}"
        records[id] = TerminalCommands()
        workspaces[id] = workspace
        directories[id] = directory
        snapshot.value = snapshot.value.copy(sessions = snapshot.value.sessions + TerminalSessionState(id, title, TerminalStatus.Running, assistant = chat))
        // As bash's first PROMPT_COMMAND: where it is, and a D no command started.
        if (marks) print(id, prompt(id, 0)) else print(id, "% ")
        return id
    }

    fun end(id: String) {
        records.remove(id)?.close()
        snapshot.value = snapshot.value.copy(sessions = snapshot.value.sessions.filterNot { it.id == id })
    }

    override fun commands(id: String): TerminalCommands? = records[id]

    override fun write(id: String, text: String): Boolean {
        if (id !in records) return false
        typed += text
        when {
            text == CTRL_C && hanging?.first == id -> if (hanging!!.second.stop) {
                hanging = null
                finish(id, Answer(output = "^C\r\n", code = 130))
            } else {
                print(id, "^C")
            }
            text.startsWith(PASTE_START) && text.endsWith(PASTE_END + "\r") -> {
                val command = text.removePrefix(PASTE_START).removeSuffix(PASTE_END + "\r")
                print(id, command.replace("\n", "\r\n") + "\r\n$OSC_C")
                val answer = answer(command)
                if (answer.hang) {
                    hanging = id to answer
                    print(id, answer.output)
                } else {
                    finish(id, answer)
                }
            }
            else -> print(id, text)
        }
        return true
    }

    private fun finish(id: String, answer: Answer) {
        answer.directory?.let { directories[id] = it }
        print(id, answer.output + prompt(id, answer.code))
    }

    private fun prompt(id: String, code: Int) = "\u001B]7;file://localhost${directories[id]}\u0007\u001B]133;D;$code\u0007$OSC_A$ $OSC_B"

    private fun print(id: String, text: String) {
        val bytes = text.toByteArray()
        records[id]?.feed(bytes, bytes.size)
        screens[id] = (screens[id].orEmpty() + text)
    }

    private val screens = mutableMapOf<String, String>()

    override fun screen(id: String, lines: Int): String? =
        screens[id]?.replace(Regex("\u001B\\][^\u0007]*\u0007"), "")?.lines()?.takeLast(lines)?.joinToString("\n")

    override fun workspaceOf(id: String): String? = workspaces[id]
    override suspend fun open(workspaceHostPath: String?, reuseExisting: Boolean): String? = null
    override suspend fun follow(output: FollowedOutput): String? = null
    override fun select(id: String) = Unit
    override fun reorder(movingId: String, targetId: String) = Unit
    override fun close(id: String) = end(id)
    override fun applySettings(next: TerminalSettings) = Unit

    companion object {
        const val OSC_A = "\u001B]133;A\u0007"
        const val OSC_B = "\u001B]133;B\u0007"
        const val OSC_C = "\u001B]133;C\u0007"
        const val PASTE_START = "\u001B[200~"
        const val PASTE_END = "\u001B[201~"
        const val CTRL_C = "\u0003"
    }
}
