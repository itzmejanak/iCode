package dev.icode.mobile.domain

/**
 * The assistant's tools by the name the model calls them (docs/assistant-sheets-plan.md §2.1): what a
 * step's sheet is titled, and how a run of steps says what they did — [one] for one call, [many] with
 * its count. The data layer's tools take their names from here, so each is written once. Then each
 * call and its result.
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

/** How a run of steps is drawn and said (docs/assistant-sheets-plan.md §2.1). */
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
    /** What a command printed, as printed — colours and all, bounded: its step replays it as a terminal (docs/agent-control-plan.md §6). */
    val printed: String? = null,
)
