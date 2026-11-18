package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.AvailableCommand
import com.agentclientprotocol.model.AvailableCommandInput
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
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.InstalledAgent
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
