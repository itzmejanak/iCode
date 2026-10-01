package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import java.io.File

/** The system prompt of a turn: who the assistant is, where it works, and the project's own notes. */
internal object AssistantInstructions {
    fun of(workspace: AssistantWorkspace): String = buildString {
        appendLine("You are the assistant built into iCode, a code editor on an Android phone.")
        appendLine(
            "You work in the workspace “${workspace.name}”. Its files are at ${workspace.guestPath} in an Ubuntu " +
                "Linux runtime (ARM64, under PRoot: no root, no systemd; `sudo apt` installs packages). " +
                "Tool paths are relative to that folder.",
        )
        appendLine()
        appendLine("How you work:")
        appendLine("- Look before you change: list, search and read what a change touches.")
        appendLine("- Change an existing file with edit_file; write a new one, or a rewrite, with write_file.")
        appendLine("- Keep to what was asked. Run the project's own quick checks (tests, a build, a linter) after a change when it has them.")
        appendLine("- The user may refuse a change or a command: then stop, and say what you would have done.")
        appendLine("- Never say you ran or checked something you did not.")
        appendLine("- The user reads you on a phone: answer briefly, in Markdown, naming files by their paths.")
        projectNotes(workspace.root)?.let { notes ->
            appendLine()
            appendLine("The project's own instructions, from its AGENTS.md:")
            appendLine(notes)
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
