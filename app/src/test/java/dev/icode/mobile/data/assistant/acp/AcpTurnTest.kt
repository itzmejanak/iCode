package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.AuthEnvVar
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.Cost
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallLocation
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.domain.AgentKey
import dev.icode.mobile.domain.AgentSignIn
import dev.icode.mobile.domain.AgentSignInWay
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantOption
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.InstalledAgent
import dev.icode.mobile.domain.LineChanges
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A message answered by a coding agent, over the protocol, with the SDK's own agent side as the agent. */
@OptIn(UnstableApi::class)
class AcpTurnTest {
    @get:Rule val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fake = FakeAgents()
    private val keys = mutableMapOf<String, Map<String, String>>()
    private var idleMillis = AcpAgents.IDLE_MILLIS
    private val agents by lazy {
        AcpAgents(
            scope, fake,
            login = { mapOf("HOME" to "/root") },
            installed = { mapOf(AGENT to InstalledAgent("1.0.0", listOf("/root/.local/share/icode/agents/fake/1.0.0/fake-acp", "--acp"), mapOf("FAKE_MODE" to "acp"), 0)) },
            keys = { keys[it].orEmpty() },
            version = "1.2.3",
            idleMillis = idleMillis,
        )
    }
    private val turn by lazy { AcpTurn(agents) }
    private val place by lazy { TestRuntime(temp.root).workspace }
    private val events = Recorded()

    @After fun stop() = scope.cancel()

    private class Recorded : AssistantEvents {
        val replies = CopyOnWriteArrayList<AssistantMessage.Reply>()
        val calls = CopyOnWriteArrayList<AssistantCall>()
        val results = CopyOnWriteArrayList<AssistantResult>()
        val written = CopyOnWriteArrayList<String>()
        val used = CopyOnWriteArrayList<Long>()

        override fun writing(reasoning: String, text: String) {
            written += text
        }

        override fun replied(reply: AssistantMessage.Reply) {
            replies += reply
        }

        override fun calling(call: AssistantCall) {
            calls += call
        }

        override fun answered(result: AssistantResult) {
            results += result
        }

        override fun used(tokens: Long) {
            used += tokens
        }
    }

    private suspend fun send(
        text: String,
        history: List<AssistantMessage> = emptyList(),
        chat: Pair<String?, String> = CHAT,
        approve: suspend (AssistantCall, List<AssistantOption>) -> String? = { _, _ -> null },
    ) = withTimeout(20_000) { turn.answer(AGENT, chat, place, history, AssistantMessage.User(text), events, approve) }

    @Test fun `the agent starts in the runtime with its command, the login's variables, its own and its keys`() = runBlocking {
        keys[AGENT] = mapOf("FAKE_KEY" to "k-123")
        send("Hello")
        val start = fake.starts.single()
        assertEquals(listOf("/root/.local/share/icode/agents/fake/1.0.0/fake-acp", "--acp"), start.argv)
        assertEquals(mapOf("HOME" to "/root", "FAKE_MODE" to "acp", "FAKE_KEY" to "k-123"), start.variables)
        assertEquals(listOf(place.guestPath), fake.sessions)
        assertEquals(listOf(AssistantMessage.Reply("Done.")), events.replies)
    }

    @Test fun `its words are a reply, its edit a step with the lines it changed, and the reply says what it cost`() = runBlocking {
        val file = "${place.guestPath}/src/App.kt"
        fake.answer = {
            emit(Event.SessionUpdateEvent(FakeAgents.chunk("Fixing it")))
            emit(Event.SessionUpdateEvent(SessionUpdate.ToolCall(ToolCallId("c1"), "Edit App.kt", ToolKind.EDIT, ToolCallStatus.PENDING, locations = listOf(ToolCallLocation(file)))))
            emit(Event.SessionUpdateEvent(SessionUpdate.ToolCallUpdate(ToolCallId("c1"), status = ToolCallStatus.COMPLETED, content = listOf(ToolCallContent.Diff(file, "a\nb\n", "a\n")))))
            emit(Event.SessionUpdateEvent(FakeAgents.chunk("Fixed.")))
            emit(Event.SessionUpdateEvent(SessionUpdate.UsageUpdate(1_200, 200_000, Cost(0.14, "USD"))))
            emit(FakeAgents.ended())
        }
        send("Fix App.kt")
        val call = events.calls.single()
        assertEquals("src/App.kt", call.file)
        assertEquals(AssistantToolKind.Change, call.kind)
        assertEquals("Edit App.kt", call.summary)
        assertEquals(listOf(AssistantMessage.Reply("Fixing it", listOf(call)), AssistantMessage.Reply("Fixed.", cost = "0.14 USD")), events.replies)
        assertEquals(LineChanges(1, 0), events.results.single().changes)
        assertFalse(events.results.single().failed)
        assertEquals(listOf(1_200L), events.used)

        fake.answer = {
            emit(Event.SessionUpdateEvent(FakeAgents.chunk("Again.")))
            emit(Event.SessionUpdateEvent(SessionUpdate.UsageUpdate(1_400, 200_000, Cost(0.20, "USD"))))
            emit(FakeAgents.ended())
        }
        send("Once more", listOf(AssistantMessage.User("Fix App.kt"), AssistantMessage.Reply("Fixed.", by = AssistantAgent(AGENT))))
        assertEquals("Each reply shows what it added to the session", "0.06 USD", events.replies.last().cost)
    }

    @Test fun `a command's step keeps what it printed, and a failed one says so`() = runBlocking {
        fake.answer = {
            emit(Event.SessionUpdateEvent(SessionUpdate.ToolCall(ToolCallId("c1"), "Run tests", ToolKind.EXECUTE, ToolCallStatus.IN_PROGRESS, rawInput = kotlinx.serialization.json.buildJsonObject { put("command", kotlinx.serialization.json.JsonPrimitive("gradle test")) })))
            emit(Event.SessionUpdateEvent(SessionUpdate.ToolCallUpdate(ToolCallId("c1"), status = ToolCallStatus.FAILED, content = listOf(ToolCallContent.Content(ContentBlock.Text("2 tests failed"))))))
            emit(FakeAgents.ended())
        }
        send("Run the tests")
        assertEquals("gradle test", events.calls.single().command)
        val result = events.results.single()
        assertTrue(result.failed)
        assertEquals("2 tests failed", result.printed)
    }

    @Test fun `a new session is sent the chat so far, the next message in it only itself, and a forgotten chat starts again`() = runBlocking {
        val history = listOf(AssistantMessage.User("What is this?"), AssistantMessage.Reply("A Kotlin app.", by = AssistantChoice("p1", "gpt-5")))
        send("Add a test", history)
        assertTrue(fake.prompts[0].startsWith("The conversation so far in this chat, for context:"))
        assertTrue(fake.prompts[0].contains("A Kotlin app."))
        assertTrue(fake.prompts[0].endsWith("The message to answer now:\n\nAdd a test"))
        val answered = history + AssistantMessage.User("Add a test") + AssistantMessage.Reply("Done.", by = AssistantAgent(AGENT))
        send("And another", answered)
        assertEquals("And another", fake.prompts[1])
        assertEquals(1, fake.sessions.size)

        turn.forget(CHAT)
        send("Once more", answered)
        assertEquals(2, fake.sessions.size)
        assertTrue(fake.prompts[2].contains("A Kotlin app."))
        assertEquals("One process serves every chat", 1, fake.starts.size)
    }

    @Test fun `after another answerer wrote in the chat, the agent starts afresh with all of it`() = runBlocking {
        val first = listOf(AssistantMessage.User("Hi"), AssistantMessage.Reply("Hello", by = AssistantAgent(AGENT)))
        send("Plan it", first)
        val handedBack = first + AssistantMessage.User("Plan it") + AssistantMessage.Reply("Done.", by = AssistantAgent(AGENT)) +
            AssistantMessage.User("Your view?") + AssistantMessage.Reply("Use a cache.", by = AssistantChoice("p1", "gpt-5"))
        send("Build it", handedBack)
        assertEquals(2, fake.sessions.size)
        assertTrue("The model's reply reaches the agent", fake.prompts[1].contains("Use a cache."))
    }

    @Test fun `a permission is asked with the agent's own options, and the one chosen is its answer`() = runBlocking {
        val asked = CopyOnWriteArrayList<Pair<AssistantCall, List<AssistantOption>>>()
        fake.answer = { client ->
            val outcome = client.requestPermissions(
                SessionUpdate.ToolCallUpdate(ToolCallId("c1"), "Run rm -rf build", ToolKind.EXECUTE),
                listOf(
                    PermissionOption(com.agentclientprotocol.model.PermissionOptionId("allow"), "Allow", PermissionOptionKind.ALLOW_ONCE),
                    PermissionOption(com.agentclientprotocol.model.PermissionOptionId("always"), "Always allow", PermissionOptionKind.ALLOW_ALWAYS),
                    PermissionOption(com.agentclientprotocol.model.PermissionOptionId("reject"), "Reject", PermissionOptionKind.REJECT_ONCE),
                ),
            ).outcome
            emit(Event.SessionUpdateEvent(FakeAgents.chunk(((outcome as? RequestPermissionOutcome.Selected)?.optionId ?: PermissionOptionId("none")).value)))
            emit(FakeAgents.ended())
        }
        send("Clean the build") { call, options -> asked += call to options; "always" }
        val (call, options) = asked.single()
        assertEquals("Run rm -rf build", call.summary)
        assertEquals(AssistantToolKind.Command, call.kind)
        assertEquals(listOf(AssistantOption("allow", "Allow", true), AssistantOption("always", "Always allow", true, always = true), AssistantOption("reject", "Reject", false)), options)
        assertEquals("always", events.replies.single().text)
    }

    @Test fun `a permission left unanswered is cancelled, never allowed`() = runBlocking {
        fake.answer = { client ->
            val outcome = client.requestPermissions(SessionUpdate.ToolCallUpdate(ToolCallId("c1"), "Edit", ToolKind.EDIT), listOf(PermissionOption(PermissionOptionId("allow"), "Allow", PermissionOptionKind.ALLOW_ONCE))).outcome
            emit(Event.SessionUpdateEvent(FakeAgents.chunk(if (outcome is RequestPermissionOutcome.Cancelled) "cancelled" else "allowed")))
            emit(FakeAgents.ended())
        }
        send("Edit it") { _, _ -> null }
        assertEquals("cancelled", events.replies.single().text)
    }

    @Test fun `an agent that asks to be signed in says its ways, and once signed in the message goes`() = runBlocking {
        fake.signedIn = false
        fake.methods = listOf(
            AuthMethod.AgentAuth(AuthMethodId("login"), "Log in", "Opens the browser"),
            AuthMethod.EnvVarAuth(AuthMethodId("key"), "API key", null, listOf(AuthEnvVar("FAKE_KEY", "Key", secret = true, optional = false)), "https://example.com/keys"),
        )
        try {
            send("Hello")
            fail("The message went without a sign-in")
        } catch (needed: AcpAgents.SignInNeeded) {
            assertEquals(
                listOf(
                    AgentSignIn("login", "Log in", "Opens the browser", AgentSignInWay.ByAgent),
                    AgentSignIn("key", "API key", null, AgentSignInWay.Keys(listOf(AgentKey("FAKE_KEY", "Key", secret = true, optional = false)), "https://example.com/keys")),
                ),
                needed.methods,
            )
        }
        withTimeout(20_000) { agents.authenticate(AGENT, "login") }
        send("Hello")
        assertEquals("Done.", events.replies.single().text)
    }

    @Test fun `Stop cancels the agent's prompt`() = runBlocking {
        fake.answer = {
            emit(Event.SessionUpdateEvent(FakeAgents.chunk("Working")))
            awaitCancellation()
        }
        val sending = launch(Dispatchers.Default) { send("A long task") }
        withTimeout(20_000) { while (events.written.isEmpty()) kotlinx.coroutines.delay(5) }
        sending.cancel()
        withTimeout(20_000) { while (fake.cancelled.isEmpty()) kotlinx.coroutines.delay(5) }
        assertEquals("What was written is kept", "Working", events.replies.single().text)
    }

    @Test fun `a refusal or a limit ends the turn, said in plain words`() = runBlocking {
        fake.answer = { emit(FakeAgents.ended(StopReason.REFUSAL)) }
        try {
            send("Something")
            fail("A refusal went unsaid")
        } catch (refused: AssistantTurnException) {
            assertEquals("The agent refused to go on with this", refused.message)
        }
        fake.answer = { emit(FakeAgents.ended(StopReason.MAX_TURN_REQUESTS)) }
        try {
            send("Something else")
            fail("A limit went unsaid")
        } catch (limited: AssistantTurnException) {
            assertEquals("The agent reached its own limit of steps", limited.message)
        }
    }

    @Test fun `an agent no chat uses stops after its idle time, and starts again for the next message`() = runBlocking {
        idleMillis = 50
        send("Hello")
        withTimeout(20_000) { fake.programs.single().ended.await() }
        send("Again")
        assertEquals(2, fake.starts.size)
    }

    @Test fun `an agent not installed is said so`() = runBlocking {
        try {
            withTimeout(20_000) { turn.answer("missing", CHAT, place, emptyList(), AssistantMessage.User("Hi"), events) { _, _ -> null } }
            fail("A missing agent answered")
        } catch (missing: AssistantTurnException) {
            assertEquals("This agent is not installed: install it in Settings › Assistant › Coding Agents", missing.message)
        }
    }

    @Test fun `a text file goes with the message, and a picture is only named`() {
        val message = AssistantMessage.User(
            "Look",
            listOf(
                AssistantAttachment("src/App.kt", text = "fun main() {}"),
                AssistantAttachment("shot.png", AssistantAttachmentKind.Image, file = "a.png", mimeType = "image/png"),
            ),
        )
        assertEquals(
            "Look\n\nAttached file src/App.kt:\n```\nfun main() {}\n```\n\nAttached shot.png: pictures and documents are not sent to agents.",
            AcpTurn.prompt(emptyList(), message),
        )
    }

    private companion object {
        const val AGENT = "fake"
        val CHAT: Pair<String?, String> = "/workspaces/app" to "chat-1"
    }
}
