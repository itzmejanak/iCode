package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRulesTest {
    private fun call(id: String, kind: AssistantToolKind = AssistantToolKind.Read) =
        AssistantCall(id, "read_file", """{"path":"a.kt"}""", "Read a.kt", kind)

    private val asked = AssistantMessage.User("Fix the build")
    private val reply = AssistantMessage.Reply("Looking.", listOf(call("1"), call("2", AssistantToolKind.Change)))

    @Test fun `a call left open is answered as stopped, joined to the results it has`() {
        val partial = AssistantMessage.Results(listOf(AssistantResult("1", "read_file", "text", failed = false)))
        val closed = AssistantRules.closed(listOf(asked, reply, partial, AssistantMessage.User("Go on")))
        assertEquals(4, closed.size)
        val results = (closed[2] as AssistantMessage.Results).results
        assertEquals(listOf("1", "2"), results.map { it.callId })
        assertEquals(AssistantRules.STOPPED, results[1].output)
        assertTrue(results[1].failed)
    }

    @Test fun `a reply whose calls have no results at all gets them after it`() {
        val closed = AssistantRules.closed(listOf(asked, reply))
        assertEquals(3, closed.size)
        assertEquals(listOf("1", "2"), (closed[2] as AssistantMessage.Results).results.map { it.callId })
        assertEquals(closed, AssistantRules.closed(closed))
    }

    @Test fun `steps show what is asked, running and waiting, then what came of them`() {
        val turn = AssistantTurn("/w/app", "app", "Read a.kt")
        val running = AssistantState(workspace = "/w/app", messages = listOf(asked, reply), turn = turn, streaming = "Now")
        val steps = AssistantRules.entries(running)
        assertEquals(listOf("Said", "Answer", "Step", "Step", "Answer"), steps.map { it.javaClass.simpleName })
        assertEquals(AssistantStepStatus.Running, (steps[2] as AssistantEntry.Step).status)
        assertEquals(AssistantStepStatus.Queued, (steps[3] as AssistantEntry.Step).status)
        assertTrue((steps[4] as AssistantEntry.Answer).streaming)

        val asking = running.copy(approval = AssistantApproval(call("1"), "Read a.kt"))
        assertEquals(AssistantStepStatus.Asking, (AssistantRules.entries(asking)[2] as AssistantEntry.Step).status)

        val done = AssistantRules.entries(running.copy(messages = AssistantRules.closed(listOf(asked, reply)), turn = null))
        assertEquals(AssistantStepStatus.Failed, (done[2] as AssistantEntry.Step).status)
        assertEquals(AssistantRules.STOPPED, (done[2] as AssistantEntry.Step).output)
        assertTrue("streamed text is not shown once the turn ends", done.none { it is AssistantEntry.Answer && it.streaming })
    }

    @Test fun `another workspace's turn does not run this chat's steps`() {
        val elsewhere = AssistantState(workspace = "/w/app", messages = listOf(asked, reply), turn = AssistantTurn("/w/other", "other", "…"))
        assertEquals(AssistantStepStatus.Failed, (AssistantRules.entries(elsewhere)[2] as AssistantEntry.Step).status)
    }

    @Test fun `the settings form says what is missing, in order`() {
        val problem = { kind: AssistantProviderKind, url: String, model: String, typed: Boolean, saved: Boolean ->
            AssistantRules.settingsProblem(kind, url, model, typed, saved)
        }
        assertEquals("Enter the server's address", problem(AssistantProviderKind.OpenAICompatible, " ", "m", false, false))
        assertEquals("Enter an address that starts with https:// or http://", problem(AssistantProviderKind.Ollama, "localhost:11434", "m", false, false))
        assertEquals("Enter a model", problem(AssistantProviderKind.Anthropic, "", "", true, false))
        assertEquals("A model ID has no spaces", problem(AssistantProviderKind.OpenAI, "", "gpt 5", true, false))
        assertEquals("Enter the API key", problem(AssistantProviderKind.OpenAI, "", "gpt-5", false, false))
        assertNull(problem(AssistantProviderKind.OpenAI, "", "gpt-5", false, true))
        assertNull(problem(AssistantProviderKind.Ollama, "http://127.0.0.1:11434", "qwen3:0.6b", false, false))
        assertNull(problem(AssistantProviderKind.OpenAICompatible, "https://api.deepseek.com/v1", "deepseek-chat", true, false))
    }

    @Test fun `an address is stored trimmed, and only where the provider asks for one`() {
        assertEquals("https://api.groq.com/openai/v1", AssistantRules.storedBaseUrl(AssistantProviderKind.OpenAICompatible, " https://api.groq.com/openai/v1/ "))
        assertNull(AssistantRules.storedBaseUrl(AssistantProviderKind.OpenAI, "https://elsewhere"))
        assertEquals("app", AssistantRules.workspaceName("/data/files/workspaces/app/"))
    }

    @Test fun `a key never shows in an intent's text`() {
        val save = WorkbenchIntent.SaveAssistantSettings(AssistantProviderKind.OpenAI, "", "gpt-5", "sk-secret", true)
        assertTrue("sk-secret" !in save.toString())
    }
}
