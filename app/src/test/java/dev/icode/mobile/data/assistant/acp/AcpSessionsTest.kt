package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.model.CreateElicitationRequest
import com.agentclientprotocol.model.ElicitationScope
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionInfo
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.Cost
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.AvailableCommand
import com.agentclientprotocol.model.AvailableCommandInput
import com.agentclientprotocol.model.CreateElicitationResponse
import com.agentclientprotocol.model.ElicitationAction
import com.agentclientprotocol.model.ElicitationContentValue
import com.agentclientprotocol.model.ElicitationMode
import com.agentclientprotocol.model.ElicitationPropertySchema
import com.agentclientprotocol.model.ElicitationSchema
import com.agentclientprotocol.model.EnumOption
import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.PlanEntryPriority
import com.agentclientprotocol.model.PlanEntryStatus
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigSelectOption
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionConfigValueId
import com.agentclientprotocol.model.SessionMode
import com.agentclientprotocol.model.SessionModeId
import com.agentclientprotocol.model.SessionUpdate
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.domain.AgentCommand
import dev.icode.mobile.domain.AgentPlanStatus
import dev.icode.mobile.domain.AgentPlanStep
import dev.icode.mobile.domain.AgentSetting
import dev.icode.mobile.domain.AgentValue
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.InstalledAgent
import dev.icode.mobile.domain.McpAnswer
import dev.icode.mobile.domain.McpField
import dev.icode.mobile.domain.McpQuestion
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** An agent's sessions: the mode they start in, their settings and commands, a new chat's, and the to-do list. */
@OptIn(UnstableApi::class)
class AcpSessionsTest {
    @get:Rule val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fake = FakeAgents()
    private val changed = CopyOnWriteArrayList<Pair<String, Pair<String?, String>>>()
    private val agents by lazy {
        AcpAgents(
            scope, fake,
            login = { emptyMap() },
            installed = { listOf(CLAUDE, CLINE, OTHER).associateWith { InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0) } },
            keys = { emptyMap() },
            version = "1.2.3",
            changed = { agent, chat -> changed += agent to chat },
        )
    }
    private val place by lazy { TestRuntime(temp.root).workspace }

    @After fun stop() = scope.cancel()

    private fun mode(id: String, name: String) = SessionMode(SessionModeId(id), name, null)

    private suspend fun prepared(agent: String, chat: Pair<String?, String>) =
        withTimeout(20_000) { agents.use(agent) { agents.prepare(it, chat, place.guestPath) } }

    @Test fun `a chat starts in the agent's asking mode, though the agent would pick another`() = runBlocking {
        fake.modes = listOf(mode("default", "Manual"), mode("acceptEdits", "Accept edits"), mode("auto", "Auto"))
        fake.startMode = "auto"
        val held = prepared(CLAUDE, CHAT)
        assertEquals(listOf("mode" to "default"), fake.sets.toList())
        val setting = AcpSettings.of(held.session).single()
        assertEquals("default", setting.current)
        assertEquals(listOf("default", "acceptEdits", "auto"), setting.values.map { it.id })
    }

    @Test fun `a mode the agent reports itself replaces the one iCode set`() = runBlocking {
        fake.modes = listOf(mode("default", "Manual"), mode("plan", "Plan"))
        fake.answer = {
            emit(Event.SessionUpdateEvent(SessionUpdate.CurrentModeUpdate(SessionModeId("plan"))))
            emit(FakeAgents.ended())
        }
        withTimeout(20_000) { AcpTurn(agents).answer(CLAUDE, CHAT, place, emptyList(), AssistantMessage.User("Plan first"), Recorded()) { _, _ -> null } }
        assertEquals("plan", AcpSettings.of(agents.held(CLAUDE, CHAT)!!.session).single().current)
    }

    @Test fun `once the agent has stopped, the chat takes its session up again where the agent can, and sends only what is new`() = runBlocking {
        fake.loads = true
        val records = Kept()
        val first = AcpAgents(scope, fake, { emptyMap() }, { mapOf(OTHER to InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0)) }, { emptyMap() }, "1.2.3", records = records)
        withTimeout(20_000) { AcpTurn(first).answer(OTHER, CHAT, place, emptyList(), AssistantMessage.User("One"), Recorded()) { _, _ -> null } }
        val id = records.ids.getValue(OTHER to CHAT)
        first.restart(OTHER)
        val history = listOf(AssistantMessage.User("One"), AssistantMessage.Reply("Done.", by = AssistantAgent(OTHER)))
        withTimeout(20_000) { AcpTurn(first).answer(OTHER, CHAT, place, history, AssistantMessage.User("Two"), Recorded()) { _, _ -> null } }
        assertEquals(listOf(id), fake.loaded)
        assertEquals("Two", fake.prompts.last())
        assertEquals("One session made, then taken up again", 1, fake.sessions.size)
    }

    @Test fun `an agent that cannot take a session up again starts a new one, sent the chat so far`() = runBlocking {
        val records = Kept()
        val first = AcpAgents(scope, fake, { emptyMap() }, { mapOf(OTHER to InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0)) }, { emptyMap() }, "1.2.3", records = records)
        withTimeout(20_000) { AcpTurn(first).answer(OTHER, CHAT, place, emptyList(), AssistantMessage.User("One"), Recorded()) { _, _ -> null } }
        first.restart(OTHER)
        val history = listOf(AssistantMessage.User("One"), AssistantMessage.Reply("Done.", by = AssistantAgent(OTHER)))
        withTimeout(20_000) { AcpTurn(first).answer(OTHER, CHAT, place, history, AssistantMessage.User("Two"), Recorded()) { _, _ -> null } }
        assertTrue(fake.loaded.isEmpty())
        assertTrue(fake.prompts.last().startsWith("The conversation so far in this chat"))
        assertEquals(2, fake.sessions.size)
    }

    @Test fun `the sessions a chat no longer uses are deleted by an agent that deletes sessions, started again for it`() = runBlocking {
        fake.deletes = true
        val records = Kept()
        val first = AcpAgents(scope, fake, { emptyMap() }, { mapOf(OTHER to InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0)) }, { emptyMap() }, "1.2.3", records = records)
        withTimeout(20_000) { AcpTurn(first).answer(OTHER, CHAT, place, emptyList(), AssistantMessage.User("One"), Recorded()) { _, _ -> null } }
        val id = records.ids.getValue(OTHER to CHAT)
        first.restart(OTHER)
        first.forget(CHAT)
        withTimeout(20_000) { while (fake.deleted.isEmpty()) delay(5) }
        assertEquals(listOf(id), fake.deleted.toList())
        assertTrue(records.ids.isEmpty())
        assertEquals("Started again to delete it", 2, fake.programs.size)
    }

    @Test fun `an agent that does not delete sessions is not asked to, nor started again for it`() = runBlocking {
        val records = Kept()
        val first = AcpAgents(scope, fake, { emptyMap() }, { mapOf(OTHER to InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0)) }, { emptyMap() }, "1.2.3", records = records)
        withTimeout(20_000) { AcpTurn(first).answer(OTHER, CHAT, place, emptyList(), AssistantMessage.User("One"), Recorded()) { _, _ -> null } }
        first.restart(OTHER)
        first.forget(CHAT)
        delay(300)
        assertTrue(fake.deleted.isEmpty())
        assertEquals(1, fake.programs.size)
    }

    @Test fun `an agent lists its sessions in a chat's place, but none a chat holds`() = runBlocking {
        fake.lists = true
        val held = prepared(CLAUDE, CHAT)
        fake.listed = listOf(
            SessionInfo(SessionId("s-out"), place.guestPath, "Fix the login", "2026-11-17T10:00:00Z"),
            SessionInfo(held.session.sessionId, place.guestPath, "Held"),
            SessionInfo(SessionId("s-else"), "/elsewhere", "Elsewhere"),
        )
        assertEquals(listOf("s-out"), withTimeout(20_000) { agents.listed(CLAUDE, place.guestPath) }.map { it.sessionId.value })
    }

    @Test fun `an agent that lists no sessions is not started again to be asked`() = runBlocking {
        prepared(CLAUDE, CHAT)
        agents.restart(CLAUDE)
        assertEquals(emptyList<SessionInfo>(), agents.listed(CLAUDE, place.guestPath))
        assertEquals(1, fake.programs.size)
    }

    @Test fun `a session taken up is loaded and its conversation replayed into the chat's messages`() = runBlocking {
        fake.loads = true
        fake.history = listOf(
            SessionUpdate.UserMessageChunk(ContentBlock.Text("Fix the ")),
            SessionUpdate.UserMessageChunk(ContentBlock.Text("login")),
            FakeAgents.chunk("Looking."),
            SessionUpdate.ToolCall(ToolCallId("c1"), "List files", ToolKind.EXECUTE, ToolCallStatus.COMPLETED, content = listOf(ToolCallContent.Content(ContentBlock.Text("a.kt")))),
            SessionUpdate.UsageUpdate(10, 100, Cost(1.5, "USD")),
            FakeAgents.chunk("Done."),
            SessionUpdate.UserMessageChunk(ContentBlock.Text("Thanks")),
        )
        val records = Kept()
        val first = AcpAgents(scope, fake, { emptyMap() }, { mapOf(OTHER to InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0)) }, { emptyMap() }, "1.2.3", records = records)
        records.keep(OTHER, CHAT, "s-out")
        val replay = AcpReplay(OTHER) { null }
        val held = withTimeout(20_000) { first.use(OTHER) { first.prepare(it, CHAT, place.guestPath, replay::on) } }
        assertEquals("s-out", held.session.sessionId.value)
        assertTrue("Nothing more goes with its first message", held.prompted)
        assertTrue(fake.sessions.isEmpty())
        val messages = replay.messages()
        assertEquals(listOf("User", "Reply", "Results", "Reply", "User"), messages.map { it.javaClass.simpleName })
        assertEquals("Fix the login", (messages[0] as AssistantMessage.User).text)
        assertEquals(AssistantMessage.Reply("Looking.", (messages[1] as AssistantMessage.Reply).calls, by = AssistantAgent(OTHER)), messages[1])
        assertEquals("a.kt", (messages[2] as AssistantMessage.Results).results.single().output)
        assertEquals(AssistantMessage.Reply("Done.", by = AssistantAgent(OTHER)), messages[3])
    }

    @Test fun `a session to take up that the agent cannot load is said, never replaced by a new one`() = runBlocking {
        val records = Kept()
        val first = AcpAgents(scope, fake, { emptyMap() }, { mapOf(OTHER to InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0)) }, { emptyMap() }, "1.2.3", records = records)
        records.keep(OTHER, CHAT, "s-out")
        val failure = runCatching { withTimeout(20_000) { first.use(OTHER) { first.prepare(it, CHAT, place.guestPath) {} } } }.exceptionOrNull()
        assertEquals("The agent could not load this chat", failure?.message)
        assertTrue(fake.sessions.isEmpty())
        assertEquals("s-out", records.ids[OTHER to CHAT])
    }

    @Test fun `a chat the agent has not replied in yet keeps its session, so one taken up is never deleted`() = runBlocking {
        fake.loads = true
        fake.deletes = true
        val records = Kept()
        val first = AcpAgents(scope, fake, { emptyMap() }, { mapOf(OTHER to InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0)) }, { emptyMap() }, "1.2.3", records = records)
        records.keep(OTHER, CHAT, "s-out")
        withTimeout(20_000) { AcpTurn(first).answer(OTHER, CHAT, place, listOf(AssistantMessage.User("One")), AssistantMessage.User("Two"), Recorded()) { _, _ -> null } }
        assertEquals(listOf("s-out"), fake.loaded.toList())
        assertEquals("Two", fake.prompts.last())
        delay(200)
        assertTrue(fake.deleted.isEmpty())
    }

    @Test fun `the window an agent reports is kept, so the next new session can tell what fits`() = runBlocking {
        val kept = java.util.concurrent.ConcurrentHashMap<String, Long>()
        val windows = object : AcpTurn.Windows {
            override fun of(agent: String) = kept[agent]

            override fun keep(agent: String, tokens: Long) {
                kept[agent] = tokens
            }
        }
        fake.answer = {
            emit(Event.SessionUpdateEvent(SessionUpdate.UsageUpdate(1_200, 200_000)))
            emit(Event.SessionUpdateEvent(FakeAgents.chunk("Done.")))
            emit(FakeAgents.ended())
        }
        withTimeout(20_000) { AcpTurn(agents, windows).answer(CLAUDE, CHAT, place, emptyList(), AssistantMessage.User("Hi"), Recorded()) { _, _ -> null } }
        assertEquals(200_000L, kept[CLAUDE])
    }

    @Test fun `an agent's question is asked as the card asks it, and the answer goes back as its fields' types`() = runBlocking {
        val shown = CopyOnWriteArrayList<McpQuestion>()
        val questioning = AcpAgents(
            scope, fake, { emptyMap() }, { mapOf(OTHER to InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0)) }, { emptyMap() }, "1.2.3",
            asked = { agent, request ->
                val question = AcpQuestions.questionOf(7, agent, request)!!
                shown += question
                AcpQuestions.responseOf(McpAnswer(7, McpAnswer.Action.Accept, mapOf("colour" to "blue", "count" to "3", "sure" to "true")), question)
            },
        )
        var answer: CreateElicitationResponse? = null
        fake.answer = { client ->
            val form = ElicitationSchema(
                properties = mapOf(
                    "colour" to ElicitationPropertySchema.StringProperty(title = "Colour", oneOf = listOf(EnumOption("blue", "Blue"), EnumOption("red", "Red"))),
                    "count" to ElicitationPropertySchema.IntegerProperty(title = "How many"),
                    "sure" to ElicitationPropertySchema.BooleanProperty(title = "Sure"),
                ),
                required = listOf("colour"),
            )
            // The fake agent's first session is "s1".
            answer = client.createElicitation(CreateElicitationRequest(ElicitationScope.Session(SessionId("s1")), ElicitationMode.Form(form), "Pick one"))
            emit(FakeAgents.ended())
        }
        withTimeout(20_000) { AcpTurn(questioning).answer(OTHER, CHAT, place, emptyList(), AssistantMessage.User("Ask me"), Recorded()) { _, _ -> null } }
        assertTrue("Questions are offered as forms and pages", fake.client!!.capabilities.elicitation?.let { it.supportsForm && it.supportsUrl } == true)
        val question = shown.single()
        assertEquals("Pick one", question.message)
        assertEquals(
            listOf(
                McpField("colour", "Colour", "", McpField.Kind.Choice, true, listOf("blue" to "Blue", "red" to "Red")),
                McpField("count", "How many", "", McpField.Kind.Integer, false),
                McpField("sure", "Sure", "", McpField.Kind.Flag, false),
            ),
            question.fields,
        )
        assertEquals(
            ElicitationAction.Accept(mapOf("colour" to ElicitationContentValue.StringValue("blue"), "count" to ElicitationContentValue.IntegerValue(3), "sure" to ElicitationContentValue.BooleanValue(true))),
            answer!!.action,
        )
    }

    @Test fun `an agent is not told it may ask where nothing can ask the user`() = runBlocking {
        withTimeout(20_000) { AcpTurn(agents).answer(OTHER, CHAT, place, emptyList(), AssistantMessage.User("Hi"), Recorded()) { _, _ -> null } }
        assertEquals(null, fake.client!!.capabilities.elicitation)
    }

    /** The chats' record of each agent's session, in memory. */
    private class Kept : AcpAgents.Records {
        val ids = java.util.concurrent.ConcurrentHashMap<Pair<String, Pair<String?, String>>, String>()

        override fun of(agent: String, chat: Pair<String?, String>) = ids[agent to chat]

        override fun keep(agent: String, chat: Pair<String?, String>, id: String) {
            ids[agent to chat] = id
        }

        override fun drop(chat: Pair<String?, String>): Map<String, String> {
            val dropped = ids.filterKeys { it.second == chat }
            ids.keys.removeAll(dropped.keys)
            return dropped.mapKeys { it.key.first }
        }
    }

    @Test fun `an agent iCode has not checked starts as it starts`() = runBlocking {
        fake.modes = listOf(mode("build", "Build"), mode("plan", "Plan"))
        fake.startMode = "build"
        val held = prepared(OTHER, CHAT)
        assertTrue(fake.sets.isEmpty())
        assertEquals("build", AcpSettings.of(held.session).single().current)
    }

    @Test fun `options are settings by their kind, a switch starts in its asking value, and a value set goes to the agent`() = runBlocking {
        fake.options = listOf(
            SessionConfigOption.Select(
                SessionConfigId("mode"), "Session Mode", null, com.agentclientprotocol.model.SessionConfigOptionCategory("mode"), SessionConfigValueId("act"),
                SessionConfigSelectOptions.Flat(listOf(SessionConfigSelectOption(SessionConfigValueId("plan"), "Plan", null), SessionConfigSelectOption(SessionConfigValueId("act"), "Act", null))),
            ),
            SessionConfigOption.BooleanOption(SessionConfigId("auto_approve"), "Auto-approve tools", null, null, true),
        )
        val held = prepared(CLINE, CHAT)
        assertEquals(listOf("auto_approve" to "false"), fake.sets.toList())
        assertEquals(
            listOf(
                AgentSetting("mode", "Session Mode", "mode", "act", listOf(AgentValue("plan", "Plan"), AgentValue("act", "Act"))),
                AgentSetting("auto_approve", "Auto-approve tools", null, "false"),
            ),
            AcpSettings.of(held.session),
        )
        AcpSettings.set(held.session, "mode", "plan")
        assertEquals("mode" to "plan", fake.sets.last())
        assertEquals("plan", AcpSettings.of(held.session).first().current)
    }

    @Test fun `the commands an agent says once a session is made are kept for its chat, and the chat is told`() = runBlocking {
        fake.commands = listOf(AvailableCommand("review", "Review the changes", AvailableCommandInput.Unstructured("what to review")))
        val held = prepared(OTHER, CHAT)
        withTimeout(20_000) { while (held.operations.commands.isEmpty()) delay(5) }
        assertEquals(listOf(AgentCommand("review", "Review the changes", "what to review")), held.operations.commands)
        assertTrue(OTHER to CHAT in changed)
    }

    @Test fun `a new chat's session, made while it was empty, takes its first message with the mode chosen meanwhile`() = runBlocking {
        fake.modes = listOf(mode("default", "Manual"), mode("plan", "Plan"))
        val empty = CHAT.first to AcpAgents.NEW_CHAT
        val early = prepared(CLAUDE, empty)
        AcpSettings.set(early.session, "mode", "plan")
        val events = Recorded()
        withTimeout(20_000) { AcpTurn(agents).answer(CLAUDE, CHAT, place, emptyList(), AssistantMessage.User("Plan it"), events) { _, _ -> null } }
        assertEquals("One session, made as the chat opened", 1, fake.sessions.size)
        assertEquals(early, agents.held(CLAUDE, CHAT))
        assertEquals("plan", AcpSettings.of(early.session).single().current)
    }

    @Test fun `an agent's to-do list is the turn's, as it last said it`() = runBlocking {
        fake.answer = {
            emit(Event.SessionUpdateEvent(SessionUpdate.PlanUpdate(listOf(PlanEntry("Read the code", PlanEntryPriority.HIGH, PlanEntryStatus.COMPLETED), PlanEntry("Fix it", PlanEntryPriority.HIGH, PlanEntryStatus.IN_PROGRESS), PlanEntry("Test it", PlanEntryPriority.LOW, PlanEntryStatus.PENDING)))))
            emit(FakeAgents.ended())
        }
        val events = Recorded()
        withTimeout(20_000) { AcpTurn(agents).answer(OTHER, CHAT, place, emptyList(), AssistantMessage.User("Fix it"), events) { _, _ -> null } }
        assertEquals(
            listOf(AgentPlanStep("Read the code", AgentPlanStatus.Done), AgentPlanStep("Fix it", AgentPlanStatus.Working), AgentPlanStep("Test it", AgentPlanStatus.Pending)),
            events.plans.single(),
        )
    }

    private class Recorded : AssistantEvents {
        val plans = CopyOnWriteArrayList<List<AgentPlanStep>>()

        override fun writing(reasoning: String, text: String) = Unit
        override fun replied(reply: AssistantMessage.Reply) = Unit
        override fun calling(call: AssistantCall) = Unit
        override fun answered(result: AssistantResult) = Unit
        override fun used(tokens: Long) = Unit
        override fun planned(steps: List<AgentPlanStep>) {
            plans += steps
        }
    }

    private companion object {
        const val CLAUDE = "claude-acp"
        const val CLINE = "cline"
        const val OTHER = "opencode"
        val CHAT: Pair<String?, String> = "/workspaces/app" to "chat-1"
    }
}
