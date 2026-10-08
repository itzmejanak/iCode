package dev.icode.mobile.domain

/**
 * A chat as it is shared (§12 R9 — export): the transcript a session is shared by, mirroring the
 * page itself — what was said, what the assistant answered, and each step with what it did and the
 * output that followed. Text only, bounded: a chat holds a command's whole output, and a share
 * intent carries a limited amount.
 */
object AssistantExports {
    /** A share intent's bound; above it, the transcript ends with what was cut off. */
    const val MAX_CHARS = 100_000

    /** A session's transcript, as the page shows it: said, thinking, answered, each step and the compaction its read starts at. */
    fun transcript(session: AssistantSession, workspace: String, messages: List<AssistantMessage>): String {
        val builder = StringBuilder()
        builder.appendLine("# ${session.title}")
        builder.appendLine()
        builder.appendLine("Worked in $workspace")
        builder.appendLine()
        messages.forEachIndexed { index, message ->
            when (message) {
                is AssistantMessage.User -> user(builder, message)
                is AssistantMessage.Reply -> reply(builder, message, resultsOf(messages, index))
                is AssistantMessage.Results -> Unit
                is AssistantMessage.Compacted -> compacted(builder, message)
            }
        }
        return bounded(builder.toString())
    }

    private fun user(out: StringBuilder, message: AssistantMessage.User) {
        out.appendLine("## You")
        out.appendLine()
        if (message.text.isNotBlank()) out.appendLine(message.text.trim())
        message.attachments.takeIf { it.isNotEmpty() }?.let { attachments ->
            if (message.text.isNotBlank()) out.appendLine()
            out.appendLine("_Attached:_ " + attachments.joinToString(", ") { it.name })
        }
        out.appendLine()
    }

    private fun reply(out: StringBuilder, message: AssistantMessage.Reply, results: List<AssistantResult>) {
        if (message.reasoning.isNotBlank()) {
            out.appendLine("## Thinking")
            out.appendLine()
            message.reasoning.trim().lineSequence().forEach { out.appendLine("> $it") }
            out.appendLine()
        }
        if (message.text.isNotBlank()) {
            out.appendLine("## Assistant")
            out.appendLine()
            out.appendLine(message.text.trim())
            out.appendLine()
        }
        message.calls.forEach { call -> step(out, call, results.firstOrNull { it.callId == call.id }) }
    }

    /** One step: what it did, the command line where it ran one, the output that came back, the lines it changed. */
    private fun step(out: StringBuilder, call: AssistantCall, result: AssistantResult?) {
        out.appendLine("### ${call.summary}")
        out.appendLine()
        call.command?.takeIf { it.isNotBlank() }?.let {
            out.appendLine("```sh")
            out.appendLine(it)
            out.appendLine("```")
            out.appendLine()
        }
        result?.let { answered ->
            answered.output.takeIf { it.isNotBlank() }?.let { output ->
                out.appendLine("```")
                out.appendLine(output.let(::trimmed))
                out.appendLine("```")
                out.appendLine()
            }
            answered.changes?.let { changes ->
                out.appendLine("_${changes.label}_")
                out.appendLine()
            }
        }
    }

    private fun compacted(out: StringBuilder, message: AssistantMessage.Compacted) {
        out.appendLine("> **Compacted:** ${message.summary.trim()}")
        out.appendLine()
    }

    /** A tool's output, trimmed around: a trailing blank line is its printing, not its words. */
    private fun trimmed(output: String): String = output.trim('\n')

    /** Beyond a share's bound: keep the head, say what was left out. */
    private fun bounded(text: String): String = when {
        text.length <= MAX_CHARS -> text
        else -> text.take(MAX_CHARS).trimEnd() + "\n\n…cut off here: the chat continues past the first $MAX_CHARS characters."
    }

    /** The results of the reply at [index]: those right after it, matched there as the page does. */
    private fun resultsOf(messages: List<AssistantMessage>, index: Int): List<AssistantResult> =
        (messages.getOrNull(index + 1) as? AssistantMessage.Results)?.results.orEmpty()
}