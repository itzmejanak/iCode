package dev.icode.mobile.domain

import dev.icode.mobile.domain.ToolSuggestion.Why
import dev.icode.mobile.domain.WorkspaceTool.Assistant
import dev.icode.mobile.domain.WorkspaceTool.Editor
import dev.icode.mobile.domain.WorkspaceTool.SourceControl
import dev.icode.mobile.domain.WorkspaceTool.Terminal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Each suggestion starts when its event happens out of sight, ends as its row says, and never switches
 * by itself (docs/design-and-navigation.md). The newest wins.
 */
class ToolSuggestionsTest {
    private val start = SuggestionFacts(workspace = "/w", inRepository = true)

    /** The suggestion after each of [steps], each the facts and the tool on screen, from [start]. */
    private fun after(vararg steps: Pair<SuggestionFacts, WorkspaceTool?>): ToolSuggestion? = from(start, *steps)

    private fun from(first: SuggestionFacts, vararg steps: Pair<SuggestionFacts, WorkspaceTool?>): ToolSuggestion? =
        steps.fold(ToolSuggestions().next(first, Editor)) { suggestions, (facts, shown) -> suggestions.next(facts, shown) }.suggestion

    @Test
    fun `the assistant asking out of sight suggests it, until it is answered or shown`() {
        val asking = start.copy(asking = true)
        assertEquals(ToolSuggestion(Why.Asking), after(asking to Editor))
        assertNull(after(asking to Editor, start to Editor))
        assertNull(after(asking to Editor, asking to Assistant))
        // Asked while the Assistant shows, it is in sight: nothing to suggest.
        assertNull(after(asking to Assistant, asking to Editor))
    }

    @Test
    fun `a turn that changed files in a repository suggests Source Control, until it is opened`() {
        val changed = start.copy(ended = AssistantTurnEnd("/w", 1, changedFiles = true))
        assertEquals(ToolSuggestion(Why.FilesChanged), after(changed to Assistant))
        assertEquals(ToolSuggestion(Why.FilesChanged), after(changed to Assistant, changed to Editor))
        assertNull(after(changed to Assistant, changed to SourceControl, changed to Editor))
        // A turn that changed nothing, one in another workspace, and one outside a repository suggest nothing.
        assertNull(after(start.copy(ended = AssistantTurnEnd("/w", 1, changedFiles = false)) to Assistant))
        assertNull(after(start.copy(ended = AssistantTurnEnd("/other", 1, changedFiles = true)) to Assistant))
        assertNull(after(changed.copy(inRepository = false) to Assistant))
        // Each turn's end is one event: the next turn's is another.
        val again = changed.copy(ended = AssistantTurnEnd("/w", 2, changedFiles = true))
        assertEquals(ToolSuggestion(Why.FilesChanged), after(changed to Assistant, changed to SourceControl, again to Assistant))
    }

    @Test
    fun `a build that found more errors suggests Terminal, until it is opened or they are gone`() {
        val errors = start.copy(buildErrors = 2)
        assertEquals(ToolSuggestion(Why.BuildErrors), after(errors to Editor))
        assertNull(after(errors to Editor, start to Editor))
        assertNull(after(errors to Editor, errors to Terminal, errors to Editor))
        // Fewer errors than before is no news.
        assertNull(after(errors to Terminal, start.copy(buildErrors = 1) to Editor))
    }

    @Test
    fun `a long command that ended suggests its terminal, until it is opened or the session is gone`() {
        val running = start.copy(longRuns = mapOf("s1" to 0, "s2" to 3))
        val ended = running.copy(longRuns = mapOf("s1" to 0, "s2" to 4))
        assertNull(from(running, running to Editor))
        assertEquals(ToolSuggestion(Why.CommandEnded, "s2"), from(running, ended to Editor))
        assertNull(from(running, ended to Editor, ended.copy(longRuns = mapOf("s1" to 0)) to Editor))
        assertNull(from(running, ended to Editor, ended to Terminal))
        // Ended while Terminal shows, it was seen.
        assertNull(from(running, ended to Terminal, ended to Editor))
        // A session opened since: one long command ended in it is news too.
        assertEquals(ToolSuggestion(Why.CommandEnded, "s3"), from(running, running.copy(longRuns = running.longRuns + ("s3" to 1)) to Editor))
    }

    @Test
    fun `conflicts left by a pull or a merge suggest Source Control, until they are resolved or it is opened`() {
        val conflicts = start.copy(conflicts = 2)
        assertEquals(ToolSuggestion(Why.Conflicts), after(conflicts to Terminal))
        assertEquals(ToolSuggestion(Why.Conflicts), after(conflicts to Terminal, start.copy(conflicts = 1) to Terminal))
        assertNull(after(conflicts to Terminal, start to Terminal))
        assertNull(after(conflicts to Terminal, conflicts to SourceControl))
    }

    @Test
    fun `the newest wins, and the first in order of two at once`() {
        val asking = start.copy(asking = true)
        val errorsToo = asking.copy(buildErrors = 1)
        assertEquals(ToolSuggestion(Why.BuildErrors), after(asking to Editor, errorsToo to Editor))
        assertEquals(ToolSuggestion(Why.Asking), after(errorsToo to Editor))
    }

    @Test
    fun `what was there before it started, or in another workspace, is no event`() {
        val busy = SuggestionFacts(workspace = "/w", asking = true, buildErrors = 3, conflicts = 1, inRepository = true)
        assertNull(ToolSuggestions().next(busy, Editor).suggestion)
        val suggested = ToolSuggestions().next(start, Editor).next(start.copy(asking = true), Editor)
        assertEquals(ToolSuggestion(Why.Asking), suggested.suggestion)
        // Another workspace opens: what it has is where it starts, and the suggestion goes.
        assertNull(suggested.next(busy.copy(workspace = "/other"), Editor).suggestion)
    }
}
