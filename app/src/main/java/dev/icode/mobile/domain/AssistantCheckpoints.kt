package dev.icode.mobile.domain

/** One file as a checkpoint keeps it: its content's SHA-256, its size and last change, and whether it runs. */
data class CheckpointFile(val hash: String, val size: Long, val modified: Long, val executable: Boolean)

/**
 * A place's files at one moment (docs/assistant-and-mcp.md), by their paths relative to it, `/` between
 * folders; [taken] is when. What it leaves out is never undone: see [CheckpointRules].
 */
data class Checkpoint(val taken: Long, val files: Map<String, CheckpointFile>)

/**
 * Undo back to before message [index] of the chat shown, its files, its chat or both, waiting for the
 * user: [changed] are files changed outside the chat's own work since (docs/assistant-and-mcp.md).
 */
data class AssistantUndo(val index: Int, val files: Boolean, val chat: Boolean, val changed: List<String>)

/** Undo in the chat shown (docs/assistant-and-mcp.md), in the assistant's order. */
sealed interface UndoIntent : ICodeIntent.AssistantIntent {
    /**
     * Back to before its message at [index]: its place's [files], its [chat], or both. Files changed
     * outside its work since are asked about first, unless [confirmed].
     */
    data class Message(val index: Int, val files: Boolean, val chat: Boolean, val confirmed: Boolean = false) : UndoIntent

    /** The Undo asked about is not made. */
    data object Dismiss : UndoIntent

    /** The composer took the message an Undo took out of the chat. */
    data object Taken : UndoIntent
}

/** How a chat's Undo puts back what changed since one of its messages (docs/assistant-and-mcp.md). */
object CheckpointRules {
    /**
     * Folders a checkpoint never keeps, nor Undo touches: Git's own, and what tools make again —
     * dependencies, builds and caches.
     */
    val SKIPPED = setOf(
        ".git", "node_modules", "build", "dist", "out", "target", ".gradle", ".next", ".nuxt", ".cache", "__pycache__",
        ".venv", "venv", ".tox", ".mypy_cache", ".pytest_cache", "Pods", "DerivedData", ".dart_tool", ".terraform", "coverage",
    )

    /** Files larger than this are not kept, nor touched by Undo. */
    const val MAX_FILE = 64L * 1024 * 1024

    /**
     * Whether a folder named [name] is left out. In the runtime's home — the place of the chats in no
     * workspace — hidden folders are too: the tools', the agents' own, and their caches.
     */
    fun skips(name: String, home: Boolean): Boolean = name in SKIPPED || home && name.startsWith(".")

    /** The paths whose content, or whether it runs, differs between [from] and [to]: changed, added or removed. */
    fun changed(from: Checkpoint, to: Checkpoint): Set<String> =
        (from.files.keys + to.files.keys).filterTo(sortedSetOf()) { path ->
            val before = from.files[path]
            val after = to.files[path]
            before?.hash != after?.hash || before?.executable != after?.executable
        }

    /**
     * What changed outside a chat's own work since one of its messages: between each of its turns' end
     * and the next message's start, and since its last turn's end — [spans], each an end and what
     * followed it. A span whose end was never kept (a turn the process lost) says nothing.
     */
    fun outside(spans: List<Pair<Checkpoint?, Checkpoint>>): Set<String> =
        spans.flatMapTo(sortedSetOf()) { (end, next) -> end?.let { changed(it, next) }.orEmpty() }
}
