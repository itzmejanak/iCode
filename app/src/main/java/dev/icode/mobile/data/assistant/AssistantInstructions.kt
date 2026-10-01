package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantMode
import java.io.File

/**
 * iCode's words to the model: a turn's system prompt — who the assistant is, where it works, the
 * project's own notes, and the summary of a compacted chat — and a message with its files.
 */
internal object AssistantInstructions {
    /** What the model is asked for when a chat is compacted (§12 R4). */
    const val COMPACT_REQUEST =
        "Summarize this conversation so far, with any earlier summary of it, for yourself to continue it from " +
            "the summary alone: what the user wants, what was decided, the files read and changed and what changed " +
            "in them, commands run and their outcome, and what is still open. Keep paths, names and exact values. " +
            "Write only the summary."

    fun of(workspace: AssistantWorkspace, mode: AssistantMode, summary: String?): String = buildString {
        appendLine("You are the assistant built into iCode, a code editor on an Android phone.")
        appendLine(
            "You work in the workspace “${workspace.name}”. Its files are at ${workspace.guestPath} in an Ubuntu " +
                "Linux runtime (ARM64, under PRoot: no root, no systemd; `sudo apt` installs packages). " +
                "Tool paths are relative to that folder.",
        )
        appendLine()
        appendLine("How you work:")
        when (mode) {
            AssistantMode.Agent -> {
                appendLine("- Look before you change: list, search and read what a change touches.")
                appendLine("- Change an existing file with edit_file; write a new one, or a rewrite, with write_file.")
                appendLine("- Keep to what was asked. Run the project's own quick checks (tests, a build, a linter) after a change when it has them.")
                appendLine("- The user may refuse a change or a command: then stop, and say what you would have done.")
            }
            AssistantMode.Ask -> {
                appendLine("- The user chose Ask: you can list, search and read the workspace, but not change it or run commands.")
                appendLine("- Answer from what you read; show a change you suggest as a diff or code block, for the user to make.")
            }
        }
        appendLine("- Never say you ran or checked something you did not.")
        appendLine("- The user reads you on a phone: answer briefly, in Markdown, naming files by their paths.")
        projectNotes(workspace.root)?.let { notes ->
            appendLine()
            appendLine("The project's own instructions, from its AGENTS.md:")
            appendLine(notes)
        }
        summary?.let {
            appendLine()
            appendLine("This chat was compacted. Your summary of it until then:")
            appendLine(it)
        }
    }

    /** [message] as the model reads it: the files the user attached, then what they wrote. */
    fun message(message: AssistantMessage.User): String {
        if (message.attachments.isEmpty()) return message.text
        return buildString {
            appendLine("Files the user attached, as they were when sent:")
            message.attachments.forEach { file ->
                appendLine()
                appendLine("<file path=\"${file.path}\">")
                appendLine(file.content.trimEnd())
                appendLine("</file>")
            }
            appendLine()
            append(message.text)
        }
    }

    /** AGENTS.md at the workspace's top — the convention agents read — up to [MAX_NOTES] characters. */
    private fun projectNotes(root: File): String? {
        val file = File(root, "AGENTS.md").takeIf { it.isFile } ?: return null
        return runCatching {
            file.bufferedReader().use { reader ->
                val buffer = CharArray(MAX_NOTES)
                var read = 0
                while (read < MAX_NOTES) {
                    val count = reader.read(buffer, read, MAX_NOTES - read)
                    if (count < 0) break
                    read += count
                }
                String(buffer, 0, read).trim().ifEmpty { null }
            }
        }.getOrNull()
    }

    private const val MAX_NOTES = 16_000
}
