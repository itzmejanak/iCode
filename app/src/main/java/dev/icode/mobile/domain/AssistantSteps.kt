package dev.icode.mobile.domain

/**
 * The assistant's tools by the name the model calls them (docs/assistant-and-mcp.md): what a
 * step's sheet is titled, and how a run of steps says what they did — [one] for one call, [many] with
 * its count. The data layer's tools take their names from here, so each is written once. Then each
 * call, its result, and the tab a running call's command runs in.
 */
enum class AssistantToolName(val id: String, val title: String, private val one: String, private val many: String) {
    ListFiles("list_files", "List files", "listed a folder", "listed %d folders"),
    ReadFile("read_file", "Read file", "read a file", "read %d files"),
    SearchText("search_text", "Search", "ran a search", "ran %d searches"),
    GitStatus("git_status", "Git status", "checked Git's status", "checked Git's status %d times"),
    GitDiff("git_diff", "Git diff", "read a diff", "read %d diffs"),
    WriteFile("write_file", "Write file", "wrote a file", "wrote %d files"),
    EditFile("edit_file", "Edit file", "edited a file", "edited %d files"),
    RunCommand("run_command", "Command", "ran a command", "ran %d commands"),
    ListTerminals("list_terminals", "Terminals", "listed the terminals", "listed the terminals %d times"),
    ReadTerminal("read_terminal", "Read terminal", "read a terminal", "read terminals %d times"),
    SendTerminalInput("send_terminal_input", "Terminal input", "typed in a terminal", "typed in terminals %d times"),
    StartProcess("start_process", "Start process", "started a process", "started %d processes"),
    ProcessStatus("process_status", "Processes", "checked the processes", "checked the processes %d times"),
    SendProcessInput("send_process_input", "Process input", "sent a process input", "sent processes input %d times"),
    StopProcess("stop_process", "Stop process", "stopped a process", "stopped %d processes"),
    RunAndroidApp("run_android_app", "Build & Run", "built and ran the app", "built and ran the app %d times"),
    ListRunCommands("list_run_commands", "Run tab", "listed the Run tab", "listed the Run tab %d times"),
    SaveRunCommand("save_run_command", "Save Run command", "saved a Run command", "saved %d Run commands"),
    DeleteRunCommand("delete_run_command", "Delete Run command", "deleted a Run command", "deleted %d Run commands"),
    ListMcpServers("list_mcp_servers", "MCP servers", "listed the MCP servers", "listed the MCP servers %d times"),
    SaveMcpServer("save_mcp_server", "Save MCP server", "saved an MCP server", "saved %d MCP servers"),
    RemoveMcpServer("remove_mcp_server", "Remove MCP server", "removed an MCP server", "removed %d MCP servers"),
    ListSpeechProviders("list_speech_providers", "Speech providers", "listed the speech providers", "listed the speech providers %d times"),
    SaveSpeechProvider("save_speech_provider", "Save speech provider", "saved a speech provider", "saved %d speech providers"),
    RemoveSpeechProvider("remove_speech_provider", "Remove speech provider", "removed a speech provider", "removed %d speech providers"),
    ListLanguageServices("list_language_services", "Language servers", "listed the language servers", "listed the language servers %d times"),
    SaveLanguageService("save_language_service", "Save language server", "saved a language server", "saved %d language servers"),
    RemoveLanguageService("remove_language_service", "Remove language server", "removed a language server", "removed %d language servers"),
    ControlLanguageService("control_language_service", "Language server", "started or stopped a language server", "started or stopped language servers %d times"),
    LanguageDiagnostics("language_diagnostics", "Problems", "checked a file's problems", "checked problems %d times"),
    FindDefinition("find_definition", "Definition", "found a definition", "found %d definitions"),
    FindReferences("find_references", "References", "found references", "found references %d times"),
    DocumentSymbols("document_symbols", "Symbols", "read a file's symbols", "read symbols of %d files"),
    WorkspaceSymbols("workspace_symbols", "Workspace symbols", "searched the symbols", "searched the symbols %d times"),
    RenameSymbol("rename_symbol", "Rename symbol", "renamed a symbol", "renamed %d symbols"),
    FormatFile("format_file", "Format file", "formatted a file", "formatted %d files"),
    AskUser("ask_user", "Question", "asked you", "asked you %d times"),
    // A coding agent's own tools, by the kind it gives each (ACP `tool_call`; docs/assistant-and-mcp.md).
    DeleteFile("agent_delete", "Delete", "deleted a file", "deleted %d files"),
    MoveFile("agent_move", "Move", "moved a file", "moved %d files"),
    Fetch("agent_fetch", "Fetch", "fetched a page", "fetched %d pages"),
    Think("agent_think", "Think", "thought it through", "thought it through %d times"),
    SwitchMode("agent_mode", "Mode", "changed its mode", "changed its mode %d times"),
    AgentTool("agent_tool", "Tool", "used a tool", "used %d tools"),
    ;

    /** What [count] calls of it did: "read a file", "read 3 files". */
    fun did(count: Int): String = if (count == 1) one else many.format(count)

    companion object {
        private val byId = entries.associateBy { it.id }

        /** [id]'s tool; null for a name no tool of this build has (a model may call one). */
        fun of(id: String): AssistantToolName? = byId[id]
    }
}

/** What a step's sheet says of its status. */
val AssistantStepStatus.word: String get() = when (this) {
    AssistantStepStatus.Asking -> "Waiting for your answer"
    AssistantStepStatus.Running -> "Running"
    AssistantStepStatus.Queued -> "Next"
    AssistantStepStatus.Done -> "Completed"
    AssistantStepStatus.Failed -> "Failed"
}

/** How a run of steps is drawn and said (docs/assistant-and-mcp.md). */
object AssistantStepRules {
    /**
     * [entries] with each run of steps, one after another with nothing said between them, made one
     * [AssistantEntry.Steps], keyed by its first step.
     */
    fun rows(entries: List<AssistantEntry>): List<AssistantEntry> = buildList {
        var run = mutableListOf<AssistantEntry.Step>()
        fun close() {
            if (run.isNotEmpty()) add(AssistantEntry.Steps(run.first().key, run))
            run = mutableListOf()
        }
        entries.forEach { entry ->
            if (entry is AssistantEntry.Step) run += entry else {
                close()
                add(entry)
            }
        }
        close()
    }

    /**
     * What [steps] did, as their row says it: one step's own summary; for more, each tool's phrase in
     * the order first used, counted — "Read 2 files, listed a folder, ran a command".
     */
    fun summary(steps: List<AssistantEntry.Step>): String {
        steps.singleOrNull()?.let { return it.call.summary }
        val counts = steps.groupingBy { it.call.tool }.eachCount()
        return counts.entries.joinToString(", ") { (tool, count) ->
            AssistantToolName.of(tool)?.did(count) ?: if (count == 1) "used $tool" else "used $tool $count times"
        }.replaceFirstChar(Char::uppercaseChar)
    }

    /** A run's status, what matters first: a question waiting, then one running, one next, one failed; else done. */
    fun status(steps: List<AssistantEntry.Step>): AssistantStepStatus = STATUS_ORDER.first { status ->
        status == AssistantStepStatus.Done || steps.any { it.status == status }
    }

    /** The lines a run's changes added and removed, together; null when none of them changed a file. */
    fun changes(steps: List<AssistantEntry.Step>): LineChanges? {
        val changed = steps.filter { it.status == AssistantStepStatus.Done }.mapNotNull { it.changes }
        if (changed.isEmpty()) return null
        return LineChanges(changed.sumOf { it.added }, changed.sumOf { it.removed })
    }

    private val STATUS_ORDER = listOf(
        AssistantStepStatus.Asking,
        AssistantStepStatus.Running,
        AssistantStepStatus.Queued,
        AssistantStepStatus.Failed,
        AssistantStepStatus.Done,
    )
}

/** What a tool does to the workspace: reads never ask; changes and commands do. */
enum class AssistantToolKind { Read, Change, Command }

/** One request for a tool, as the model made it. */
data class AssistantCall(
    /** The provider's ID for it, or iCode's for a call that came without one: its result names it. */
    val id: String,
    val tool: String,
    /** Its arguments as the model wrote them: JSON text. */
    val arguments: String,
    /** "Read src/App.kt": what its step says, written when the call arrived. */
    val summary: String,
    val kind: AssistantToolKind,
    /** The workspace file it reads or changes, relative to its top; null for a call that touches none. */
    val file: String? = null,
    /** The command line it runs, whole, for a call that runs one. */
    val command: String? = null,
)

/** A tool's answer, as the model reads it, and for a change to a file, by how many lines it changed it. */
data class AssistantResult(
    val callId: String,
    val tool: String,
    val output: String,
    val failed: Boolean,
    val changes: LineChanges? = null,
    /** What a command printed, as printed — colours and all, bounded: its step replays it as a terminal (docs/assistant-and-mcp.md). */
    val printed: String? = null,
)

/** Text a change writes, under [label]: what write_file writes, what edit_file replaces and with what. */
data class AssistantCode(val label: String, val text: String)

/**
 * read_file's answer as the model reads it, each line after its number and a tab, written and read in
 * this one place: a step's page shows the numbers apart from the file's lines, which it colours as the
 * file is (docs/editor-and-language-services.md).
 */
object NumberedLines {
    fun write(start: Int, lines: List<String>): String = lines.mapIndexed { index, line -> "${start + index}\t$line" }.joinToString("\n")

    /** [text]'s lines, each with its number; null for a line with none, such as the note after part of a file. */
    fun read(text: String): List<Pair<Int?, String>> = text.split('\n').map { line ->
        val tab = line.indexOf('\t')
        val number = if (tab > 0) line.substring(0, tab).toIntOrNull() else null
        if (number != null) number to line.substring(tab + 1) else null to line
    }
}

/** A call's command running in a terminal tab: [session] is the tab's ID. */
data class AssistantTerminal(val callId: String, val session: String)
