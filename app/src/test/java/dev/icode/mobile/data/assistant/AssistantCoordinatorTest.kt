package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSettings
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
import java.io.File
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AssistantCoordinatorTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val workbench = WorkbenchStateHolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val effects = Collections.synchronizedList(mutableListOf<WorkbenchEffect>())
    private val providers = FakeProviders()
    private val chats by lazy { AssistantChats(AssistantChatStore(temp.newFolder("chats"))) }
    private val app by lazy { temp.newFolder("app").path }
    private var script: suspend (AssistantTurnRequest, AssistantEvents) -> Unit = { _, _ -> }
    private val requests = Collections.synchronizedList(mutableListOf<AssistantTurnRequest>())
    private val ran = Collections.synchronizedList(mutableListOf<String>())

    private val engine = object : AssistantEngine {
        override val suggestions = mapOf(AssistantProviderKind.OpenAI to listOf("gpt-5"))
        override suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents) {
            requests += turn
            script(turn, events)
        }
    }

    private val coordinator by lazy {
        AssistantCoordinator(
            workbench, scope, engine, providers, chats,
            tools = { listOf(FakeTool("read_file", AssistantToolKind.Read), FakeTool("edit_file", AssistantToolKind.Change)) },
            activeRoot = { app },
            describe = { WorkspaceRoot("app", it, "/workspace/app") },
        )
    }

    @Before fun start() {
        BackendLog.writer = { _, _, _ -> }
        scope.launch { workbench.effects.collect { effects += it } }
        coordinator.start()
        until { state().settings != null }
        coordinator.handle(WorkbenchIntent.OpenAssistant)
        until { state().workspace == app }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun state(): AssistantState = workbench.current.assistant

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; assistant: ${state()}" }
            Thread.sleep(5)
        }
    }

    private val edit = AssistantCall("c1", "edit_file", """{"path":"a.kt"}""", "Edit a.kt", AssistantToolKind.Change)

    /** A turn as an engine runs one: a reply calling [edit], its result as the runner would give it, then "Done." */
    private fun editTurn() {
        script = { turn, events ->
            events.text("Let me ")
            events.text("edit.")
            events.replied(AssistantMessage.Reply("Let me edit.", listOf(edit)))
            events.calling(edit)
            val tool = turn.tools.single { it.name == "edit_file" }
            // As Koog's runner does: a refusal is the result, a cancellation is not.
            val result = try {
                Result.success(tool.run(JSONObject(edit.arguments)))
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (refused: Exception) {
                Result.failure(refused)
            }
            events.answered(
                AssistantMessage.Results(listOf(AssistantResult("c1", "edit_file", result.getOrElse { it.message!! }, result.isFailure))),
            )
            events.replied(AssistantMessage.Reply("Done."))
        }
    }

    @Test fun `a change waits for Allow, runs, and the chat is kept`() {
        editTurn()
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("  Rename x  "))
        until { state().approval != null }
        assertEquals("Edit a.kt", state().approval!!.call.summary)
        assertEquals("Waiting for your answer", state().turn!!.step)
        assertTrue(ran.isEmpty())
        coordinator.handle(WorkbenchIntent.AnswerAssistant("c1", AssistantAnswer.Allow))
        until { state().turn == null }
        assertEquals(listOf("edit_file"), ran)
        assertEquals("Rename x", requests.single().input)
        assertEquals(4, state().messages.size)
        assertEquals("Done.", (state().messages.last() as AssistantMessage.Reply).text)
        assertEquals(state().messages, AssistantChatStore(File(temp.root, "chats")).load(app))
        assertEquals("", state().streaming)
        assertNull(state().failure)
    }

    @Test fun `Deny tells the model the change was refused, and nothing runs`() {
        editTurn()
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("Rename x"))
        until { state().approval != null }
        coordinator.handle(WorkbenchIntent.AnswerAssistant("c1", AssistantAnswer.Deny))
        until { state().turn == null }
        assertTrue(ran.isEmpty())
        val result = (state().messages[2] as AssistantMessage.Results).results.single()
        assertTrue(result.failed)
        assertEquals("The user did not allow this change", result.output)
    }

    @Test fun `Allow All answers this chat's next changes, and setting Ask off answers every one`() {
        editTurn()
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("one"))
        until { state().approval != null }
        coordinator.handle(WorkbenchIntent.AnswerAssistant("c1", AssistantAnswer.AllowAll))
        until { state().turn == null }
        assertTrue(state().allowAll)
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("two"))
        until { requests.size == 2 && state().turn == null }
        assertEquals(listOf("edit_file", "edit_file"), ran)

        coordinator.handle(WorkbenchIntent.NewAssistantChat)
        until { state().messages.isEmpty() }
        assertFalse(state().allowAll)
        coordinator.handle(WorkbenchIntent.SaveAssistantSettings(AssistantProviderKind.OpenAI, "", "gpt-5", null, askBeforeChanges = false))
        until { state().settings?.askBeforeChanges == false }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("three"))
        until { requests.size == 3 && state().turn == null }
        assertEquals(3, ran.size)
    }

    @Test fun `Stop ends the turn where it is, and an open call is answered as stopped`() {
        editTurn()
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("Rename x"))
        until { state().approval != null }
        coordinator.handle(WorkbenchIntent.StopAssistant)
        until { state().turn == null }
        assertNull(state().approval)
        assertNull("a stop is not a failure", state().failure)
        val results = (state().messages[2] as AssistantMessage.Results).results.single()
        assertEquals(AssistantRules.STOPPED, results.output)
        assertTrue(ran.isEmpty())
    }

    @Test fun `a refusal from the provider is said on the page, and what the user wrote stays`() {
        script = { _, _ -> throw AssistantTurnException("The provider refused the API key — check it in Assistant settings") }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("Hello"))
        until { state().failure != null }
        assertEquals("The provider refused the API key — check it in Assistant settings", state().failure)
        assertEquals(listOf(AssistantMessage.User("Hello")), state().messages)
        assertNull(state().turn)
    }

    @Test fun `one turn at a time, and none without a provider`() {
        editTurn()
        // Two in the same instant: the second finds the first one's turn before its page does.
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("first"))
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("at once"))
        until { state().approval != null }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("second"))
        until { effects.any { it is WorkbenchEffect.Message && "working in app" in it.text } }
        assertEquals(1, requests.size)
        coordinator.handle(WorkbenchIntent.StopAssistant)
        until { state().turn == null }

        coordinator.handle(WorkbenchIntent.RemoveAssistantSettings)
        until { state().settings == null }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("third"))
        until { state().failure != null }
        assertTrue(state().failure!!.startsWith("Set up the assistant first"))
        assertEquals(1, requests.size)
    }

    @Test fun `a chat follows its workspace's rename and goes with its delete`() {
        script = { _, events -> events.replied(AssistantMessage.Reply("Hi.")) }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("Hello"))
        until { state().turn == null && state().messages.size == 2 }
        coordinator.workspaceMoved(app, "$app-renamed")
        assertEquals("$app-renamed", state().workspace)
        assertEquals(2, chats.of("$app-renamed").size)
        coordinator.workspaceForgotten("$app-renamed")
        assertNull(state().workspace)
        assertTrue(chats.of("$app-renamed").isEmpty())
    }

    @Test fun `settings are checked before they are saved`() {
        coordinator.handle(WorkbenchIntent.SaveAssistantSettings(AssistantProviderKind.Anthropic, "", "claude-sonnet-4-5", null, true))
        until { effects.any { it is WorkbenchEffect.Message && it.text == "Enter the API key" } }
        assertEquals(AssistantProviderKind.OpenAI, state().settings!!.kind)
        coordinator.handle(WorkbenchIntent.SaveAssistantSettings(AssistantProviderKind.Anthropic, "", "claude-sonnet-4-5", "sk-ant", true))
        until { state().settings!!.kind == AssistantProviderKind.Anthropic }
        assertEquals("sk-ant", providers.key)
    }

    private inner class FakeTool(override val name: String, override val kind: AssistantToolKind) : AssistantTool {
        override val description = name
        override val parameters = emptyList<AssistantParameter>()
        override fun summary(arguments: JSONObject) = name
        override suspend fun run(arguments: JSONObject): String {
            ran += name
            return "ok"
        }
    }

    private class FakeProviders : AssistantProviderStore {
        var saved: AssistantSettings? = AssistantSettings(AssistantProviderKind.OpenAI, null, "gpt-5", hasKey = true)
        var key: String? = "sk-test"
        override fun settings() = saved
        override fun save(kind: AssistantProviderKind, baseUrl: String?, model: String, key: String?, askBeforeChanges: Boolean) {
            if (key != null) this.key = key
            saved = AssistantSettings(kind, baseUrl, model, hasKey = this.key != null, askBeforeChanges = askBeforeChanges)
        }
        override fun clear() {
            saved = null
            key = null
        }
        override fun connection() = saved?.let { AssistantConnection(it.kind, it.baseUrl, it.model, key) }
    }
}
