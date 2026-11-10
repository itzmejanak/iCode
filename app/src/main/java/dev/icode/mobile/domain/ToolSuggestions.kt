package dev.icode.mobile.domain

/**
 * A tool the dock suggests (docs/dock-navigation-plan.md D8), and [why]: something that happened
 * while another tool showed. [session] is the terminal whose long command ended.
 */
data class ToolSuggestion(val why: Why, val session: String? = null) {
    val tool: WorkspaceTool get() = why.tool

    /** Each rule of D8: its tool, the tone the dock's grip takes, and what TalkBack says. */
    enum class Why(val tool: WorkspaceTool, val tone: NoticeTone, val said: String) {
        Asking(WorkspaceTool.Assistant, NoticeTone.HeadsUp, "the assistant is waiting for you"),
        Conflicts(WorkspaceTool.SourceControl, NoticeTone.Problem, "conflicts to resolve"),
        BuildErrors(WorkspaceTool.Terminal, NoticeTone.Problem, "the build has errors"),
        CommandEnded(WorkspaceTool.Terminal, NoticeTone.Done, "a long command ended"),
        FilesChanged(WorkspaceTool.SourceControl, NoticeTone.Info, "the assistant changed files"),
    }
}

/** What the suggestions follow, read from a snapshot ([of]). */
data class SuggestionFacts(
    /** The open workspace: in another, nothing before counts. */
    val workspace: String? = null,
    val asking: Boolean = false,
    val ended: AssistantTurnEnd? = null,
    val inRepository: Boolean = false,
    /** Errors a build or run found: the Problems a language server finds as you type do not count. */
    val buildErrors: Int = 0,
    /** Each running terminal's long commands ended so far. */
    val longRuns: Map<String, Int> = emptyMap(),
    /** Files Git holds in conflict. */
    val conflicts: Int = 0,
) {
    companion object {
        fun of(snapshot: ICodeSnapshot) = SuggestionFacts(
            workspace = snapshot.activeWorkspaceLocation,
            asking = snapshot.assistant.asking,
            ended = snapshot.assistant.ended,
            inRepository = snapshot.gitRepository != null,
            buildErrors = snapshot.diagnostics.count { it.severity == Severity.Error },
            longRuns = snapshot.terminal.sessions.filter { it.isRunning }.associate { it.id to it.longRunsEnded },
            conflicts = ToolBadges.files(snapshot.gitChanges.filter { it.status == GitStatusCodes.CONFLICTED }),
        )
    }
}

/**
 * The dock's one suggestion (D8), following the facts snapshot by snapshot ([next]). Fixed rules,
 * never guesses: something happens while another tool shows, and its tool is suggested until it is
 * shown or what it is about has passed. The newest wins; of two at once, the first in [ToolSuggestion.Why]'s
 * order. It never switches by itself.
 */
data class ToolSuggestions(val suggestion: ToolSuggestion? = null, private val facts: SuggestionFacts? = null) {
    /** With [now], [shown] being the tool on screen (null outside the workspace). */
    fun next(now: SuggestionFacts, shown: WorkspaceTool?): ToolSuggestions {
        val before = facts
        // The first facts are where it starts, and another workspace starts again: nothing has happened yet.
        if (before == null || before.workspace != now.workspace) return ToolSuggestions(facts = now)
        val newest = happened(before, now)?.takeIf { it.tool != shown }
        val kept = suggestion?.takeIf { it.tool != shown && holds(it, now) }
        return ToolSuggestions(newest ?: kept, now)
    }

    private fun happened(before: SuggestionFacts, now: SuggestionFacts): ToolSuggestion? = when {
        now.asking && !before.asking -> ToolSuggestion(ToolSuggestion.Why.Asking)
        now.conflicts > 0 && before.conflicts == 0 -> ToolSuggestion(ToolSuggestion.Why.Conflicts)
        now.buildErrors > before.buildErrors -> ToolSuggestion(ToolSuggestion.Why.BuildErrors)
        else -> now.longRuns.entries.firstOrNull { (id, ended) -> ended > (before.longRuns[id] ?: 0) }
            ?.let { ToolSuggestion(ToolSuggestion.Why.CommandEnded, it.key) }
            ?: now.ended?.takeIf { it != before.ended && it.changedFiles && it.workspace == now.workspace && now.inRepository }
                ?.let { ToolSuggestion(ToolSuggestion.Why.FilesChanged) }
    }

    /** What [suggestion] is about is still there. */
    private fun holds(suggestion: ToolSuggestion, now: SuggestionFacts): Boolean = when (suggestion.why) {
        ToolSuggestion.Why.Asking -> now.asking
        ToolSuggestion.Why.Conflicts -> now.conflicts > 0
        ToolSuggestion.Why.BuildErrors -> now.buildErrors > 0
        ToolSuggestion.Why.CommandEnded -> suggestion.session in now.longRuns
        ToolSuggestion.Why.FilesChanged -> now.inRepository
    }
}
