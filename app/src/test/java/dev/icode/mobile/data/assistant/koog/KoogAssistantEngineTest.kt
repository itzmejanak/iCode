package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.requireEndFrame
import ai.koog.prompt.streaming.toStreamFrames
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantInstructions
import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantPicture
import dev.icode.mobile.data.assistant.AssistantStepLimitException
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
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantToolKind
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
        /**
         * Each request's frames, as a provider's client streams them; when null, the scripted reply,
         * ending as a real server's does (with a finish reason).
         */
        var frames: ((request: Int) -> List<StreamFrame>)? = null

        /** Holds a stream's end until the page was shown some of it: what is written is shown before it is recorded. */
        var shownFirst: CompletableDeferred<Unit>? = null

        /** Breaks a stream after its frames, as a connection that drops does. */
        var breakWith: Throwable? = null

        /** Refuses every stream before its first frame, as a server that does not take one does. */
        var refuseStreams: Throwable? = null

        /** The requests that streamed. */
        var streamed = 0

        override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> {
            prompts += prompt
            this.tools += tools
            streamed += 1
            val request = prompts.size - 1
            return flow {
                refuseStreams?.let { throw it }
                failWith?.let { throw it }
                hang?.let {
                    it.complete(Unit)
                    awaitCancellation()
                }
                val script = frames?.invoke(request)
                    ?: replies[minOf(request + 1, replies.size) - 1].let { it.copy(finishReason = it.finishReason ?: "stop") }.toStreamFrames()
                script.forEach { frame ->
                    if (frame is StreamFrame.End) shownFirst?.let { withTimeout(5_000) { it.await() } }
                    emit(frame)
                    yield()
                }
                breakWith?.let { throw it }
            }.requireEndFrame()
        }
        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("no moderation")
        override suspend fun models(): List<LLModel> = listed
        override fun close() {
            closed = true
        }
    }

    private class Recorder : AssistantEvents {
        val events = mutableListOf<Any>()
        val wrote = CompletableDeferred<Unit>()
        override fun writing(reasoning: String, text: String) {
            events += "writing:$reasoning|$text"
            wrote.complete(Unit)
        }
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
            events.events.filterNot { it is String && it.startsWith("writing:") },
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

    @Test fun `a call whose arguments were not an object is refused without running or asking, and its reply is kept`() = runBlocking {
        // As a long script cut at the output limit arrives, carried by iCode's reading client.
        val cut = "{\"path\":\"a.kt\",\"note\":\"line one\\nline"
        val cutReply = Message.Assistant(listOf(MessagePart.Text("Next:"), call("c1", args = KoogArguments.readable(cut))), ResponseMetaInfo.Empty, finishReason = "length")
        val client = ScriptedClient(listOf(cutReply, done))
        val tool = ReadTool()
        val events = Recorder()
        KoogAssistantEngine { client }.run(turn(tool), events)

        assertTrue(tool.seen.isEmpty())
        val reply = events.events.filterIsInstance<AssistantMessage.Reply>().first()
        // The page keeps what the model wrote.
        assertEquals(cut, reply.calls.single().arguments)
        assertEquals("read_file", reply.calls.single().summary)
        val result = events.events.filterIsInstance<AssistantResult>().single()
        assertTrue(result.failed)
        assertEquals(KoogArguments.refusal(cut), result.output)
        // The model is sent its call as an object, and why it did not run; the turn goes on.
        val second = client.prompts[1].messages
        val sentCall = (second[2] as Message.Assistant).parts.filterIsInstance<MessagePart.Tool.Call>().single()
        assertEquals(cut, KoogArguments.unreadable(sentCall.args))
        assertEquals(KoogArguments.refusal(cut), (second[3] as Message.User).parts.filterIsInstance<MessagePart.Tool.Result>().single().output)
        assertEquals(AssistantMessage.Reply("Done"), events.events.last())

        // A later turn sends that call as an object too: no provider is sent broken JSON.
        val later = KoogHistory.prompt("You are iCode's assistant.", AssistantRules.closed(listOf(AssistantMessage.User("Go"), reply)), ai.koog.prompt.params.LLMParams(), KoogModels.llModel(AssistantProviderKind.OpenAI, AssistantModelSpec("gpt-5")))
        val replayed = later.messages.flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Call>().single()
        assertEquals(cut, KoogArguments.unreadable(replayed.args))
    }

    @Test fun `a model that never stops calling tools is stopped at the step limit`() = runBlocking {
        val client = ScriptedClient(listOf(callingRead))
        try {
            KoogAssistantEngine { client }.run(turn(ReadTool(), maxSteps = 2), Recorder())
            fail("expected the step limit")
        } catch (stopped: AssistantStepLimitException) {
            assertEquals("Stopped at the step limit — send a message to continue", stopped.message)
        }
        assertEquals(2, client.prompts.size)
    }

    @Test fun `a turn goes on from the chat as it is, with no new message`() = runBlocking {
        val client = ScriptedClient(listOf(done))
        val history = listOf(
            AssistantMessage.User("Fix it"),
            AssistantMessage.Reply("Reading", listOf(AssistantCall("call_1", "read_file", """{"path":"a.kt"}""", "Read a.kt", AssistantToolKind.Read))),
            AssistantMessage.Results(listOf(AssistantResult("call_1", "read_file", "fun main() {}", failed = false))),
        )
        KoogAssistantEngine { client }.run(turn(ReadTool(), history).copy(input = null), Recorder())
        val sent = client.prompts.single().messages
        assertEquals(4, sent.size)
        assertTrue((sent.last() as Message.User).parts.single() is MessagePart.Tool.Result)
    }

    @Test fun `a step's screen goes with its results where the model sees pictures, and only the latest stays`() = runBlocking {
        val seeing = KoogModels.catalogue.getValue(AssistantProviderKind.OpenAI).first { it.images }
        val screens = ArrayDeque(listOf(byteArrayOf(1), byteArrayOf(2)))
        val client = ScriptedClient(listOf(callingRead, reply(MessagePart.Text("Again"), call("call_2")), done))
        KoogAssistantEngine { client }.run(turn(ReadTool()).copy(model = seeing, picture = { screens.removeFirstOrNull()?.let(::AssistantPicture) }), Recorder())
        fun pictures(prompt: Prompt) = prompt.messages.flatMap { it.parts }.flatMap { if (it is MessagePart.Tool.Result) it.parts else listOf(it) }
            .filterIsInstance<MessagePart.Attachment>().map { (it.source.content as AttachmentContent.Binary).asBytes().toList() }
        // Beside the results, in the same message: Chat Completions sends it after the tool messages.
        val second = client.prompts[1].messages.last() as Message.User
        assertTrue(second.parts.first() is MessagePart.Tool.Result)
        assertEquals(listOf(listOf<Byte>(1)), pictures(client.prompts[1]))
        assertEquals(listOf(listOf<Byte>(2)), pictures(client.prompts[2]))
    }

    @Test fun `a screen is dropped for a model that sees none, and goes in the result for OpenAI's Responses`() {
        val blind = KoogModels.llModel(AssistantProviderKind.OpenAICompatible, AssistantModelSpec("local"))
        assertEquals(KoogTurn.PicturePlace.None, KoogTurn.picturePlace(AssistantProviderKind.OpenAICompatible, blind, sees = false))
        val responses = LLModel(LLMProvider.OpenAI, "gpt-x", listOf(LLMCapability.Vision.Image, LLMCapability.Tools, LLMCapability.OpenAIEndpoint.Responses), 400_000)
        assertEquals(KoogTurn.PicturePlace.InResult, KoogTurn.picturePlace(AssistantProviderKind.OpenAI, responses, sees = true))
        val both = responses.copy(capabilities = responses.capabilities.orEmpty() + LLMCapability.OpenAIEndpoint.Completions)
        assertEquals(KoogTurn.PicturePlace.Beside, KoogTurn.picturePlace(AssistantProviderKind.OpenAI, both, sees = true))
        assertEquals(KoogTurn.PicturePlace.Beside, KoogTurn.picturePlace(AssistantProviderKind.Anthropic, both, sees = true))
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

    @Test fun `a reply's reasoning is kept apart from its answer, and never sent back to the model`() = runBlocking {
        val recorder = Recorder()
        val thinking = reply(MessagePart.Reasoning("a.kt defines main."), MessagePart.Text("It starts the app."))
        KoogAssistantEngine { ScriptedClient(listOf(thinking)) }.run(turn(ReadTool()), recorder)
        assertEquals(AssistantMessage.Reply("It starts the app.", reasoning = "a.kt defines main."), recorder.events.filterIsInstance<AssistantMessage.Reply>().single())

        // Written into the text by the model itself, as some do.
        val inline = Recorder()
        KoogAssistantEngine { ScriptedClient(listOf(reply(MessagePart.Text("<think>\nCheck a.kt first.\n</think>\n\nIt starts the app.")))) }.run(turn(ReadTool()), inline)
        assertEquals(AssistantMessage.Reply("It starts the app.", reasoning = "Check a.kt first."), inline.events.filterIsInstance<AssistantMessage.Reply>().single())

        // A later turn sends the answer alone.
        val client = ScriptedClient(listOf(done))
        val earlier = listOf(AssistantMessage.User("What does a.kt do?"), AssistantMessage.Reply("It starts the app.", reasoning = "a.kt defines main."))
        KoogAssistantEngine { client }.run(turn(ReadTool(), earlier), Recorder())
        val sent = client.prompts.single().messages
        assertTrue(sent.none { message -> message.parts.any { it is MessagePart.Reasoning } || "a.kt defines main." in message.textContent() })
        assertEquals("It starts the app.", sent[2].textContent())
    }

    @Test fun `a call names the file it touches and the command it runs, as its tool reads them`() {
        val runner = object : AssistantTool {
            override val name = "run_command"
            override val description = "Runs a command"
            override val parameters = listOf(AssistantParameter("command", "The command"))
            override val kind = AssistantToolKind.Command
            override fun summary(arguments: JSONObject) = "Run `${arguments.optString("command").lineSequence().first()}`"
            override fun command(arguments: JSONObject) = arguments.optString("command")
            override suspend fun run(arguments: JSONObject) = ""
        }
        val reader = object : AssistantTool by ReadTool() {
            override fun file(arguments: JSONObject) = arguments.optString("path")
        }
        val tools = mapOf("run_command" to runner, "read_file" to reader)
        val reply = KoogHistory.reply(
            reply(call("c1", """{"command":"npm test\nnpm run lint"}""", tool = "run_command"), call("c2", """{"path":"src/a.kt"}""")),
            tools,
        ) { "new" }
        assertEquals("npm test\nnpm run lint", reply.calls[0].command)
        assertEquals("Run `npm test`", reply.calls[0].summary)
        assertEquals(null, reply.calls[0].file)
        assertEquals("src/a.kt", reply.calls[1].file)
        assertEquals(null, reply.calls[1].command)
        // Arguments that could not be read name nothing.
        val unreadable = KoogHistory.reply(reply(call("c3", KoogArguments.readable("{\"command\":\"npm"), tool = "run_command")), tools) { "new" }
        assertEquals(null, unreadable.calls.single().command)
    }

    @Test fun `a think block is reasoning, an unclosed one all of it, and text without one is left alone`() {
        assertEquals("plan" to "answer", KoogHistory.thinking("<think>plan</think>answer"))
        assertEquals("plan, cut" to "", KoogHistory.thinking("<think>plan, cut"))
        assertEquals("" to "No <think> here", KoogHistory.thinking("No <think> here"))
    }

    @Test fun `each provider's word for the output limit is recognized, and no other reason`() {
        fun ended(reason: String?) = KoogHistory.cutShort(Message.Assistant(listOf(MessagePart.Text("x")), ResponseMetaInfo.Empty, finishReason = reason))
        assertTrue(ended("length") && ended("max_tokens") && ended("MAX_TOKENS"))
        assertTrue(listOf("stop", "tool_calls", "end_turn", "STOP", null).none { ended(it) })
        fun filtered(reason: String?) = KoogHistory.filtered(Message.Assistant(listOf(MessagePart.Text("x")), ResponseMetaInfo.Empty, finishReason = reason))
        assertTrue(listOf("content_filter", "refusal", "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY").all { filtered(it) })
        assertTrue(listOf("stop", "length", "MAX_TOKENS", "error", null).none { filtered(it) })
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

    @Test fun `a request is answered in one whole reply with no tools, or says why there is none`() = runBlocking {
        val client = ScriptedClient(listOf(reply(MessagePart.Text("  feat(auth): add login validation  "))))
        assertEquals("feat(auth): add login validation", KoogAssistantEngine { client }.answer(connection, model, "You write Git commit messages.", "The staged diff: +a"))
        assertEquals(emptyList<ToolDescriptor>(), client.tools.single())
        assertEquals(listOf("You write Git commit messages.", "The staged diff: +a"), client.prompts.single().messages.map { it.textContent() })
        val cut = Message.Assistant(listOf(MessagePart.Text("feat: add")), ResponseMetaInfo.Empty, finishReason = "length")
        try {
            KoogAssistantEngine { ScriptedClient(listOf(cut)) }.answer(connection, model, "You write Git commit messages.", "The staged diff: +a")
            fail("expected the answer to be refused")
        } catch (cutShort: AssistantTurnException) {
            assertEquals("The model's answer was cut short at its output limit — try again", cutShort.message)
        }
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

    private val openRouter = AssistantConnection(AssistantProviderKind.OpenRouter, null, "sk-or-test")

    /** A turn on OpenRouter, whose replies stream. */
    private fun streamedTurn(tool: AssistantTool) =
        turn(tool).copy(connection = openRouter, model = KoogModels.model(AssistantProviderKind.OpenRouter, AssistantModelSpec("minimax/minimax-m3")))

    @Test fun `an OpenRouter reply streams, is shown as it is written, then recorded whole without its reasoning in the prompt`() = runBlocking {
        val usage = ResponseMetaInfo.Empty.copy(inputTokensCount = 1_200, outputTokensCount = 40)
        val first = listOf(
            StreamFrame.ReasoningDelta(text = "Need "),
            StreamFrame.ReasoningDelta(text = "a.kt."),
            StreamFrame.ReasoningComplete(id = null, content = listOf("Need a.kt.")),
            StreamFrame.TextDelta("Read"),
            StreamFrame.TextDelta("ing"),
            StreamFrame.TextComplete("Reading"),
            StreamFrame.ToolCallDelta("call_1", "read_file", """{"path":"""),
            StreamFrame.ToolCallDelta(null, null, "\"a.kt\"}"),
            StreamFrame.ToolCallComplete("call_1", "read_file", """{"path":"a.kt"}"""),
            StreamFrame.End("tool_calls", usage),
        )
        val events = Recorder()
        val client = ScriptedClient(emptyList(), provider = LLMProvider.OpenRouter).apply {
            frames = { request -> if (request == 0) first else done.copy(finishReason = "stop").toStreamFrames() }
            shownFirst = events.wrote
        }
        val tool = ReadTool()
        KoogAssistantEngine { client }.run(streamedTurn(tool), events)

        val call = AssistantCall("call_1", "read_file", """{"path":"a.kt"}""", "Read a.kt", AssistantToolKind.Read)
        // Shown as written: each a part of what came, before the reply is recorded.
        val written = events.events.takeWhile { it !is AssistantMessage.Reply }.filterIsInstance<String>().filter { it.startsWith("writing:") }
        assertTrue(events.events.toString(), written.isNotEmpty())
        written.forEach { shown ->
            val (reasoning, text) = shown.removePrefix("writing:").split("|")
            assertTrue(shown, "Need a.kt.".startsWith(reasoning) && "Reading".startsWith(text))
        }
        assertEquals(
            listOf(
                "used:1240",
                AssistantMessage.Reply("Reading", listOf(call), reasoning = "Need a.kt."),
                "calling:call_1",
                AssistantResult("call_1", "read_file", "fun main() {}", failed = false),
                "used:1320",
                AssistantMessage.Reply("Done"),
            ),
            events.events.filterNot { it is String && it.startsWith("writing:") },
        )
        assertEquals(listOf("a.kt"), tool.seen)
        // The next request goes on from the reply and its result, the reasoning left out.
        val second = client.prompts[1].messages
        val echoed = second[2] as Message.Assistant
        assertTrue(echoed.parts.none { it is MessagePart.Reasoning })
        assertEquals("Reading", echoed.textContent())
        assertEquals("call_1", echoed.parts.filterIsInstance<MessagePart.Tool.Call>().single().id)
        assertEquals("call_1", (second[3] as Message.User).parts.filterIsInstance<MessagePart.Tool.Result>().single().id)
    }

    @Test fun `a stream that breaks before its end says the connection ended, not that the provider answered`() = runBlocking {
        val partial = listOf(StreamFrame.TextDelta("Three tiers"))
        for (failure in listOf(null, KoogHttpClientException("OpenRouter", message = "Exception during streaming: Connection reset", cause = java.net.SocketException("Connection reset")))) {
            val client = ScriptedClient(emptyList(), provider = LLMProvider.OpenRouter).apply {
                frames = { partial }
                breakWith = failure
            }
            try {
                KoogAssistantEngine { client }.run(streamedTurn(ReadTool()), Recorder())
                fail("expected a failure")
            } catch (broken: AssistantTurnException) {
                assertEquals("The connection ended before the reply was complete — try again", broken.message)
            }
        }
    }

    @Test fun `a stream that closes without saying how it ended, or says its provider failed, records nothing`() = runBlocking {
        val cases = mapOf(
            null to "The connection ended before the reply was complete — try again",
            "error" to "The model's provider failed while writing the reply — try again",
        )
        for ((reason, said) in cases) {
            val recorder = Recorder()
            val client = ScriptedClient(emptyList(), provider = LLMProvider.OpenRouter).apply {
                frames = { listOf(StreamFrame.TextDelta("Three tiers"), StreamFrame.TextComplete("Three tiers"), StreamFrame.End(reason)) }
            }
            try {
                KoogAssistantEngine { client }.run(streamedTurn(ReadTool()), recorder)
                fail("expected a failure")
            } catch (stopped: AssistantTurnException) {
                assertEquals(said, stopped.message)
            }
            assertTrue(recorder.events.none { it is AssistantMessage.Reply })
        }
    }

    @Test fun `a reply a content filter stopped is kept, and the turn says so under it, whatever the provider calls it`() = runBlocking {
        for (reason in listOf("content_filter", "refusal", "SAFETY", "PROHIBITED_CONTENT")) {
            val stopped = Message.Assistant(listOf(MessagePart.Text("Here is how")), ResponseMetaInfo.Empty, finishReason = reason)
            val recorder = Recorder()
            try {
                KoogAssistantEngine { ScriptedClient(listOf(stopped)) }.run(turn(ReadTool()), recorder)
                fail("expected the turn to say the filter stopped it")
            } catch (filtered: AssistantTurnException) {
                assertEquals("The provider's content filter stopped the reply", filtered.message)
            }
            assertEquals(listOf(AssistantMessage.Reply("Here is how")), recorder.events.filterIsInstance<AssistantMessage.Reply>())
        }
        // With nothing written, the filter is still the reason, not an empty reply.
        val nothing = Message.Assistant(emptyList(), ResponseMetaInfo.Empty, finishReason = "content_filter")
        try {
            KoogAssistantEngine { ScriptedClient(listOf(nothing)) }.run(turn(ReadTool()), Recorder())
            fail("expected the turn to say the filter stopped it")
        } catch (filtered: AssistantTurnException) {
            assertEquals("The provider's content filter stopped the reply", filtered.message)
        }
        // A summary stopped so leaves the chat as it is.
        try {
            KoogAssistantEngine { ScriptedClient(listOf(nothing)) }.compact(connection, model, "You are iCode's assistant.", listOf(AssistantMessage.User("Fix a.kt")))
            fail("expected the summary to be refused")
        } catch (filtered: AssistantTurnException) {
            assertEquals("The provider's content filter stopped the summary, so the chat was not compacted", filtered.message)
        }
    }

    @Test fun `a reply with only reasoning, cut short at the limit, says the limit, streamed or not`() = runBlocking {
        val thinking = Message.Assistant(listOf(MessagePart.Reasoning(listOf("Tier A: start it, get an ID"))), ResponseMetaInfo.Empty, finishReason = "length")
        val whole = Recorder()
        val streamed = Recorder()
        val runs = listOf(
            Triple(ScriptedClient(listOf(thinking)), turn(ReadTool()), whole),
            Triple(ScriptedClient(emptyList(), provider = LLMProvider.OpenRouter).apply { frames = { thinking.toStreamFrames() } }, streamedTurn(ReadTool()), streamed),
        )
        for ((client, request, recorder) in runs) {
            try {
                KoogAssistantEngine { client }.run(request, recorder)
                fail("expected the turn to say it was cut short")
            } catch (cutShort: AssistantTurnException) {
                assertTrue(cutShort.message, cutShort.message!!.startsWith("The reply was cut short at the model's output limit"))
            }
            assertEquals(AssistantMessage.Reply("", reasoning = "Tier A: start it, get an ID"), recorder.events.filterIsInstance<AssistantMessage.Reply>().single())
        }
    }

    @Test fun `replies stream from OpenAI, OpenAI-compatible servers and OpenRouter, and come whole from the rest`() = runBlocking {
        val providers = mapOf(
            AssistantProviderKind.OpenAI to LLMProvider.OpenAI,
            AssistantProviderKind.OpenAICompatible to LLMProvider.OpenAI,
            AssistantProviderKind.OpenRouter to LLMProvider.OpenRouter,
            AssistantProviderKind.Anthropic to LLMProvider.Anthropic,
            AssistantProviderKind.Google to LLMProvider.Google,
            AssistantProviderKind.Ollama to LLMProvider.Ollama,
        )
        for ((kind, provider) in providers) {
            val client = ScriptedClient(listOf(done), provider = provider)
            val request = turn(ReadTool()).copy(
                connection = AssistantConnection(kind, "https://example.com/v1".takeIf { kind.asksBaseUrl }, "sk-test"),
                model = KoogModels.model(kind, AssistantModelSpec("some-model")),
            )
            val events = Recorder()
            KoogAssistantEngine { client }.run(request, events)
            val streams = kind in setOf(AssistantProviderKind.OpenAI, AssistantProviderKind.OpenAICompatible, AssistantProviderKind.OpenRouter)
            assertEquals("$kind", if (streams) 1 else 0, client.streamed)
            assertEquals("$kind", AssistantMessage.Reply("Done"), events.events.last())
        }
    }

    private val compatible = AssistantConnection(AssistantProviderKind.OpenAICompatible, "https://example.com/v1", "sk-test")
    private val compatibleModel = KoogModels.model(AssistantProviderKind.OpenAICompatible, AssistantModelSpec("free/deepseek-v4-pro"))

    @Test fun `a server that refuses to stream is asked the same whole, and its model is not streamed again`() = runBlocking {
        val client = ScriptedClient(listOf(done)).apply {
            refuseStreams = KoogHttpClientException("OpenAI", 400, """{"error":{"message":"Unrecognized request argument supplied: stream_options"}}""")
        }
        val engine = KoogAssistantEngine { client }
        val first = Recorder()
        engine.run(turn(ReadTool()).copy(connection = compatible, model = compatibleModel), first)
        assertEquals(AssistantMessage.Reply("Done"), first.events.last())
        assertEquals(1, client.streamed)
        assertEquals(2, client.prompts.size)
        val second = Recorder()
        engine.run(turn(ReadTool()).copy(connection = compatible, model = compatibleModel), second)
        assertEquals(AssistantMessage.Reply("Done"), second.events.last())
        assertEquals("not streamed again", 1, client.streamed)
    }

    @Test fun `a refusal the whole request repeats is the request's failure, and the next turn streams again`() = runBlocking {
        val tooLong = KoogHttpClientException("OpenAI", 400, """{"error":{"message":"This model's maximum context length is 8192 tokens"}}""")
        val client = ScriptedClient(listOf(done)).apply {
            refuseStreams = tooLong
            failWith = tooLong
        }
        val engine = KoogAssistantEngine { client }
        repeat(2) {
            try {
                engine.run(turn(ReadTool()).copy(connection = compatible, model = compatibleModel), Recorder())
                fail("expected a failure")
            } catch (refused: AssistantTurnException) {
                assertEquals("This chat is longer than the model accepts — compact it or start a new chat", refused.message)
            }
        }
        assertEquals(2, client.streamed)
    }

    @Test fun `a compatible server's stream need not say how it ended, OpenAI's must`() = runBlocking {
        val unsaid = listOf(StreamFrame.TextDelta("Done"), StreamFrame.TextComplete("Done"), StreamFrame.End(null))
        val events = Recorder()
        KoogAssistantEngine { ScriptedClient(emptyList()).apply { frames = { unsaid } } }.run(turn(ReadTool()).copy(connection = compatible, model = compatibleModel), events)
        assertEquals(AssistantMessage.Reply("Done"), events.events.last())
        try {
            KoogAssistantEngine { ScriptedClient(emptyList()).apply { frames = { unsaid } } }.run(turn(ReadTool()), Recorder())
            fail("expected a failure")
        } catch (broken: AssistantTurnException) {
            assertEquals("The connection ended before the reply was complete — try again", broken.message)
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
            assertEquals("The provider refused the API key — check it in the provider's settings", refused.message)
        }
    }

    @Test fun `a failure with no answer from the server is said by its cause, however Koog wrapped it`() = runBlocking {
        val causes = mapOf(
            KoogHttpClientException("OpenAI", message = "timeout", cause = HttpRequestTimeoutException("https://api.openai.com/v1/chat/completions", 900_000L, null))
                to "api.openai.com did not answer in time",
            KoogHttpClientException("OpenAI", message = "unreachable", cause = java.net.UnknownHostException("api.openai.com"))
                to "Unable to reach api.openai.com — check the address and the connection",
            LLMClientException("OpenAI", "unexpected end of stream", java.io.IOException("unexpected end of stream"))
                to "The connection ended before the reply was complete — try again",
            // A reply that is not what the API sends: never the parser's words.
            LLMClientException("OpenAI", "bad body", kotlinx.serialization.SerializationException("Unexpected EOF at path: $"))
                to "The provider's reply could not be read — try again",
            // Not from the server: said as it is.
            java.io.FileNotFoundException("/data/chats/photo.jpg (Permission denied)") to "The assistant stopped: /data/chats/photo.jpg (Permission denied)",
        )
        for ((cause, said) in causes) {
            val client = ScriptedClient(emptyList()).apply { failWith = cause }
            try {
                KoogAssistantEngine { client }.run(turn(ReadTool()), Recorder())
                fail("expected a failure")
            } catch (stopped: AssistantTurnException) {
                assertEquals(said, stopped.message)
            }
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
