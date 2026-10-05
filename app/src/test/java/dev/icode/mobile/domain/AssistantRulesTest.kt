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
        val withFiles = AssistantMessage.User("Why?", listOf(AssistantAttachment("src/a.kt", text = "x")))
        val entries = AssistantRules.entries(AssistantState(messages = listOf(withFiles, AssistantMessage.Compacted("Summary"))))
        assertEquals(listOf("src/a.kt"), (entries[0] as AssistantEntry.Said).attachments.map { it.name })
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

    @Test fun `a reply's reasoning comes before its answer and its steps, and only where there is some`() {
        val thought = AssistantMessage.Reply("Looking.", listOf(call("1")), reasoning = "The build fails in a.kt.")
        val entries = AssistantRules.entries(AssistantState(messages = AssistantRules.closed(listOf(asked, thought))))
        assertEquals(
            listOf(AssistantEntry.Said::class, AssistantEntry.Reasoning::class, AssistantEntry.Answer::class, AssistantEntry.Step::class),
            entries.map { it::class },
        )
        assertEquals("The build fails in a.kt.", (entries[1] as AssistantEntry.Reasoning).text)
        assertTrue(AssistantRules.entries(AssistantState(messages = listOf(asked, AssistantMessage.Reply("Done.")))).none { it is AssistantEntry.Reasoning })
    }

    @Test fun `a reply being written shows under the chat, its reasoning held open, and only in its own chat`() {
        val live = AssistantLive(reasoning = "Check a.kt.", text = "It starts")
        val writing = AssistantState(workspace = "/w/app", session = "s1", messages = listOf(asked), turn = AssistantTurn("/w/app", "app", "s1", "Thinking…", live))
        assertEquals(
            listOf(AssistantEntry.Said("m0", asked.text), AssistantEntry.Reasoning("live-reasoning", "Check a.kt.", live = true), AssistantEntry.Answer("live-answer", "It starts")),
            AssistantRules.entries(writing),
        )
        // Before any answer, the reasoning alone; in another chat, nothing of it.
        assertEquals(1, AssistantRules.entries(writing.copy(turn = writing.turn!!.copy(live = live.copy(text = "")))).count { it.key.startsWith("live") })
        assertTrue(AssistantRules.entries(writing.copy(session = "s2")).none { it.key.startsWith("live") })
    }

    @Test fun `an OpenAI-compatible provider at OpenRouter's address is pointed to the OpenRouter type`() {
        val hint = "Choose the OpenRouter type for OpenRouter: only it shows replies as they are written, and the models' reasoning"
        assertEquals(hint, AssistantRules.providerHint(AssistantProviderKind.OpenAICompatible, " https://openrouter.ai/api/v1 "))
        assertEquals(hint, AssistantRules.providerHint(AssistantProviderKind.OpenAICompatible, "https://eu.OpenRouter.ai/api/v1"))
        assertNull(AssistantRules.providerHint(AssistantProviderKind.OpenAICompatible, "https://api.deepseek.com/v1"))
        assertNull(AssistantRules.providerHint(AssistantProviderKind.OpenAICompatible, "https://notopenrouter.ai/v1"))
        assertNull(AssistantRules.providerHint(AssistantProviderKind.OpenAICompatible, "not an address"))
        assertNull(AssistantRules.providerHint(AssistantProviderKind.OpenRouter, "https://openrouter.ai/api/v1"))
    }

    @Test fun `the meter always says something, from the first message on`() {
        assertEquals("84k / 200k", AssistantRules.contextLabel(84_000, 200_000))
        assertEquals("0 / 200k", AssistantRules.contextLabel(null, 200_000))
        assertEquals("12k tokens", AssistantRules.contextLabel(12_000, null))
        assertEquals("0 tokens", AssistantRules.contextLabel(null, null))
    }

    @Test fun `a model is summed up by what is known of it`() {
        assertEquals(
            "200k context · reasons · images · PDFs",
            AssistantRules.modelSummary(AssistantModel("claude", 200_000, efforts = listOf(AssistantEffort.High), images = true, documents = true)),
        )
        assertEquals("", AssistantRules.modelSummary(AssistantModel("custom")))
        assertEquals("128k context · reasons · images", AssistantRules.modelSummary(AssistantModelSpec("qwen", 128_000, reasoning = true, images = true)))
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

    @Test fun `a provider's form says what is missing, in order`() {
        val one = listOf(AssistantModelSpec("m"))
        fun problem(kind: AssistantProviderKind, name: String = "P", url: String = "", typed: Boolean = false, saved: Boolean = false, models: List<AssistantModelSpec> = one) =
            AssistantRules.providerProblem(kind, name, url, typed, saved, models)
        assertEquals("Enter a name", problem(AssistantProviderKind.OpenAI, name = " "))
        assertEquals("Enter the server's address", problem(AssistantProviderKind.OpenAICompatible, url = " "))
        assertEquals("Enter an address that starts with https:// or http://", problem(AssistantProviderKind.Ollama, url = "localhost:11434"))
        assertEquals("Enter the API key", problem(AssistantProviderKind.OpenAI))
        assertEquals("Add at least one model", problem(AssistantProviderKind.OpenAI, saved = true, models = emptyList()))
        assertEquals("A model ID has no spaces", problem(AssistantProviderKind.OpenAI, typed = true, models = listOf(AssistantModelSpec("gpt 5"))))
        assertEquals("gpt-5 is listed twice", problem(AssistantProviderKind.OpenAI, typed = true, models = listOf(AssistantModelSpec("gpt-5"), AssistantModelSpec(" gpt-5"))))
        assertNull(problem(AssistantProviderKind.OpenAI, saved = true))
        assertNull(problem(AssistantProviderKind.Ollama, url = "http://127.0.0.1:11434"))
        assertNull(problem(AssistantProviderKind.OpenAICompatible, url = "https://api.deepseek.com/v1", typed = true))
        assertEquals("The context window is a number of tokens", AssistantRules.modelOptionsProblem("big", ""))
        assertEquals("The output limit is a number of tokens", AssistantRules.modelOptionsProblem("", "-1"))
        assertNull(AssistantRules.modelOptionsProblem("128k", ""))
    }

    @Test fun `a file's kind is read from its type, or its name when the type is unknown`() {
        assertEquals(AssistantAttachmentKind.Image, AssistantRules.attachmentKind("IMG.HEIC", "image/heic"))
        assertEquals(AssistantAttachmentKind.Image, AssistantRules.attachmentKind("photo.jpeg", null))
        assertEquals(AssistantAttachmentKind.Document, AssistantRules.attachmentKind("x", "application/pdf"))
        assertEquals(AssistantAttachmentKind.Document, AssistantRules.attachmentKind("spec.PDF", null))
        assertEquals(AssistantAttachmentKind.Text, AssistantRules.attachmentKind("App.kt", null))
        assertEquals(AssistantAttachmentKind.Text, AssistantRules.attachmentKind("data.json", "application/json"))
        val text = AssistantModel("t")
        assertEquals("t does not read images — choose a model that does", AssistantRules.attachmentProblem(listOf(AssistantAttachmentKind.Image), text))
        assertNull(AssistantRules.attachmentProblem(listOf(AssistantAttachmentKind.Text), text))
        assertNull(AssistantRules.attachmentProblem(listOf(AssistantAttachmentKind.Image), AssistantModel("v", images = true)))
    }

    @Test fun `a model's spec keeps only its ID when Koog knows it`() {
        assertEquals(AssistantModelSpec("gpt-5"), AssistantModel("gpt-5", 400_000, 128_000, known = true).spec)
        assertEquals(
            AssistantModelSpec("local", 32_000, 4_000, images = true, reasoning = true),
            AssistantModel("local", 32_000, 4_000, listOf(AssistantEffort.On), images = true).spec,
        )
    }

    @Test fun `an address is stored trimmed, and only where the provider asks for one`() {
        assertEquals("https://api.groq.com/openai/v1", AssistantRules.storedBaseUrl(AssistantProviderKind.OpenAICompatible, " https://api.groq.com/openai/v1/ "))
        assertNull(AssistantRules.storedBaseUrl(AssistantProviderKind.OpenAI, "https://elsewhere"))
        assertEquals("app", AssistantRules.workspaceName("/data/files/workspaces/app/"))
    }

    @Test fun `a key never shows in an intent's text`() {
        val save = ICodeIntent.SaveAssistantProvider("", AssistantProviderKind.OpenAI, "OpenAI", "", "sk-secret", listOf(AssistantModelSpec("gpt-5")))
        assertTrue("sk-secret" !in save.toString())
        assertTrue("sk-secret" !in ICodeIntent.ListAssistantModels("", AssistantProviderKind.OpenAI, "", "sk-secret").toString())
    }
}
