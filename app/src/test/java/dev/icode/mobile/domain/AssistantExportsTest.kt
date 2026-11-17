package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantExportsTest {
    @Test fun `a chat is shared as its transcript, with its steps' answers`() {
        val session = AssistantSession("s-1", "Rename x", 123L)
        val text = AssistantExports.transcript(
            session,
            "app",
            listOf(
                AssistantMessage.User("Rename x", listOf(AssistantAttachment("notes.txt", text = "keep"))),
                AssistantMessage.Reply(
                    "Renamed.",
                    calls = listOf(
                        AssistantCall("c1", "edit_file", "{}", "Rename x", AssistantToolKind.Change, file = "a.kt"),
                        AssistantCall("c2", "run_command", "{}", "Run the tests", AssistantToolKind.Command, command = "./gradlew test"),
                    ),
                ),
                AssistantMessage.Results(
                    listOf(
                        AssistantResult("c1", "edit_file", "done", failed = false, changes = LineChanges(1, 1)),
                        AssistantResult("c2", "run_command", "BUILD SUCCESSFUL", failed = false),
                    ),
                ),
            ),
        )
        assertTrue(text.startsWith("# Rename x"))
        assertTrue("app" in text)
        assertTrue("## You" in text)
        assertTrue("Rename x" in text)
        assertTrue("notes.txt" in text)
        assertTrue("## Assistant" in text)
        assertTrue("### Rename x" in text)
        assertTrue("+1 −1" in text)
        assertTrue("### Run the tests" in text)
        assertTrue("./gradlew test" in text)
        assertTrue("BUILD SUCCESSFUL" in text)
    }

    @Test fun `reasoning and a compaction are part of it, and a failed step is said failed`() {
        val session = AssistantSession("s-1", "Why", 123L)
        val text = AssistantExports.transcript(
            session,
            "app",
            listOf(
                AssistantMessage.User("Why"),
                AssistantMessage.Reply("Because.", reasoning = "The file says so."),
                AssistantMessage.Reply(
                    "oops",
                    calls = listOf(AssistantCall("c1", "edit_file", "{}", "Edit b.kt", AssistantToolKind.Change, file = "b.kt")),
                ),
                AssistantMessage.Results(listOf(AssistantResult("c1", "edit_file", "no", failed = true))),
                AssistantMessage.Compacted("Summary of 4"),
            ),
        )
        assertTrue("## Thinking" in text)
        assertTrue("> The file says so." in text)
        assertTrue("```\nno\n```" in text)
        assertTrue("> **Compacted:** Summary of 4" in text)
    }

    @Test fun `a long chat is shared whole, nothing cut off`() {
        val long = "x".repeat(250_000)
        val text = AssistantExports.transcript(AssistantSession("s-1", "Long", 123L), "app", listOf(AssistantMessage.User("Start"), AssistantMessage.Reply(long)))
        assertTrue(long in text)
    }

    @Test fun `a transcript's file is named by its chat, safe for any phone or computer`() {
        assertEquals("Fix the login test.md", AssistantExports.fileName("Fix the login test"))
        assertEquals("a b c d.md", AssistantExports.fileName("a/b\\c:d"))
        assertEquals("Chat.md", AssistantExports.fileName(" \n "))
        assertEquals(83, AssistantExports.fileName("y".repeat(200)).length)
    }

    @Test fun `an empty chat is only its heading`() {
        val text = AssistantExports.transcript(AssistantSession("s-1", "New", 123L), "app", emptyList())
        assertEquals("# New\n\nWorked in app\n\n", text)
    }
}