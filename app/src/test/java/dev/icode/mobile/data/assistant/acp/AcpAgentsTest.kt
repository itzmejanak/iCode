package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.PlanEntryPriority
import com.agentclientprotocol.model.PlanEntryStatus
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallLocation
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import dev.icode.mobile.data.assistant.AssistantAttachments
import dev.icode.mobile.data.assistant.AssistantChatStore
import dev.icode.mobile.data.assistant.AssistantChats
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantCoordinator
import dev.icode.mobile.data.assistant.AssistantEngine
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantTurnRequest
import dev.icode.mobile.data.assistant.InMemoryPreferences
import dev.icode.mobile.data.assistant.SharedPreferencesAssistantProviderStore
import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AgentIntent
import dev.icode.mobile.domain.AgentPlanItem
import dev.icode.mobile.domain.AssistantApprovalChoice
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.CodingAgent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.LineChanges
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A chat answered by a coding agent, end to end (docs/assistant-and-mcp.md): the assistant's coordinator and
 * turn job, iCode's ACP client, and an agent built on the same official SDK, over the standard streams of
 * its "process". No provider is set up: the agent answers alone.
 */
@OptIn(UnstableApi::class)
class AcpAgentsTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val stateHolder = ICodeStateHolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val agent = FakeAcpAgent()
    private val runtime by lazy { TestRuntime(temp.newFolder("runtime")) }
    private val app by lazy { runtime.root.path }
    private val chatsFolder by lazy { temp.newFolder("chats") }
    private val entry = AgentEntry(
        CodingAgent("fake", "Fake Agent", "1.0.0", "An agent in a test", "MIT", null, null, "agent-fake", npm = false),
        AgentDistribution.Binary("https://example.invalid/fake.tar.gz", "0".repeat(64), "./fake-agent", emptyList(), emptyMap()),
    )
    private var refreshed = 0

    private val engine = object : AssistantEngine {
        override val catalogue = emptyMap<AssistantProviderKind, List<AssistantModel>>()
        override fun model(kind: AssistantProviderKind, spec: AssistantModelSpec) = AssistantModel(spec.id)
        override suspend fun available(connection: AssistantConnection) = emptyList<AssistantModel>()
        override suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents) = error("The built-in assistant answers no chat here")
        override suspend fun compact(connection: AssistantConnection, model: AssistantModel, instructions: String, history: List<AssistantMessage>) = ""
        override suspend fun answer(connection: AssistantConnection, model: AssistantModel, instructions: String, request: String) = ""
    }

    /** A coordinator, as one process has it: a second one is iCode started again, over the same chats. */
    private fun coordinator() = AssistantCoordinator(
        stateHolder, scope, engine,
        SharedPreferencesAssistantProviderStore(InMemoryPreferences(), { it }, { it }),
        AssistantChats(AssistantChatStore(chatsFolder)),
        tools = { _, _, _ -> emptyList() },
        activeRoot = { app },
        describe = { WorkspaceRoot("app", it, "/workspace/app") },
        paths = runtime.paths,
        attachments = { openText -> AssistantAttachments({ null }, { it }, openText, readText = { File(it).readText() }) },
        git = { _, _ -> CliGitService.CommandResult(0, "", "") },
        storageAccess = { true },
        agentsOf = { context ->
            AcpAgents(
                context, listOf(entry), AgentLauncher(runtime.rootfs, { mapOf("PATH" to "/usr/bin") }, agent.programs(scope)),
                AgentStore(InMemoryPreferences(), { it }, { it }).apply { choose("fake") },
                AcpServers { emptyList() }, terminal = { _, _, _ -> "tab-1" }, refreshFiles = { refreshed++ },
            )
        },
    )

    @Before fun start() {
        BackendLog.writer = { _, _, _ -> }
        // Installed: the program its registry entry starts is in the runtime.
        File(runtime.rootfs, "root/.local/share/icode/agents/fake/fake-agent").apply { parentFile!!.mkdirs(); writeText("#!/bin/sh\n") }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    @Test fun aNewChatGoesToTheChosenAgentAndItsTurnIsTheChat() {
        agent.script = { _ ->
            send(SessionUpdate.AgentThoughtChunk(ContentBlock.Text("Reading the file")))
            send(SessionUpdate.AgentMessageChunk(ContentBlock.Text("I'll edit app.kt.")))
            send(SessionUpdate.ToolCall(ToolCallId("t1"), "Edit app.kt", ToolKind.EDIT, ToolCallStatus.PENDING, locations = listOf(ToolCallLocation("/workspace/app/app.kt"))))
            send(SessionUpdate.PlanUpdate(listOf(PlanEntry("Edit app.kt", PlanEntryPriority.HIGH, PlanEntryStatus.IN_PROGRESS))))
            val answer = client.requestPermissions(
                SessionUpdate.ToolCallUpdate(ToolCallId("t1"), title = "Edit app.kt", kind = ToolKind.EDIT),
                listOf(
                    PermissionOption(PermissionOptionId("allow"), "Allow", PermissionOptionKind.ALLOW_ONCE),
                    PermissionOption(PermissionOptionId("reject"), "Reject", PermissionOptionKind.REJECT_ONCE),
                ),
            )
            val allowed = (answer.outcome as? RequestPermissionOutcome.Selected)?.optionId?.value == "allow"
            send(
                SessionUpdate.ToolCallUpdate(
                    ToolCallId("t1"), status = if (allowed) ToolCallStatus.COMPLETED else ToolCallStatus.FAILED,
                    content = listOf(ToolCallContent.Diff("/workspace/app/app.kt", "fun a() = 1\nfun b() = 2\n", "fun a() = 1\n")),
                ),
            )
            send(SessionUpdate.AgentMessageChunk(ContentBlock.Text("Done.")))
        }
        val coordinator = coordinator().apply { start() }
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = true))
        until { state().opened && stateHolder.current.agents.chat?.agent == "fake" && stateHolder.current.agents.chat?.session == null }

        coordinator.handle(ICodeIntent.SendAssistantMessage("Add b()"))
        until { state().approval != null }
        val approval = state().approval!!
        assertEquals(listOf("Allow", "Reject"), approval.choices.map { it.label })
        assertEquals(AssistantApprovalChoice.Kind.AllowOnce, approval.choices.first().kind)
        assertEquals("Edit app.kt", approval.call.summary)
        coordinator.handle(AgentIntent.AnswerApproval(approval.call.id, "allow"))
        until { state().turn == null && state().messages.lastOrNull() is AssistantMessage.Reply && (state().messages.last() as AssistantMessage.Reply).text == "Done." }

        assertEquals(listOf("Add b()"), agent.prompts)
        val messages = state().messages
        assertEquals(AssistantMessage.User("Add b()"), messages[0])
        val reply = messages[1] as AssistantMessage.Reply
        assertEquals("I'll edit app.kt.", reply.text)
        assertEquals("Reading the file", reply.reasoning)
        assertEquals(AssistantToolKind.Change, reply.calls.single().kind)
        assertEquals("app.kt", reply.calls.single().file)
        val result = (messages[2] as AssistantMessage.Results).results.single()
        assertEquals(LineChanges(1, 0), result.changes)
        assertTrue(!result.failed)
        assertNull(state().failure)
        assertEquals(1, refreshed)
        // What the agent offers the chat: its model as a chip, and its plan.
        val chat = stateHolder.current.agents.chat!!
        assertEquals("Sonnet", (chat.options.single() as dev.icode.mobile.domain.AgentOption.Select).currentName)
        assertEquals(AgentPlanItem.Status.InProgress, chat.plan.single().status)
        // The chat is listed as the agent's, and its file keeps no transcript: the agent does.
        val session = state().session!!
        assertEquals("fake", state().sessions.single { it.id == session }.agent)
        val file = chatsFolder.walkTopDown().first { it.name == "$session.json" }
        val saved = JSONObject(file.readText())
        assertEquals(0, saved.getJSONArray("messages").length())
        assertEquals("fake", saved.getJSONObject("agent").getString("id"))
        assertEquals("s-1", saved.getJSONObject("agent").getString("session"))
    }

    @Test fun aChatOpenedAgainIsReadBackFromItsAgent() {
        agent.script = { send(SessionUpdate.AgentMessageChunk(ContentBlock.Text("Hello from the agent"))) }
        coordinator().apply {
            start()
            handle(ICodeIntent.OpenAssistant(inWorkspace = true))
            until { state().opened }
            handle(ICodeIntent.SendAssistantMessage("Hi"))
        }
        until { state().turn == null && state().messages.size == 2 }
        val session = state().session!!

        // iCode starts again: a new coordinator over the same chats, and a new process of the same agent.
        stateHolder.update { it.copy(assistant = AssistantState(), agents = dev.icode.mobile.domain.AgentsState()) }
        val again = coordinator().apply { start() }
        again.handle(ICodeIntent.OpenAssistant(inWorkspace = true))
        until { state().opened }
        again.handle(ICodeIntent.OpenAssistantSession(session))
        until { state().messages.size == 2 }
        assertEquals(listOf("s-1"), agent.loaded)
        assertEquals(listOf(AssistantMessage.User("Hi"), AssistantMessage.Reply("Hello from the agent", emptyList(), "")), state().messages)
        assertEquals(false, stateHolder.current.agents.chat?.loading)
    }

    @Test fun stopCancelsThePromptInTheAgent() {
        agent.script = {
            send(SessionUpdate.AgentMessageChunk(ContentBlock.Text("Working")))
            awaitCancellation()
        }
        val coordinator = coordinator().apply { start() }
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = true))
        until { state().opened }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Long task"))
        until { state().turn?.live?.text == "Working" }
        coordinator.handle(ICodeIntent.StopAssistant)
        until { state().turn == null }
        until { agent.cancels.get() == 1 }
        assertNull(state().failure)
        assertEquals("Working", (state().messages.last() as AssistantMessage.Reply).text)
    }

    @Test fun anAgentThatAsksToSignInOffersHowAndSignsIn() {
        agent.needsSignIn = true
        val coordinator = coordinator().apply { start() }
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = true))
        until { state().opened }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Hi"))
        until { state().turn == null && state().failure != null }
        assertTrue(state().failure!!.startsWith("Sign in to Fake Agent first"))
        until { stateHolder.current.agents.chat?.signIn?.isNotEmpty() == true }
        assertEquals("Log In", stateHolder.current.agents.chat!!.signIn.single().name)

        coordinator.handle(AgentIntent.SignIn("login"))
        until { agent.signedIn == 1 && stateHolder.current.agents.chat?.signIn?.isEmpty() == true }
        coordinator.handle(ICodeIntent.RetryAssistantMessage)
        until { state().turn == null && state().messages.lastOrNull() is AssistantMessage.Reply }
        assertEquals(listOf("Hi"), agent.prompts)
    }

    @Test fun theAgentsModelIsChangedFromItsChip() {
        val coordinator = coordinator().apply { start() }
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = true))
        until { state().opened }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Hi"))
        until { state().turn == null && state().messages.size == 2 }
        coordinator.handle(AgentIntent.SetOption("model", "opus"))
        until { (stateHolder.current.agents.chat?.options?.single() as? dev.icode.mobile.domain.AgentOption.Select)?.currentName == "Opus" }
    }

    private fun state(): AssistantState = stateHolder.current.assistant

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; assistant: ${state()}; agents: ${stateHolder.current.agents}" }
            Thread.sleep(5)
        }
    }
}
