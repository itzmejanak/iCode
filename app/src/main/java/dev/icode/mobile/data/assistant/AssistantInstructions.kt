package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.domain.AssistantAttachmentKind
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
                "Linux runtime on the user's phone (ARM64, under PRoot: uid 0 without root's powers, no systemd; " +
                "`apt` installs packages). Only this workspace is under /workspace. File tool paths are relative to its folder.",
        )
        appendLine()
        appendLine("How you work:")
        when (mode) {
            AssistantMode.Agent -> {
                appendLine("- Look before you change: list, search and read what a change touches.")
                appendLine("- Change an existing file with edit_file; write a new one, or a rewrite, with write_file.")
                appendLine("- Keep to what was asked. Run the project's own quick checks (tests, a build, a linter) after a change when it has them.")
                appendLine(
                    "- Run a server, a watcher or anything that must keep running with start_process, never in the background of " +
                        "run_command; check it with process_status and stop it when it is no longer needed. A server listens on a " +
                        "port from 1024, and the user opens http://127.0.0.1:<port> in their browser.",
                )
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

    /**
     * [message]'s text as the model reads it: the text files the user attached, then what they
     * wrote, then the names of the images and PDFs that go with it as attachments of their own.
     */
    fun message(message: AssistantMessage.User): String {
        val texts = message.attachments.filter { it.kind == AssistantAttachmentKind.Text }
        val others = message.attachments.filter { it.kind != AssistantAttachmentKind.Text }
        if (texts.isEmpty() && others.isEmpty()) return message.text
        return buildString {
            if (texts.isNotEmpty()) {
                appendLine("Files the user attached, as they were when sent:")
                texts.forEach { file ->
                    appendLine()
                    appendLine("<file path=\"${file.name}\">")
                    appendLine(file.text.trimEnd())
                    appendLine("</file>")
                }
                appendLine()
            }
            append(message.text)
            if (others.isNotEmpty()) {
                appendLine()
                appendLine()
                append("Also attached: " + others.joinToString(", ") { "${it.name} (${if (it.kind == AssistantAttachmentKind.Image) "image" else "PDF"})" })
            }
        }.trim()
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
