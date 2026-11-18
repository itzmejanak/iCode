package dev.icode.mobile.domain

/** One row of the Assistant page, keyed for its list. */
sealed interface AssistantEntry {
    val key: String

    /** What the user wrote, and the files sent with it. */
    data class Said(override val key: String, val text: String, val attachments: List<AssistantAttachment> = emptyList()) : AssistantEntry

    /** What the assistant answered, Markdown. */
    data class Answer(override val key: String, val text: String, /** What an agent said it cost: "0.04 USD". */ val cost: String? = null) : AssistantEntry

    /** Who answers from here on: named above the first reply after something the user said, and where it changes. */
    data class Answerer(override val key: String, val by: AssistantAnswerer) : AssistantEntry

    /** What the model thought before it answered, as its provider sent it; [live] while it is still being written. */
    data class Reasoning(override val key: String, val text: String, val live: Boolean = false) : AssistantEntry

    /** A tool the assistant used, and what came of it. */
    data class Step(
        override val key: String,
        val call: AssistantCall,
        val status: AssistantStepStatus,
        /** What the tool answered; null while it has not. */
        val output: String? = null,
        /** For a change to a file, the lines it added and removed. */
        val changes: LineChanges? = null,
        /** For a command, what it printed, as printed: its replay (docs/assistant-and-mcp.md). */
        val printed: String? = null,
    ) : AssistantEntry

    /** Where the chat was compacted: before it, the model reads only its summary. */
    data class Compacted(override val key: String, val summary: String) : AssistantEntry

    /** Steps one after another, one row of the chat ([AssistantStepRules.rows]); keyed by its first. */
    data class Steps(override val key: String, val steps: List<Step>) : AssistantEntry
}

enum class AssistantStepStatus { Asking, Running, Queued, Done, Failed }
