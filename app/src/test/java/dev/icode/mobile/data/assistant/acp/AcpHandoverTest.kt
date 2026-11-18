package dev.icode.mobile.data.assistant.acp

import dev.icode.mobile.domain.AssistantMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conversation so far, as a new session with an agent is sent it. */
class AcpHandoverTest {
    /** Twenty exchanges, each about 400 characters. */
    private val history = (1..20).flatMap { n -> listOf(AssistantMessage.User("Question $n " + "q".repeat(200)), AssistantMessage.Reply("Answer $n " + "a".repeat(200))) }
    private val summarised = mutableListOf<List<AssistantMessage>>()
    private val summary: suspend (List<AssistantMessage>) -> String? = { older -> summarised += older; "They talked about questions 1 to ${older.size / 2}." }

    @Test fun `a chat goes whole while it fits the agent's window, or while the window is not known`() = runBlocking {
        val whole = AcpHandover.of(history, null, summary)
        assertEquals(whole, AcpHandover.of(history, 100_000, summary))
        assertTrue(whole.contains("Question 1 ") && whole.contains("Answer 20 "))
        assertTrue("Nothing summarized", summarised.isEmpty())
    }

    @Test fun `a chat the window would not fit goes as a summary of the older part and the newest part in full`() = runBlocking {
        val sent = AcpHandover.of(history, 2_000, summary)
        val older = summarised.single()
        assertTrue("The older part is summarized from its start", older.first() == history.first() && older.isNotEmpty())
        assertTrue(sent.startsWith("A summary of the earlier part of this chat:\n\nThey talked about questions 1 to ${older.size / 2}."))
        assertTrue("The newest part begins with something the user said", sent.contains("Question ${older.size / 2 + 1} "))
        assertTrue(sent.contains("Answer 20 "))
        assertFalse("What was summarized is not sent again", sent.contains("Question 1 "))
        assertTrue("It fits the room left", sent.length / AcpHandover.CHARS_PER_TOKEN < 2_000)
    }

    @Test fun `with no model to write the summary, the newest part goes alone and the agent is told the rest was left out`() = runBlocking {
        val sent = AcpHandover.of(history, 2_000) { null }
        assertTrue(sent.startsWith(AcpHandover.LEFT_OUT))
        assertTrue(sent.contains("Answer 20 "))
        assertFalse(sent.contains("Question 1 "))
        val failing = AcpHandover.of(history, 2_000) { throw IllegalStateException("No connection") }
        assertTrue("A summary that fails is as none", failing.startsWith(AcpHandover.LEFT_OUT))
    }
}
