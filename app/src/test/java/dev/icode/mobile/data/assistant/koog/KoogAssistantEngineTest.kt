package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.assistant.AssistantTurnRequest
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The real Koog agent runs iCode's loop here; only the provider is scripted. */
class KoogAssistantEngineTest {
    private val connection = AssistantConnection(AssistantProviderKind.OpenAI, null, "gpt-5", "sk-test")

    private class ScriptedClient(private val replies: List<List<StreamFrame>>) : LLMClient() {
        val prompts = mutableListOf<Prompt>()
        val tools = mutableListOf<List<ToolDescriptor>>()
        var closed = false
        var hang: CompletableDeferred<Unit>? = null
        var failWith: Throwable? = null

        override fun llmProvider(): LLMProvider = LLMProvider.OpenAI
        override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant = error("streaming only")
        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("no moderation")
        override fun close() {
            closed = true
        }

        override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = flow {
            prompts += prompt
            this@ScriptedClient.tools += tools
            failWith?.let { throw it }
            hang?.let {
                it.complete(Unit)
                awaitCancellation()
            }
            replies[minOf(prompts.size, replies.size) - 1].forEach { emit(it) }
        }
    }

    private class Recorder : AssistantEvents {
        val events = mutableListOf<Any>()
        override fun text(delta: String) {
            events += "text:$delta"
        }
        override fun replied(reply: AssistantMessage.Reply) {
            events += reply
        }
        override fun calling(call: AssistantCall) {
            events += "calling:${call.id}"
        }
        override fun answered(results: AssistantMessage.Results) {
            events += results
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

    private val callingRead = listOf(
        StreamFrame.TextDelta("Reading"),
        StreamFrame.TextComplete("Reading"),
        StreamFrame.ToolCallComplete("call_1", "read_file", """{"path":"a.kt"}"""),
        StreamFrame.End("tool_calls"),
    )
    private val done = listOf(StreamFrame.TextDelta("Do"), StreamFrame.TextDelta("ne"), StreamFrame.TextComplete("Done"), StreamFrame.End("stop"))

    private fun turn(tool: AssistantTool, history: List<AssistantMessage> = emptyList(), maxSteps: Int = 40) =
        AssistantTurnRequest(connection, "You are iCode's assistant.", history, "What does a.kt do?", listOf(tool), maxSteps)

    @Test fun `a reply's tool call runs, its result goes back, and the next reply ends the turn`() = runBlocking {
        val client = ScriptedClient(listOf(callingRead, done))
        val tool = ReadTool()
        val events = Recorder()
        val history = listOf(AssistantMessage.User("Hi"), AssistantMessage.Reply("Hello."))
        KoogAssistantEngine { client }.run(turn(tool, history), events)

        val call = AssistantCall("call_1", "read_file", """{"path":"a.kt"}""", "Read a.kt", AssistantToolKind.Read)
        assertEquals(
            listOf(
                "text:Reading",
                AssistantMessage.Reply("Reading", listOf(call)),
                "calling:call_1",
                AssistantMessage.Results(listOf(AssistantResult("call_1", "read_file", "fun main() {}", failed = false))),
                "text:Do", "text:ne",
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

    @Test fun `a tool's refusal reaches the model as a failed result, and the turn goes on`() = runBlocking {
        val events = Recorder()
        KoogAssistantEngine { ScriptedClient(listOf(callingRead, done)) }.run(turn(ReadTool("a.kt is outside this workspace")), events)
        val results = events.events.filterIsInstance<AssistantMessage.Results>().single().results.single()
        assertTrue(results.failed)
        assertEquals("a.kt is outside this workspace", results.output)
        assertEquals(AssistantMessage.Reply("Done"), events.events.last())
    }

    @Test fun `a reply streamed without a complete frame keeps its text, and a call without an ID gets one`() = runBlocking {
        val events = Recorder()
        val noId = listOf(StreamFrame.ToolCallComplete(null, "read_file", "{\"path\":\"b.kt\"}"), StreamFrame.End())
        KoogAssistantEngine { ScriptedClient(listOf(noId, listOf(StreamFrame.TextDelta("Only deltas"), StreamFrame.End()))) }.run(turn(ReadTool()), events)
        val replies = events.events.filterIsInstance<AssistantMessage.Reply>()
        assertTrue(replies[0].calls.single().id.startsWith("icode-"))
        assertEquals(replies[0].calls.single().id, events.events.filterIsInstance<AssistantMessage.Results>().single().results.single().callId)
        assertEquals("Only deltas", replies[1].text)
    }

    @Test fun `a model that never stops calling tools is stopped at the step limit`() = runBlocking {
        try {
            KoogAssistantEngine { ScriptedClient(listOf(callingRead)) }.run(turn(ReadTool(), maxSteps = 2), Recorder())
            fail("expected the step limit")
        } catch (stopped: AssistantTurnException) {
            assertEquals("Stopped after 2 steps — send a message to continue", stopped.message)
        }
    }

    @Test fun `a refused key is said plainly`() = runBlocking {
        val client = ScriptedClient(emptyList()).apply {
            failWith = KoogHttpClientException("OpenAI", 401, """{"error":{"message":"Incorrect API key provided: sk-te****"}}""")
        }
        try {
            KoogAssistantEngine { client }.run(turn(ReadTool()), Recorder())
            fail("expected a failure")
        } catch (refused: AssistantTurnException) {
            assertEquals("The provider refused the API key — check it in Assistant settings", refused.message)
        }
    }

    @Test fun `cancelling stops the turn as a cancellation, not as a failure`() = runBlocking {
        val streaming = CompletableDeferred<Unit>()
        val client = ScriptedClient(emptyList()).apply { hang = streaming }
        val running = async { KoogAssistantEngine { client }.run(turn(ReadTool()), Recorder()) }
        withTimeout(5_000) { streaming.await() }
        running.cancel()
        val outcome = runCatching { running.await() }.exceptionOrNull()
        assertTrue("$outcome", outcome is kotlinx.coroutines.CancellationException)
    }

    @Test fun `the client is kept between turns and replaced when the settings change`() = runBlocking {
        val made = mutableListOf<ScriptedClient>()
        val engine = KoogAssistantEngine { ScriptedClient(listOf(done)).also(made::add) }
        engine.run(turn(ReadTool()), Recorder())
        engine.run(turn(ReadTool()), Recorder())
        assertEquals(1, made.size)
        engine.run(turn(ReadTool()).copy(connection = connection.copy(model = "gpt-5-mini")), Recorder())
        assertEquals(2, made.size)
        assertTrue(made[0].closed)
    }

    @Test fun `typed models chat and call tools, and catalogued ones keep Koog's capabilities`() {
        val typed = KoogModels.resolve(AssistantProviderKind.OpenAICompatible, "deepseek-chat")
        assertEquals(LLMProvider.OpenAI, typed.provider)
        assertTrue(typed.supports(ai.koog.prompt.llm.LLMCapability.Tools))
        assertTrue(typed.supports(ai.koog.prompt.llm.LLMCapability.OpenAIEndpoint.Completions))
        val suggested = KoogModels.suggestions()
        assertTrue(suggested.getValue(AssistantProviderKind.Anthropic).isNotEmpty())
        assertTrue(suggested.getValue(AssistantProviderKind.OpenAICompatible).isEmpty())
        val catalogued = KoogModels.resolve(AssistantProviderKind.Anthropic, suggested.getValue(AssistantProviderKind.Anthropic).first())
        assertTrue(catalogued in ai.koog.prompt.executor.clients.anthropic.AnthropicModels.models)
    }
}
