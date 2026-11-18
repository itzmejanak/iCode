package dev.icode.mobile.data.assistant.acp

import dev.icode.mobile.domain.AssistantExports
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession

/**
 * The conversation so far, as a new session with an agent is sent it (docs/assistant-and-mcp.md): whole
 * while it fits the agent's window, as [AssistantRules.nearFull] says of a model's; otherwise its newest
 * part in full after a summary of the rest, written by the chat's model — or, with none to write it, the
 * newest part alone, the agent told the rest was left out (owner's choice, 2026-11-18). Its size is
 * estimated at [CHARS_PER_TOKEN] characters a token; an agent whose window is not known yet gets it whole.
 */
internal object AcpHandover {
    /** What the agent is told of [history], for a window of [window] tokens; [summarise] writes the summary, or gives null. */
    suspend fun of(history: List<AssistantMessage>, window: Long?, summarise: suspend (List<AssistantMessage>) -> String?): String {
        val whole = transcript(history)
        if (window == null || !AssistantRules.nearFull(tokensOf(whole), window)) return whole
        val newest = newest(history, (window * AssistantRules.COMPACT_AT / 2).toLong())
        val older = history.dropLast(newest.size)
        val summary = if (older.isEmpty()) null else runCatching { summarise(older) }.getOrNull()?.takeIf(String::isNotBlank)
        return buildString {
            if (summary != null) append("A summary of the earlier part of this chat:\n\n").append(summary).append("\n\n")
            else if (older.isNotEmpty()) append(LEFT_OUT).append("\n\n")
            append(transcript(newest))
        }
    }

    /** The newest messages whose transcript fits [room] tokens, from something the user said; each measured once. */
    private fun newest(history: List<AssistantMessage>, room: Long): List<AssistantMessage> {
        var from = history.size
        var used = 0L
        while (from > 0) {
            used += tokensOf(transcript(listOf(history[from - 1])))
            if (used > room) break
            from--
        }
        val start = (from until history.size).firstOrNull { history[it] is AssistantMessage.User } ?: history.lastIndex
        return history.drop(start.coerceAtLeast(0))
    }

    private fun transcript(messages: List<AssistantMessage>): String = AssistantExports.transcript(AssistantSession("", "Chat", 0), "this place", messages)

    private fun tokensOf(text: String): Long = text.length.toLong() / CHARS_PER_TOKEN

    /** A rough share of text per token, for every agent's model alike. */
    const val CHARS_PER_TOKEN = 4

    const val LEFT_OUT = "Earlier messages in this chat were left out: the whole chat would not fit."
}
