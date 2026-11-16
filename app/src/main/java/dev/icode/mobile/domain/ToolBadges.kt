package dev.icode.mobile.domain

import dev.icode.mobile.domain.notices.NoticeTone

/**
 * What a tool's icon carries, on the dock and the rail alike (docs/design-and-navigation.md):
 * [count] over it, zero for none; its icon in [tone] while the tool is busy or waits for the user;
 * a dot while something [runs] there.
 */
data class ToolBadge(
    val count: Int = 0,
    val tone: NoticeTone? = null,
    val runs: Boolean = false,
    /** What TalkBack says after the tool's name: "3 changes"; null when there is nothing to say. */
    val said: String? = null,
)

/** Each tool's badge, from the snapshot. */
object ToolBadges {
    fun of(tool: WorkspaceTool, snapshot: ICodeSnapshot): ToolBadge = when (tool) {
        WorkspaceTool.Editor -> snapshot.openDocuments.count { it.dirty }.let { ToolBadge(it, said = counted(it, "unsaved file")) }
        WorkspaceTool.SourceControl -> files(snapshot.gitChanges).let { ToolBadge(it, said = counted(it, "change")) }
        WorkspaceTool.Terminal -> {
            val errors = snapshot.problems.count { it.severity == Severity.Error }
            val runs = snapshot.terminal.sessions.any { it.isRunning && it.commandRunning }
            ToolBadge(errors, runs = runs, said = listOfNotNull(counted(errors, "error"), "a command running".takeIf { runs }).joinToString(", ").ifEmpty { null })
        }
        WorkspaceTool.Assistant -> when {
            snapshot.assistant.asking -> ToolBadge(tone = NoticeTone.HeadsUp, said = "waiting for you")
            snapshot.assistant.running -> ToolBadge(tone = NoticeTone.Info, said = "working")
            else -> ToolBadge()
        }
        WorkspaceTool.Explorer, WorkspaceTool.Search -> ToolBadge()
    }

    /** How many files [changes] are of: a file staged and changed again is one, as VS Code counts it. */
    fun files(changes: List<GitChange>): Int = changes.distinctBy { it.path }.size

    private fun counted(count: Int, what: String): String? = when (count) {
        0 -> null
        1 -> "1 $what"
        else -> "$count ${what}s"
    }
}
