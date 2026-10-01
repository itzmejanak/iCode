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

    @Test fun `a call left open is answered as stopped, in its reply's order`() {
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

    @Test fun `results belong to the reply right before them, though another reply's calls had the same IDs`() {
        // Ollama names a call by a hash of its tool and arguments: the same read twice has the same ID.
        val again = AssistantMessage.Reply("Again.", listOf(call("1")))
        val chat = listOf(
            asked, AssistantMessage.Reply("", listOf(call("1"))),
            AssistantMessage.Results(listOf(AssistantResult("1", "read_file", "first", failed = false))),
            again,
        )
        val closed = AssistantRules.closed(chat)
        assertEquals(AssistantRules.STOPPED, (closed[4] as AssistantMessage.Results).results.single().output)
        val steps = AssistantRules.entries(AssistantState(messages = closed)).filterIsInstance<AssistantEntry.Step>()
        assertEquals(listOf("first", AssistantRules.STOPPED), steps.map { it.output })
    }

    @Test fun `results that answer no reply are dropped`() {
        val stray = AssistantMessage.Results(listOf(AssistantResult("9", "read_file", "x", failed = false)))
        assertEquals(listOf(asked), AssistantRules.closed(listOf(stray, asked)))
    }

    @Test fun `steps show what is asked, running and waiting, then what came of them`() {
        val turn = AssistantTurn("/w/app", "app", "s1", "Read a.kt")
        val running = AssistantState(workspace = "/w/app", session = "s1", messages = listOf(asked, reply), turn = turn)
        val steps = AssistantRules.entries(running)
        assertEquals(listOf("Said", "Answer", "Step", "Step"), steps.map { it.javaClass.simpleName })
        assertEquals(AssistantStepStatus.Running, (steps[2] as AssistantEntry.Step).status)
        assertEquals(AssistantStepStatus.Queued, (steps[3] as AssistantEntry.Step).status)

        val asking = running.copy(approval = AssistantApproval(call("1"), "Read a.kt"))
        assertEquals(AssistantStepStatus.Asking, (AssistantRules.entries(asking)[2] as AssistantEntry.Step).status)

        val done = AssistantRules.entries(running.copy(messages = AssistantRules.closed(listOf(asked, reply)), turn = null))
        assertEquals(AssistantStepStatus.Failed, (done[2] as AssistantEntry.Step).status)
        assertEquals(AssistantRules.STOPPED, (done[2] as AssistantEntry.Step).output)
    }

    @Test fun `another chat's turn does not run this chat's steps`() {
        val otherChat = AssistantState(workspace = "/w/app", session = "s1", messages = listOf(asked, reply), turn = AssistantTurn("/w/app", "app", "s2", "…"))
        assertEquals(AssistantStepStatus.Failed, (AssistantRules.entries(otherChat)[2] as AssistantEntry.Step).status)
        val otherWorkspace = otherChat.copy(turn = AssistantTurn("/w/other", "other", "s1", "…"))
        assertEquals(AssistantStepStatus.Failed, (AssistantRules.entries(otherWorkspace)[2] as AssistantEntry.Step).status)
    }

    @Test fun `files sent with a message show by their paths, and a compaction as its own row`() {
        val withFiles = AssistantMessage.User("Why?", listOf(AssistantAttachment("src/a.kt", "x")))
        val entries = AssistantRules.entries(AssistantState(messages = listOf(withFiles, AssistantMessage.Compacted("Summary"))))
        assertEquals(listOf("src/a.kt"), (entries[0] as AssistantEntry.Said).attachments)
        assertEquals("Summary", (entries[1] as AssistantEntry.Compacted).summary)
    }

    @Test fun `the model is sent the last summary and what came after it`() {
        val summary = AssistantMessage.Compacted("The user wants the build fixed.")
        val after = AssistantMessage.User("Now the tests")
        val sent = AssistantRules.forModel(listOf(asked, reply, AssistantMessage.Compacted("old"), summary, after))
        assertEquals("The user wants the build fixed.", sent.summary)
        assertEquals(listOf(after), sent.messages)
        val never = AssistantRules.forModel(listOf(asked, reply))
        assertNull(never.summary)
        assertEquals(3, never.messages.size)
    }

    @Test fun `the meter is the last request's share of the window, when both are known`() {
        assertEquals(0.5f, AssistantRules.contextShare(100_000, 200_000))
        assertEquals(1f, AssistantRules.contextShare(300_000, 200_000))
        assertNull(AssistantRules.contextShare(null, 200_000))
        assertNull(AssistantRules.contextShare(100, null))
    }

    @Test fun `a chat is titled by its first line, cut short`() {
        assertEquals("Fix the build", AssistantRules.sessionTitle("  Fix the build\nand the tests"))
        assertEquals("New chat", AssistantRules.sessionTitle("   "))
        val long = AssistantRules.sessionTitle("a".repeat(100))
        assertEquals(61, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test fun `an effort is sent only when the model offers it`() {
        val reasons = AssistantModel("o", efforts = listOf(AssistantEffort.Low, AssistantEffort.High))
        assertEquals(AssistantEffort.High, AssistantRules.effortFor(AssistantEffort.High, reasons))
        assertNull(AssistantRules.effortFor(AssistantEffort.Max, reasons))
        assertNull(AssistantRules.effortFor(AssistantEffort.High, AssistantModel("plain")))
        assertNull(AssistantRules.effortFor(null, reasons))
    }

    @Test fun `token counts read as typed and show as people read them`() {
        assertEquals(200_000L, AssistantRules.tokens("200k"))
        assertEquals(1_048_576L, AssistantRules.tokens("1048576"))
        assertEquals(1_000_000L, AssistantRules.tokens(" 1M "))
        assertEquals(128_000L, AssistantRules.tokens("128,000"))
        assertNull(AssistantRules.tokens("lots"))
        assertNull(AssistantRules.tokens("0"))
        assertNull(AssistantRules.tokens("-5"))
        assertEquals("200k", AssistantRules.tokenCount(200_000))
        assertEquals("1m", AssistantRules.tokenCount(1_048_576))
        assertEquals("1.5m", AssistantRules.tokenCount(1_500_000))
        assertEquals("8.1k", AssistantRules.tokenCount(8_192))
        assertEquals("950", AssistantRules.tokenCount(950))
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
        assertEquals(
            "The context window is a number of tokens",
            AssistantRules.settingsProblem(AssistantProviderKind.OpenAICompatible, "https://x/v1", "m", true, false, contextWindow = "big"),
        )
        assertEquals(
            "The output limit is a number of tokens",
            AssistantRules.settingsProblem(AssistantProviderKind.OpenAICompatible, "https://x/v1", "m", true, false, maxOutput = "-1"),
        )
    }

    @Test fun `an address is stored trimmed, and only where the provider asks for one`() {
        assertEquals("https://api.groq.com/openai/v1", AssistantRules.storedBaseUrl(AssistantProviderKind.OpenAICompatible, " https://api.groq.com/openai/v1/ "))
        assertNull(AssistantRules.storedBaseUrl(AssistantProviderKind.OpenAI, "https://elsewhere"))
        assertEquals("app", AssistantRules.workspaceName("/data/files/workspaces/app/"))
    }

    @Test fun `a key never shows in an intent's text`() {
        val save = WorkbenchIntent.SaveAssistantSettings(AssistantProviderKind.OpenAI, "", "gpt-5", "sk-secret")
        assertTrue("sk-secret" !in save.toString())
    }
}
