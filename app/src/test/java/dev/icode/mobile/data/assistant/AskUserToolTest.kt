package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AskUserTool
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantQuestion
import dev.icode.mobile.domain.AssistantReply
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** ask_user (docs/assistant-questions-plan.md): the turn waits for the user's reply, which the model reads. */
class AskUserToolTest {
    @get:Rule val temp = TemporaryFolder()

    private val stateHolder = ICodeStateHolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chats by lazy { AssistantChats(AssistantChatStore(temp.newFolder("chats"))) }
    private val page by lazy { AssistantPage(stateHolder, scope, chats) }
    private val questions by lazy { AssistantQuestions(page) }
    private val call = AssistantCall("c1", "ask_user", "{}", "Asked: Which database?", AssistantToolKind.Read)
    private val step by lazy { AssistantStep(clarify = questions::clarify).also { it.current = call } }
    private val tool by lazy { AskUserTool(step) }

    private val arguments = JSONObject(
        """
        {"questions": [
          {"question": "Which database?", "header": "Database", "multiSelect": false,
           "options": [{"label": "PostgreSQL", "description": "A server"}, {"label": "SQLite", "description": "A file"}]},
          {"question": "Which features?", "header": "Features", "multiSelect": true,
           "options": [{"label": "Auth", "description": "Sign-in"}, {"label": "Search", "description": "Full text"}]}
        ]}
        """.trimIndent(),
    )

    @After fun stop() = scope.cancel()

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(5)
        }
    }

    @Test fun `the questions wait on the page until answered, and the model reads each answer`() = runBlocking {
        val result = scope.async { tool.run(arguments) }
        until { stateHolder.current.assistant.clarification != null }
        val asked = stateHolder.current.assistant.clarification!!
        assertEquals("c1", asked.call.id)
        assertEquals(listOf("Database", "Features"), asked.questions.map { it.header })
        assertEquals(listOf(false, true), asked.questions.map { it.multiple })
        assertEquals("A file", asked.questions[0].options[1].description)
        // Another call's reply is not this one's.
        questions.reply("c0", AssistantReply.Skipped)
        assertFalse(result.isCompleted)
        questions.reply("c1", AssistantReply.Answered(listOf(AssistantQuestion.Answer(listOf("SQLite")), AssistantQuestion.Answer(listOf("Auth")))))
        assertEquals("The user answered:\n- Which database? → SQLite\n- Which features? → Auth", withTimeout(5_000) { result.await() })
        assertNull(stateHolder.current.assistant.clarification)
    }

    @Test fun `a message sent in the chat the questions wait in is the reply, and one sent elsewhere is not`() = runBlocking {
        page.state { it.copy(turn = AssistantTurn("/w/app", "app", "s1", "Thinking…")) }
        assertFalse("nothing asks yet", questions.replyWith("/w/app", "s1", "hello"))
        val result = scope.async { tool.run(arguments) }
        until { stateHolder.current.assistant.clarification != null }
        assertEquals("Waiting for your answer", stateHolder.current.assistant.turn?.step)
        assertFalse(questions.replyWith("/w/app", "s2", "elsewhere"))
        assertTrue(questions.replyWith("/w/app", "s1", "Pick for me"))
        assertEquals("The user replied instead of choosing: \"Pick for me\"", withTimeout(5_000) { result.await() })
        assertEquals("Asked: Which database?", stateHolder.current.assistant.turn?.step)
    }

    @Test fun `stopping the turn ends the wait and takes the questions away`() = runBlocking {
        val result = scope.async { tool.run(arguments) }
        until { stateHolder.current.assistant.clarification != null }
        result.cancelAndJoin()
        assertNull(stateHolder.current.assistant.clarification)
    }

    @Test fun `a call the rules refuse says what to fix, and asks nothing`() = runBlocking {
        fun refused(json: String): String = try {
            runBlocking { tool.run(JSONObject(json)) }
            fail("asked")
            ""
        } catch (refusal: AssistantToolException) {
            refusal.message.orEmpty()
        }
        assertEquals(
            "Question 1 has 1 options: give 2 to 4. The user can always answer in their own words.",
            refused("""{"questions":[{"question":"Which?","header":"One","options":[{"label":"A","description":""}]}]}"""),
        )
        assertEquals("questions is a list of {question, header, options: [{label, description}], multiSelect}.", refused("""{"questions":"none"}"""))
        assertNull(stateHolder.current.assistant.clarification)
    }

    @Test fun `a list sent as text is read as the list`() {
        val asText = JSONObject().put("questions", arguments.getJSONArray("questions").toString())
        assertEquals(AskUserTool.questionsOf(arguments), AskUserTool.questionsOf(asText))
        assertEquals("Asked: Which database? (+1 more)", tool.summary(arguments))
        assertEquals("Question", tool.summary(JSONObject()))
    }
}
