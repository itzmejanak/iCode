package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantInstructions
import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.assistant.AssistantTurnRequest
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Koog's real standard agent runs here; only the provider is scripted. */
class KoogAssistantEngineTest {
    private val connection = AssistantConnection(AssistantProviderKind.OpenAI, null, "sk-test")
    private val model = KoogModels.model(AssistantProviderKind.OpenAI, AssistantModelSpec("gpt-5"))

    private class ScriptedClient(
        private val replies: List<Message.Assistant>,
        private val provider: LLMProvider = LLMProvider.OpenAI,
        private val listed: List<LLModel> = emptyList(),
    ) : LLMClient() {
        val prompts = mutableListOf<Prompt>()
        val tools = mutableListOf<List<ToolDescriptor>>()
        var closed = false
        var hang: CompletableDeferred<Unit>? = null
        var failWith: Throwable? = null

        override fun llmProvider(): LLMProvider = provider
        override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
            prompts += prompt
            this.tools += tools
            failWith?.let { throw it }
            hang?.let {
                it.complete(Unit)
                awaitCancellation()
            }
            return replies[minOf(prompts.size, replies.size) - 1]
        }
        override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = error("whole replies only")
        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("no moderation")
        override suspend fun models(): List<LLModel> = listed
        override fun close() {
            closed = true
        }
    }

    private class Recorder : AssistantEvents {
        val events = mutableListOf<Any>()
        override fun replied(reply: AssistantMessage.Reply) {
            events += reply
        }
        override fun calling(call: AssistantCall) {
            events += "calling:${call.id}"
        }
        override fun answered(result: AssistantResult) {
            events += result
        }
        override fun used(tokens: Long) {
            events += "used:$tokens"
        }
    }

    private class ReadTool(private val refusal: String? = null) : AssistantTool {
        val seen = mutableListOf<String>()
        override val name = "read_file"
        override val description = "Reads a file"
        override val parameters = listOf(AssistantParameter("path", "The file"))
        override val kind = AssistantToolKind.Read
        override fun summary(arguments: JSONObject) = "Read ${arguments.optString("path")}"
        override suspend fun run(arguments: JSONObject): String {
            seen += arguments.getString("path")
            refusal?.let { throw AssistantToolException(it) }
            return "fun main() {}"
        }
    }

    private fun reply(vararg parts: MessagePart.ResponsePart, input: Int? = null, output: Int? = null) =
        Message.Assistant(parts.toList(), ResponseMetaInfo.Empty.copy(inputTokensCount = input, outputTokensCount = output))

    private fun call(id: String?, args: String = """{"path":"a.kt"}""", tool: String = "read_file") = MessagePart.Tool.Call(id = id, tool = tool, args = args)

    private val callingRead = reply(MessagePart.Text("Reading"), call("call_1"), input = 1_200, output = 40)
    private val done = reply(MessagePart.Text("Done"), input = 1_300, output = 20)

    private fun turn(
        tool: AssistantTool,
        history: List<AssistantMessage> = emptyList(),
        input: AssistantMessage.User = AssistantMessage.User("What does a.kt do?"),
        maxSteps: Int = 40,
    ) = AssistantTurnRequest(connection, model, null, "You are iCode's assistant.", history, input, listOf(tool), maxSteps)

    @Test fun `a reply's tool call runs, its result goes back, and the next reply ends the turn`() = runBlocking {
        val client = ScriptedClient(listOf(callingRead, done))
        val tool = ReadTool()
        val events = Recorder()
        val history = listOf(AssistantMessage.User("Hi"), AssistantMessage.Reply("Hello."))
        KoogAssistantEngine { client }.run(turn(tool, history), events)

        val call = AssistantCall("call_1", "read_file", """{"path":"a.kt"}""", "Read a.kt", AssistantToolKind.Read)
        assertEquals(
            listOf(
                "used:1240",
                AssistantMessage.Reply("Reading", listOf(call)),
                "calling:call_1",
                AssistantResult("call_1", "read_file", "fun main() {}", failed = false),
                "used:1320",
                AssistantMessage.Reply("Done"),
            ),
            events.events,
        )
        assertEquals(listOf("a.kt"), tool.seen)
        assertEquals(listOf("read_file"), client.tools.first().map { it.name })
        assertEquals(listOf("path"), client.tools.first().single().requiredParameters.map { it.name })

        // The first request: the instructions, the chat so far, the question.
        val first = client.prompts[0].messages
        assertEquals(listOf("You are iCode's assistant.", "Hi", "Hello.", "What does a.kt do?"), first.map { it.textContent() })
        // The second: the reply with its call, then the call's result, matched by its ID.
        val second = client.prompts[1].messages
        val sentCall = (second[4] as Message.Assistant).parts.filterIsInstance<MessagePart.Tool.Call>().single()
        val sentResult = (second[5] as Message.User).parts.filterIsInstance<MessagePart.Tool.Result>().single()
        assertEquals("call_1", sentCall.id)
        assertEquals("call_1", sentResult.id)
        assertEquals("fun main() {}", sentResult.output)
    }

    @Test fun `a reply after results that both says something and calls a tool has its call run`() = runBlocking {
        val client = ScriptedClient(listOf(callingRead, reply(MessagePart.Text("Now b.kt"), call("call_2", """{"path":"b.kt"}""")), done))
        val tool = ReadTool()
        val events = Recorder()
        KoogAssistantEngine { client }.run(turn(tool), events)
        assertEquals(listOf("a.kt", "b.kt"), tool.seen)
        assertEquals(3, client.prompts.size)
        assertEquals(AssistantMessage.Reply("Done"), events.events.last())
    }

    @Test fun `a tool's refusal reaches the model as a failed result, and the turn goes on`() = runBlocking {
        val events = Recorder()
        KoogAssistantEngine { ScriptedClient(listOf(callingRead, done)) }.run(turn(ReadTool("a.kt is outside this workspace")), events)
        val result = events.events.filterIsInstance<AssistantResult>().single()
        assertTrue(result.failed)
        assertEquals("a.kt is outside this workspace", result.output)
        assertEquals(AssistantMessage.Reply("Done"), events.events.last())
    }

    @Test fun `calls without IDs get iCode's, in order, and each result names its own`() = runBlocking {
        val events = Recorder()
        val two = reply(call(null, """{"path":"a.kt"}"""), call(null, """{"path":"b.kt"}"""))
        val tool = ReadTool()
        KoogAssistantEngine { ScriptedClient(listOf(two, done)) }.run(turn(tool), events)
        val calls = events.events.filterIsInstance<AssistantMessage.Reply>().first().calls
        assertTrue(calls.all { it.id.startsWith("icode-") })
        assertEquals(listOf("Read a.kt", "Read b.kt"), calls.map { it.summary })
        assertEquals(calls.map { "calling:${it.id}" }, events.events.filterIsInstance<String>().filter { it.startsWith("calling:") })
        assertEquals(calls.map { it.id }, events.events.filterIsInstance<AssistantResult>().map { it.callId })
        assertEquals(listOf("a.kt", "b.kt"), tool.seen)
    }

    @Test fun `an unknown tool and arguments that are not JSON are answered as failures without running anything`() = runBlocking {
        val events = Recorder()
        val tool = ReadTool()
        val wrong = reply(call("c1", tool = "delete_everything", args = "{}"), call("c2", args = "{not json"))
        KoogAssistantEngine { ScriptedClient(listOf(wrong, done)) }.run(turn(tool), events)
        val results = events.events.filterIsInstance<AssistantResult>()
        assertEquals(listOf("c1", "c2"), results.map { it.callId })
        assertTrue(results.all { it.failed })
        assertTrue(results[0].output, "delete_everything" in results[0].output)
        assertTrue(tool.seen.isEmpty())
        // The malformed call keeps its words on the page: the tool's name, its arguments unparsed.
        val reported = events.events.filterIsInstance<AssistantMessage.Reply>().first().calls[1]
        assertEquals("read_file", reported.summary)
        assertEquals(AssistantMessage.Reply("Done"), events.events.last())
    }

    @Test fun `a model that never stops calling tools is stopped at the step limit`() = runBlocking {
        val client = ScriptedClient(listOf(callingRead))
        try {
            KoogAssistantEngine { client }.run(turn(ReadTool(), maxSteps = 2), Recorder())
            fail("expected the step limit")
        } catch (stopped: AssistantTurnException) {
            assertEquals("Stopped at the step limit — send a message to continue", stopped.message)
        }
        assertEquals(2, client.prompts.size)
    }

    @Test fun `a reply with nothing in it ends the turn with a reason`() = runBlocking {
        try {
            KoogAssistantEngine { ScriptedClient(listOf(reply())) }.run(turn(ReadTool()), Recorder())
            fail("expected a failure")
        } catch (empty: AssistantTurnException) {
            assertEquals("The model sent an empty reply — send a message to continue", empty.message)
        }
    }

    @Test fun `a reply cut short at the output limit is kept, and the turn says so under it`() = runBlocking {
        val cut = Message.Assistant(listOf(MessagePart.Text("Three tiers. Tier A: start it, get an ID. Tools")), ResponseMetaInfo.Empty, finishReason = "length")
        val recorder = Recorder()
        try {
            KoogAssistantEngine { ScriptedClient(listOf(cut)) }.run(turn(ReadTool()), recorder)
            fail("expected the turn to say it was cut short")
        } catch (cutShort: AssistantTurnException) {
            assertEquals("The reply was cut short at the model's output limit — ask it to continue", cutShort.message)
        }
        assertEquals(listOf(AssistantMessage.Reply("Three tiers. Tier A: start it, get an ID. Tools")), recorder.events.filterIsInstance<AssistantMessage.Reply>())

        // A model iCode does not know: its output limit is the user's to raise.
        val custom = KoogModels.model(AssistantProviderKind.OpenAI, AssistantModelSpec("custom-model"))
        try {
            KoogAssistantEngine { ScriptedClient(listOf(cut)) }.run(turn(ReadTool()).copy(model = custom), Recorder())
            fail("expected the turn to say it was cut short")
        } catch (cutShort: AssistantTurnException) {
            assertEquals(
                "The reply was cut short at the model's output limit — ask it to continue, or set a higher Output Limit for custom-model in its provider's settings",
                cutShort.message,
            )
        }

        // Only the last reply counts: one cut short whose call ran, then a finished one, ends well.
        val cutCall = Message.Assistant(listOf(call("call_1")), ResponseMetaInfo.Empty, finishReason = "length")
        KoogAssistantEngine { ScriptedClient(listOf(cutCall, done)) }.run(turn(ReadTool()), Recorder())
    }

    @Test fun `each provider's word for the output limit is recognized, and no other reason`() {
        fun ended(reason: String?) = KoogHistory.cutShort(Message.Assistant(listOf(MessagePart.Text("x")), ResponseMetaInfo.Empty, finishReason = reason))
        assertTrue(ended("length") && ended("max_tokens") && ended("MAX_TOKENS"))
        assertTrue(listOf("stop", "tool_calls", "end_turn", "STOP", null).none { ended(it) })
    }

    @Test fun `a summary cut short at the output limit leaves the chat uncompacted`() = runBlocking {
        val client = ScriptedClient(listOf(Message.Assistant(listOf(MessagePart.Text("The user wants")), ResponseMetaInfo.Empty, finishReason = "max_tokens")))
        try {
            KoogAssistantEngine { client }.compact(connection, model, "You are iCode's assistant.", listOf(AssistantMessage.User("Fix a.kt")))
            fail("expected the summary to be refused")
        } catch (cutShort: AssistantTurnException) {
            assertEquals("The model's summary was cut short at its output limit, so the chat was not compacted", cutShort.message)
        }
    }

    @Test fun `attached files reach the model with the message, and calls of tools not offered as text`() = runBlocking {
        val client = ScriptedClient(listOf(done))
        val earlier = listOf(
            AssistantMessage.User("Fix it"),
            AssistantMessage.Reply("", listOf(AssistantCall("w1", "write_file", """{"path":"a.kt"}""", "Write a.kt", AssistantToolKind.Change))),
            AssistantMessage.Results(listOf(AssistantResult("w1", "write_file", "Wrote a.kt", failed = false))),
        )
        val input = AssistantMessage.User("Why?", listOf(AssistantAttachment("a.kt", text = "fun main() {}")))
        KoogAssistantEngine { client }.run(turn(ReadTool(), earlier, input), Recorder())
        val sent = client.prompts.single().messages
        // write_file is not among this turn's tools (Ask): Koog sends its call and result as text.
        assertTrue(sent.none { message -> message.parts.any { it is MessagePart.Tool.Call || it is MessagePart.Tool.Result } })
        assertTrue(sent[2].textContent(), "write_file" in sent[2].textContent())
        assertEquals(AssistantInstructions.message(input), sent.last().textContent())
        assertTrue("<file path=\"a.kt\">\nfun main() {}\n</file>" in sent.last().textContent())
    }

    @Test fun `compacting asks for a summary of the chat, with no tools`() = runBlocking {
        val client = ScriptedClient(listOf(reply(MessagePart.Text("  The user wants a.kt fixed.  "))))
        val chat = listOf(AssistantMessage.User("Fix a.kt"), AssistantMessage.Reply("On it."))
        val summary = KoogAssistantEngine { client }.compact(connection, model, "You are iCode's assistant.", chat)
        assertEquals("The user wants a.kt fixed.", summary)
        assertEquals(emptyList<ToolDescriptor>(), client.tools.single())
        assertEquals(
            listOf("You are iCode's assistant.", "Fix a.kt", "On it.", AssistantInstructions.COMPACT_REQUEST),
            client.prompts.single().messages.map { it.textContent() },
        )
    }

    @Test fun `every request carries the model's output limit and the chosen effort`() = runBlocking {
        val opus = KoogModels.catalogue.getValue(AssistantProviderKind.Anthropic).first()
        val anthropic = AssistantConnection(AssistantProviderKind.Anthropic, null, "sk-ant")
        val client = ScriptedClient(listOf(done), LLMProvider.Anthropic)
        val request = turn(ReadTool()).copy(connection = anthropic, model = opus, effort = opus.efforts.first())
        KoogAssistantEngine { client }.run(request, Recorder())
        val params = client.prompts.single().params as AnthropicParams
        assertTrue("${params.maxTokens}", (params.maxTokens ?: 0) > 2_048)
        assertTrue("${params.thinking}", params.thinking != null)
    }

    @Test fun `a refused key is said plainly`() = runBlocking {
        val client = ScriptedClient(emptyList()).apply {
            failWith = KoogHttpClientException("OpenAI", 401, """{"error":{"message":"Incorrect API key provided: sk-te****"}}""")
        }
        try {
            KoogAssistantEngine { client }.run(turn(ReadTool()), Recorder())
            fail("expected a failure")
        } catch (refused: AssistantTurnException) {
            assertEquals("The provider refused the API key — check it in the provider's settings", refused.message)
        }
    }

    @Test fun `cancelling stops the turn as a cancellation, not as a failure`() = runBlocking {
        val asked = CompletableDeferred<Unit>()
        val client = ScriptedClient(emptyList()).apply { hang = asked }
        val running = async { KoogAssistantEngine { client }.run(turn(ReadTool()), Recorder()) }
        withTimeout(5_000) { asked.await() }
        running.cancel()
        val outcome = runCatching { running.await() }.exceptionOrNull()
        assertTrue("$outcome", outcome is kotlinx.coroutines.CancellationException)
    }

    @Test fun `a server's client is kept between turns, whichever of its models, and one in use is never closed`() = runBlocking {
        val made = mutableListOf<ScriptedClient>()
        val engine = KoogAssistantEngine { ScriptedClient(listOf(done)).also(made::add) }
        engine.run(turn(ReadTool()), Recorder())
        engine.run(turn(ReadTool()).copy(model = KoogModels.model(AssistantProviderKind.OpenAI, AssistantModelSpec("gpt-5-mini"))), Recorder())
        assertEquals(1, made.size)
        // Other servers, one after another: the eldest go, never the one just used.
        (1..9).forEach { engine.run(turn(ReadTool()).copy(connection = connection.copy(apiKey = "sk-$it")), Recorder()) }
        assertTrue(made.first().closed)
        assertTrue(!made.last().closed)
    }

    @Test fun `an image goes as itself to a model that sees, and as a line of text to one that does not`() = runBlocking {
        val photo = java.io.File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val input = AssistantMessage.User("What is this?", listOf(AssistantAttachment("photo.jpg", AssistantAttachmentKind.Image, file = photo.path, mimeType = "image/jpeg")))
        val seeing = KoogModels.catalogue.getValue(AssistantProviderKind.OpenAI).first { it.images }
        val client = ScriptedClient(listOf(done))
        KoogAssistantEngine { client }.run(turn(ReadTool(), input = input).copy(model = seeing), Recorder())
        val sent = client.prompts.single().messages.last().parts
        val image = sent.filterIsInstance<MessagePart.Attachment>().single().source as AttachmentSource.Image
        assertEquals(listOf<Byte>(1, 2, 3), (image.content as AttachmentContent.Binary).asBytes().toList())
        assertTrue(sent.filterIsInstance<MessagePart.Text>().single().text.endsWith("Also attached: photo.jpg (image)"))

        val blind = ScriptedClient(listOf(done))
        val typed = KoogModels.model(AssistantProviderKind.OpenAICompatible, AssistantModelSpec("local"))
        KoogAssistantEngine { blind }.run(turn(ReadTool(), input = input).copy(model = typed), Recorder())
        val told = blind.prompts.single().messages.last().parts
        assertTrue(told.none { it is MessagePart.Attachment })
        assertTrue(told.any { it is MessagePart.Text && "photo.jpg, not sent: this model cannot see images" in it.text })
    }

    @Test fun `a server's models are listed, Koog's facts where it knows them, and nothing that cannot chat`() = runBlocking {
        val known = KoogModels.catalogue.getValue(AssistantProviderKind.OpenAI).first()
        val embedding = ai.koog.prompt.executor.clients.openai.OpenAIModels.Embeddings.TextEmbedding3Small
        val listed = listOf(LLModel(LLMProvider.OpenAI, "zz-new"), known.let { m -> LLModel(LLMProvider.OpenAI, m.id) }, embedding, LLModel(LLMProvider.OpenAI, "aa-new"))
        val client = ScriptedClient(emptyList(), listed = listed)
        val models = KoogAssistantEngine { client }.available(connection)
        assertEquals(listOf(known.id, "aa-new", "zz-new"), models.map { it.id })
        assertTrue(models.first().known && models.drop(1).none { it.known })
        assertTrue("a listing's client is closed after", client.closed)
    }

    @Test fun `a wrong address while listing says so, not that a model is missing`() = runBlocking {
        val wrong = object : LLMClient() {
            override fun llmProvider(): LLMProvider = LLMProvider.OpenAI
            override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant = error("")
            override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = error("")
            override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("")
            override suspend fun models(): List<LLModel> = throw KoogHttpClientException("OpenAI", 404, "")
            override fun close() {}
        }
        try {
            KoogAssistantEngine { wrong }.available(AssistantConnection(AssistantProviderKind.OpenAICompatible, "https://example.com/v2", null))
            fail("expected a failure")
        } catch (refused: AssistantTurnException) {
            assertEquals("example.com has no such API — check the address in the provider's settings", refused.message)
        }
    }
}
