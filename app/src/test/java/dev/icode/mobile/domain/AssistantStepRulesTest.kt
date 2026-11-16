package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A run of steps as the chat draws it (docs/assistant-and-mcp.md). */
class AssistantStepRulesTest {
    private fun step(
        key: String,
        tool: AssistantToolName = AssistantToolName.ReadFile,
        status: AssistantStepStatus = AssistantStepStatus.Done,
        changes: LineChanges? = null,
        summary: String = "${tool.title} $key",
    ) = AssistantEntry.Step(key, AssistantCall(key, tool.id, "{}", summary, AssistantToolKind.Read), status, changes = changes)

    @Test fun `steps one after another are one row, broken by what is said between them`() {
        val said = AssistantEntry.Said("m0", "Fix it")
        val reasoning = AssistantEntry.Reasoning("m1r", "Thinking")
        val answer = AssistantEntry.Answer("m3", "Done.")
        val rows = AssistantStepRules.rows(listOf(said, step("a"), step("b"), reasoning, step("c"), answer))
        assertEquals(listOf("m0", "a", "m1r", "c", "m3"), rows.map { it.key })
        assertEquals(listOf("a", "b"), (rows[1] as AssistantEntry.Steps).steps.map { it.key })
        assertEquals(listOf("c"), (rows[3] as AssistantEntry.Steps).steps.map { it.key })
    }

    @Test fun `one step says its own summary, more say what each tool did, counted, in the order first used`() {
        assertEquals("Read README.md", AssistantStepRules.summary(listOf(step("a", summary = "Read README.md"))))
        val run = listOf(
            step("a"), step("b", AssistantToolName.ListFiles), step("c"),
            step("d", AssistantToolName.RunCommand), step("e", AssistantToolName.EditFile),
        )
        assertEquals("Read 2 files, listed a folder, ran a command, edited a file", AssistantStepRules.summary(run))
        assertEquals("Ran 2 commands", AssistantStepRules.summary(listOf(step("a", AssistantToolName.RunCommand), step("b", AssistantToolName.RunCommand))))
    }

    @Test fun `a tool this build does not have is said by its name`() {
        val unknown = AssistantEntry.Step("x", AssistantCall("x", "browse", "{}", "browse", AssistantToolKind.Read), AssistantStepStatus.Failed)
        assertEquals("Used browse, read a file", AssistantStepRules.summary(listOf(unknown, step("a"))))
        assertNull(AssistantToolName.of("browse"))
    }

    @Test fun `a run's status says what matters first`() {
        fun of(vararg statuses: AssistantStepStatus) = AssistantStepRules.status(statuses.mapIndexed { i, s -> step("$i", status = s) })
        assertEquals(AssistantStepStatus.Done, of(AssistantStepStatus.Done, AssistantStepStatus.Done))
        assertEquals(AssistantStepStatus.Failed, of(AssistantStepStatus.Done, AssistantStepStatus.Failed))
        assertEquals(AssistantStepStatus.Running, of(AssistantStepStatus.Failed, AssistantStepStatus.Running, AssistantStepStatus.Queued))
        assertEquals(AssistantStepStatus.Asking, of(AssistantStepStatus.Running, AssistantStepStatus.Asking))
    }

    @Test fun `a run's changes are its finished edits together`() {
        val run = listOf(
            step("a", AssistantToolName.EditFile, changes = LineChanges(12, 3)),
            step("b", AssistantToolName.WriteFile, changes = LineChanges(86, 0)),
            step("c", AssistantToolName.EditFile, AssistantStepStatus.Failed, changes = LineChanges(5, 5)),
            step("d"),
        )
        assertEquals(LineChanges(98, 3), AssistantStepRules.changes(run))
        assertNull(AssistantStepRules.changes(listOf(step("a"))))
    }

    @Test fun `each tool's name is its own, and its phrases count`() {
        assertEquals(AssistantToolName.entries.size, AssistantToolName.entries.map { it.id }.toSet().size)
        assertEquals("ran a search", AssistantToolName.SearchText.did(1))
        assertEquals("started 3 processes", AssistantToolName.StartProcess.did(3))
        assertEquals("Completed", AssistantStepStatus.Done.word)
    }
}
