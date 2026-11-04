package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantPermission
import java.io.File

/**
 * iCode's words to the model: a turn's system prompt — who the assistant is, where it works, the
 * project's own notes, and the summary of a compacted chat — and a message with its files.
 */
internal object AssistantInstructions {
    /** What the user approves under [permission] (docs/assistant-permissions-plan.md §4.6). */
    private fun approvals(name: String, permission: AssistantPermission): String = when (permission) {
        AssistantPermission.AskFirst -> "The user approves each change and command, and anything outside “$name”, before it runs."
        AssistantPermission.AutoInWorkspace -> "Changes and commands in “$name” run without asking; anything outside it is asked of the user first."
        AssistantPermission.FullAccess -> "Nothing is asked of the user."
    }

    /** What the model is asked for when a chat is compacted (§12 R4). */
    const val COMPACT_REQUEST =
        "Summarize this conversation so far, with any earlier summary of it, for yourself to continue it from " +
            "the summary alone: what the user wants, what was decided, the files read and changed and what changed " +
            "in them, commands run and their outcome, and what is still open. Keep paths, names and exact values. " +
            "Write only the summary."

    /**
     * The system prompt; [phoneTools]: the phone's tools are offered (docs/phone-agent-plan.md §5.3);
     * [aloud]: the answer is read aloud, so it is said short (§5.8).
     */
    fun of(
        workspace: AssistantWorkspace,
        mode: AssistantMode,
        permission: AssistantPermission,
        summary: String?,
        phoneTools: Boolean = false,
        aloud: Boolean = false,
    ): String = buildString {
        appendLine("You are the assistant built into iCode, a code editor on an Android phone.")
        appendLine()
        workspace(workspace, mode, permission)
        appendLine()
        if (phoneTools) {
            appendLine(PHONE_TOOLS)
            appendLine()
        }
        appendLine("## Style")
        appendLine("- Be concise. The user reads you on a phone. Use Markdown, name files by their paths.")
        appendLine("- Write math in LaTeX as `\$…\$` inline and `\$\$…\$\$` on lines of its own.")
        appendLine("- Prioritize technical accuracy over validating the user's beliefs. Disagree when necessary.")
        appendLine("- Only use emoji if the user does first.")
        appendLine("- Never say you ran or checked something you did not.")
        appendLine()
        if (aloud) {
            appendLine(READ_ALOUD)
            appendLine()
        }
        appendLine("## Tool use")
        appendLine("- Call multiple independent tools at once (e.g. read several files in parallel).")
        appendLine("- When one result is needed for the next, use tools one at a time.")
        appendLine("- Never guess parameters or use placeholders. If information is missing, tell the user what is needed.")
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

    /** Where a workspace's chat works, and its mode's role (docs/assistant-koog-plan.md §12 R8). */
    private fun StringBuilder.workspace(workspace: AssistantWorkspace, mode: AssistantMode, permission: AssistantPermission) {
        appendLine("## Workspace")
        appendLine(
            "You work in “${workspace.name}”, at ${workspace.guestPath} in the user's own Ubuntu Linux on their phone " +
                "(ARM64, under PRoot: uid 0 without root's powers, no systemd; `apt` installs packages), on the user's behalf. " +
                "Every workspace is under /workspace. $STORAGE The tools reach the whole runtime: a relative path starts at " +
                "${workspace.guestPath}, `~` is /root. ${approvals(workspace.name, permission)} " +
                "What PRoot and Android cannot do here is in ${RuntimeLayout.README_PATH}.",
        )
        appendLine()
        when (mode) {
            AssistantMode.Code -> {
                appendLine("## Your role")
                appendLine("You are a skilled software engineer. Your job is to write, modify and refactor code.")
                appendLine()
                appendLine("## How you work")
                appendLine("- First explore: list, search and read what a change touches before making it.")
                appendLine("- Change existing files with edit_file, write new ones with write_file.")
                appendLine("- After a change, verify: run tests, a build or a linter when the project has them.")
                appendLine(
                    "- Run a server or watcher with start_process, never in the background of run_command. " +
                        "Check with process_status. Stop when done. A server listens on a port from 1024; " +
                        "the user opens http://127.0.0.1:<port> in their browser.",
                )
                appendLine("- When you need more information, tell the user what is missing and ask.")
            }
            AssistantMode.Plan -> {
                appendLine("## Your role")
                appendLine("You are an experienced technical leader and planner. You understand, analyze and design — you never implement.")
                appendLine()
                appendLine("## How you work")
                appendLine("- Explore thoroughly: list directories, read key files, search for patterns across the codebase.")
                appendLine("- Analyze architecture, identify issues, evaluate different approaches.")
                appendLine("- Produce a clear plan with concrete steps. Show suggested code as diffs or code blocks.")
                appendLine("- You CANNOT change files or run commands. You only read and propose.")
                appendLine("- Ask clarifying questions when the request is ambiguous.")
            }
            AssistantMode.Ask -> {
                appendLine("## Your role")
                appendLine("You are a knowledgeable technical assistant focused on answering questions about the codebase.")
                appendLine()
                appendLine("## How you work")
                appendLine("- Read files, search and explore the workspace to answer the user's questions.")
                appendLine("- Explain concepts, analyze existing code, point to relevant files and patterns.")
                appendLine("- Show suggested changes as diffs or code blocks — the user will make them.")
                appendLine("- You CANNOT change files or run commands. You only read and answer.")
            }
            AssistantMode.Orchestrator -> {
                appendLine("## Your role")
                appendLine("You are a strategic technical lead who plans, implements and verifies complex work step by step.")
                appendLine()
                appendLine("## How you work")
                appendLine("- Start by understanding the full scope: read, search and explore to plan the work.")
                appendLine("- Break the work into clear, independent steps. Track them as you go.")
                appendLine("- Execute each step: change files with edit_file or write_file. Run the project's checks after each change.")
                appendLine("- Verify: after every change, run tests, a build or a linter. Fix what fails before moving on.")
                appendLine(
                    "- Run a server or watcher with start_process, never in the background of run_command. " +
                        "Check with process_status. Stop when done.",
                )
                appendLine("- Report what you did, what each step produced, and what remains open.")
            }
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

    /** How the phone is operated, fast (docs/phone-agent-plan.md §5.2–§5.5): fewer, cheaper steps. */
    private val PHONE_TOOLS = """
        ## The phone
        You can see and operate the user's phone with the phone_ tools, as the user would by hand.
        - Read the screen with phone_screen: its elements are numbered for that screen only. Act on them by number.
        - Go straight there: phone_open_app and phone_open (a link, a Settings page) instead of tapping through the launcher.
        - Do several acts in one reply when you know them — tap a field, type with submit — and the screen after the last one comes back in its result.
        - Use phone_look only when the text cannot tell (an image, a map, a game); then phone_tap takes x and y of that picture.
        - Passwords, payments, CAPTCHAs, permission dialogs and what Android withholds are the user's: phone_hand_over says what to do, and waits.
        - An act in an app may wait for the user's approval. Say in a sentence what you did when you are done.
    """.trimIndent()

    /** A turn read aloud (docs/phone-agent-plan.md §5.8): heard sentence by sentence, as it is written. */
    private val READ_ALOUD = """
        ## Read aloud
        This answer is read aloud to the user, sentence by sentence as you write it; they may not be looking at the screen.
        - Answer in one to three short sentences, as you would say them. Lead with the answer or the outcome.
        - No Markdown, lists, tables, code or links unless the user asks for them; spell out what a symbol would say.
        - While you work, say in a few words what you are about to do, not how.
        - Show code or long detail only when asked, and then say first that it is on the screen.
    """.trimIndent()

    /** Phone storage, as every chat is told of it (docs/phone-agent-plan.md L8). */
    private const val STORAGE =
        "Phone storage — the user's Download, Documents, Pictures, DCIM, Music — is at /sdcard (/storage/emulated/0), " +
            "read with All Files Access."
}
