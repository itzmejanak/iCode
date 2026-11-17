package dev.icode.mobile.domain

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.phone.PhoneAccess
import dev.icode.mobile.domain.phone.PhoneOverlay
import dev.icode.mobile.domain.voice.SpokenReplies
import dev.icode.mobile.domain.voice.SpokenTurn
import dev.icode.mobile.domain.voice.VoiceCue
import dev.icode.mobile.domain.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The model's questions to the user (docs/assistant-and-mcp.md): what may be asked, and how it and its reply are said. */
class AssistantQuestionRulesTest {
    private val database = AssistantQuestion(
        "Database",
        "Which database should the app use?",
        listOf(AssistantQuestion.Option("PostgreSQL (Recommended)", "A server, for many users"), AssistantQuestion.Option("SQLite", "A file, on the phone")),
        multiple = false,
    )
    private val features = AssistantQuestion(
        "Features",
        "Which features do you want?",
        listOf(AssistantQuestion.Option("Auth", "Sign-in"), AssistantQuestion.Option("Search", "Full text"), AssistantQuestion.Option("Export", "CSV")),
        multiple = true,
    )
    private val call = AssistantCall("c1", AssistantToolName.AskUser.id, "{}", "Asked: Which database should the app use?", AssistantToolKind.Read)
    private val asked = AssistantClarification(call, listOf(database, features))

    @Test fun `one to four questions of two to four options, each named shortly, with labels of their own`() {
        assertNull(AssistantQuestionRules.problem(listOf(database, features)))
        assertEquals("Ask 1 to 4 questions in one call, not 0.", AssistantQuestionRules.problem(emptyList()))
        assertEquals("Ask 1 to 4 questions in one call, not 5.", AssistantQuestionRules.problem(List(5) { database.copy(text = "Q$it?") }))
        assertEquals(
            "Question 1 has 1 options: give 2 to 4. The user can always answer in their own words.",
            AssistantQuestionRules.problem(listOf(database.copy(options = database.options.take(1)))),
        )
        assertEquals("Question 1's header \"Which database\" is longer than 12 characters.", AssistantQuestionRules.problem(listOf(database.copy(header = "Which database"))))
        assertEquals("Question 1 has no header: name it in a word or two.", AssistantQuestionRules.problem(listOf(database.copy(header = " "))))
        assertEquals("Question 2 has no question text.", AssistantQuestionRules.problem(listOf(database, features.copy(text = ""))))
        assertEquals(
            "Question 1 has two options with the same label.",
            AssistantQuestionRules.problem(listOf(database.copy(options = listOf(database.options[1], database.options[1])))),
        )
        assertEquals("Two questions ask the same thing.", AssistantQuestionRules.problem(listOf(database, database.copy(header = "Again"))))
    }

    @Test fun `the model reads each question with its answer, the user's own words, or that they did not answer`() {
        val answered = AssistantReply.Answered(
            listOf(AssistantQuestion.Answer(chosen = listOf("SQLite")), AssistantQuestion.Answer(chosen = listOf("Auth", "Search"), own = " and dark mode ")),
        )
        assertEquals(
            "The user answered:\n" +
                "- Which database should the app use? → SQLite\n" +
                "- Which features do you want? → Auth, Search, in their own words: \"and dark mode\"",
            AssistantQuestionRules.result(asked.questions, answered),
        )
        assertEquals("The user replied instead of choosing: \"use what you think\"", AssistantQuestionRules.result(asked.questions, AssistantReply.Said(" use what you think ")))
        assertTrue(AssistantQuestionRules.result(asked.questions, AssistantReply.Skipped).startsWith("The user closed the questions without answering."))
    }

    @Test fun `Submit waits for every question, a choice or words of their own`() {
        val one = AssistantQuestion.Answer(chosen = listOf("SQLite"))
        assertFalse(AssistantQuestionRules.complete(asked.questions, listOf(one, AssistantQuestion.Answer())))
        assertFalse(AssistantQuestionRules.complete(asked.questions, listOf(one, AssistantQuestion.Answer(own = "  "))))
        assertTrue(AssistantQuestionRules.complete(asked.questions, listOf(one, AssistantQuestion.Answer(own = "none"))))
    }

    @Test fun `its step, its notification, its bubble and its voice say the first question`() {
        assertEquals("Asked: Which database should the app use? (+1 more)", AssistantQuestionRules.summary(asked.questions))
        assertEquals("Asked: Which database should the app use?", AssistantQuestionRules.summary(listOf(database)))
        assertEquals("Which database should the app use? (1 of 2)", AssistantQuestionRules.asked(asked))
        assertEquals(
            "Which database should the app use? PostgreSQL (Recommended) or SQLite? And 1 more on the screen.",
            AssistantQuestionRules.spoken(asked),
        )
    }

    @Test fun `while it asks, its call waits for an answer, the turn asks, and a message sent there is the reply`() {
        val turn = AssistantTurn("/w/app", "app", "s1", "Waiting for your answer")
        val reply = AssistantMessage.Reply("", listOf(call))
        val waiting = AssistantState(workspace = "/w/app", session = "s1", messages = listOf(AssistantMessage.User("build it"), reply), turns = listOf(turn.copy(clarification = asked)))
        val step = AssistantRules.entries(waiting).filterIsInstance<AssistantEntry.Step>().single()
        assertEquals(AssistantStepStatus.Asking, step.status)
        assertTrue(waiting.asking)
        assertTrue(waiting.replying)
        assertFalse(waiting.copy(session = "s2").replying)
        assertFalse(waiting.changeTurn("/w/app", "s1") { it.copy(clarification = null) }.asking)
        val bubble = PhoneOverlay.of(waiting, VoiceState(), PhoneAccess(service = true, bubble = true), iCodeOnScreen = false, muted = false)
        assertTrue(bubble.asking)
        assertTrue(bubble.replying)
    }

    @Test fun `out of view it is the notification's question, and an aloud turn says it once`() {
        val turn = AssistantTurn("/w/app", "app", "s1", "Waiting for your answer", aloud = true)
        val working = ICodeStateHolder.emptySnapshot().copy(assistant = AssistantState(turns = listOf(turn.copy(clarification = asked))))
        assertEquals("The assistant asks: Which database should the app use? (1 of 2)", BackgroundWorkRules.of(working).single().question)

        val reader = SpokenTurn()
        val before = AssistantState(workspace = "/w/app", session = "s1", turns = listOf(turn))
        reader.next(AssistantState(workspace = "/w/app", session = "s1"), before, SpokenReplies.InBubble)
        val after = before.changeTurn("/w/app", "s1") { it.copy(clarification = asked) }
        val said = reader.next(before, after, SpokenReplies.InBubble).filterIsInstance<VoiceCue.Say>().single()
        assertTrue(said.text.startsWith("Which database should the app use?"))
        assertEquals(emptyList<VoiceCue>(), reader.next(after, after, SpokenReplies.InBubble).filterIsInstance<VoiceCue.Say>())
    }

    @Test fun `a retry goes on from the chat once the turn has answered anything`() {
        assertFalse(AssistantRules.replied(emptyList()))
        assertFalse(AssistantRules.replied(listOf(AssistantMessage.User("hi"))))
        assertTrue(AssistantRules.replied(listOf(AssistantMessage.User("hi"), AssistantMessage.Reply("", listOf(call)))))
        assertTrue(AssistantRules.replied(listOf(AssistantMessage.User("hi"), AssistantMessage.Results(emptyList()))))
    }
}
