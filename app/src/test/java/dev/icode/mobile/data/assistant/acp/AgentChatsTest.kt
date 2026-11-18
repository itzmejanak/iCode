package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.SessionMode
import com.agentclientprotocol.model.SessionModeId
import dev.icode.mobile.data.assistant.AssistantChatStore
import dev.icode.mobile.data.assistant.AssistantChats
import dev.icode.mobile.data.assistant.AssistantPage
import dev.icode.mobile.data.assistant.InMemoryPreferences
import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AgentChatIntent
import dev.icode.mobile.domain.AgentSignIn
import dev.icode.mobile.domain.AgentSignInWay
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.InstalledAgent
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The agents on the assistant page: started as their chat is shown, their settings there, and their sign-in. */
class AgentChatsTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateHolder = ICodeStateHolder()
    private val fake = FakeAgents()
    private val saved = AtomicInteger()
    private val runtime by lazy { TestRuntime(temp.root) }
    private val agentChats by lazy {
        val chats = AssistantChats(AssistantChatStore(temp.newFolder("chats")))
        AgentChats(
            scope, AssistantPage(stateHolder, scope, chats), chats,
            AgentPreferences(InMemoryPreferences(), { it }, { it }),
            catalogue = { emptyList() },
            installed = { mapOf(CLAUDE to InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0)) },
            placeOf = { runtime.workspace },
            runInTab = { _, _, _ -> },
            saveOpen = { saved.incrementAndGet() },
            states = stateHolder.snapshot.map { it.assistant },
        ) { changed, stopped, records ->
            AcpAgents(scope, fake, { emptyMap() }, { mapOf(CLAUDE to InstalledAgent("1.0.0", listOf("/root/agent"), emptyMap(), 0)) }, { emptyMap() }, "1.2.3", changed = changed, stopped = stopped, records = records)
        }
    }

    @Before fun quiet() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun page(): AssistantState = stateHolder.current.assistant

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; page: ${page().agentChat} ${page().signIns}" }
            Thread.sleep(5)
        }
    }

    /** A new chat in the workspace shown, answered by [agent]. */
    private fun open(agent: String) = stateHolder.update { it.copy(assistant = it.assistant.copy(opened = true, workspace = "/workspaces/app", session = null, agent = agent)) }

    @Test fun `a new chat answered by an agent starts it, in its asking mode, with its settings on the page`() {
        fake.modes = listOf(SessionMode(SessionModeId("default"), "Manual", null), SessionMode(SessionModeId("auto"), "Auto", null))
        fake.startMode = "auto"
        agentChats.start()
        open(CLAUDE)
        until { page().agentChat?.settings?.isNotEmpty() == true }
        assertEquals("default", page().agentChat!!.settings.single().current)
        assertEquals(listOf(runtime.workspace.guestPath), fake.sessions)
    }

    @Test fun `a value chosen goes to the agent and shows`() {
        fake.modes = listOf(SessionMode(SessionModeId("default"), "Manual", null), SessionMode(SessionModeId("plan"), "Plan", null))
        agentChats.start()
        open(CLAUDE)
        until { page().agentChat?.settings?.isNotEmpty() == true }
        agentChats.handle(AgentChatIntent.Set(CLAUDE, "mode", "plan"))
        until { page().agentChat!!.settings.single().current == "plan" }
    }

    @Test fun `an agent that asks to be signed in shows its ways, and once signed in its settings`() {
        fake.signedIn = false
        fake.methods = listOf(AuthMethod.AgentAuth(AuthMethodId("login"), "Log in", null))
        fake.modes = listOf(SessionMode(SessionModeId("default"), "Manual", null))
        agentChats.start()
        open(CLAUDE)
        until { CLAUDE in page().signIns }
        assertEquals(listOf(AgentSignIn("login", "Log in", null, AgentSignInWay.ByAgent)), page().signIns.getValue(CLAUDE))
        agentChats.handle(AgentChatIntent.SignIn(CLAUDE, "login"))
        until { CLAUDE !in page().signIns && page().agentChat?.settings?.isNotEmpty() == true }
    }

    @Test fun `a chat answered by a model shows no agent's settings`() {
        agentChats.start()
        open(CLAUDE)
        until { page().agentChat != null }
        stateHolder.update { it.copy(assistant = it.assistant.copy(agent = null)) }
        until { page().agentChat == null }
        assertNull(page().agentChat)
    }

    private companion object {
        const val CLAUDE = "claude-acp"
    }
}
