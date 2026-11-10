package dev.icode.mobile.domain

import dev.icode.mobile.data.workspace.ICodeStateHolder
import org.junit.Assert.assertEquals
import org.junit.Test

/** docs/dock-navigation-plan.md §5.6 F2: each tool's badge, from the snapshot (D7). */
class ToolBadgesTest {
    private val empty = ICodeStateHolder.emptySnapshot()

    private fun badge(tool: WorkspaceTool, snapshot: ICodeSnapshot) = ToolBadges.of(tool, snapshot)

    @Test
    fun `at rest no tool carries anything`() {
        WorkspaceTool.entries.forEach { assertEquals(ToolBadge(), badge(it, empty)) }
    }

    @Test
    fun `the Editor counts its unsaved files`() {
        val documents = listOf(document("a.kt", dirty = true), document("b.kt", dirty = false), document("c.kt", dirty = true))
        assertEquals(ToolBadge(2, said = "2 unsaved files"), badge(WorkspaceTool.Editor, empty.copy(openDocuments = documents)))
        assertEquals("1 unsaved file", badge(WorkspaceTool.Editor, empty.copy(openDocuments = documents.take(1))).said)
    }

    @Test
    fun `Source Control counts each changed file once, staged and changed again or not`() {
        val changes = listOf(
            GitChange(GitStatusCodes.MODIFIED, "a.kt", "src/a.kt", staged = true),
            GitChange(GitStatusCodes.MODIFIED, "a.kt", "src/a.kt", staged = false),
            GitChange(GitStatusCodes.UNTRACKED, "b.kt", "src/b.kt", staged = false),
        )
        assertEquals(ToolBadge(2, said = "2 changes"), badge(WorkspaceTool.SourceControl, empty.copy(gitChanges = changes)))
    }

    @Test
    fun `Terminal counts the errors in Problems, a build's and a language server's, and shows a command running`() {
        val problems = empty.copy(
            diagnostics = listOf(problem(Severity.Error), problem(Severity.Warning)),
            languageDiagnostics = listOf(problem(Severity.Error), problem(Severity.Info)),
        )
        assertEquals(ToolBadge(2, said = "2 errors"), badge(WorkspaceTool.Terminal, problems))
        val running = problems.copy(terminal = TerminalSnapshot(listOf(session("s1"), session("s2", running = true))))
        assertEquals(ToolBadge(2, runs = true, said = "2 errors, a command running"), badge(WorkspaceTool.Terminal, running))
        // A session that has ended runs nothing, whatever its shell last said.
        val ended = empty.copy(terminal = TerminalSnapshot(listOf(session("s1", running = true).copy(status = TerminalStatus.Exited))))
        assertEquals(ToolBadge(), badge(WorkspaceTool.Terminal, ended))
    }

    @Test
    fun `the Assistant is in the accent while it works, and amber while it waits for you`() {
        val turn = AssistantTurn("/w", "w", "chat", "Thinking…")
        val working = empty.copy(assistant = AssistantState(turn = turn))
        assertEquals(ToolBadge(tone = NoticeTone.Info, said = "working"), badge(WorkspaceTool.Assistant, working))
        val asking = empty.copy(assistant = AssistantState(turn = turn, pause = AssistantPause(AssistantPause.Kind.StepLimit, "")))
        assertEquals(ToolBadge(tone = NoticeTone.HeadsUp, said = "waiting for you"), badge(WorkspaceTool.Assistant, asking))
    }

    private fun document(name: String, dirty: Boolean) =
        OpenDocument(name, "/w/$name", "", TextEncoding.Utf8, LineEnding.Lf, dirty = dirty, readOnly = false)

    private fun problem(severity: Severity) = Diagnostic(severity, "a.kt", "m", "1:1", "kotlinc")

    private fun session(id: String, running: Boolean = false) =
        TerminalSessionState(id, "bash", TerminalStatus.Running, commandRunning = running)
}
