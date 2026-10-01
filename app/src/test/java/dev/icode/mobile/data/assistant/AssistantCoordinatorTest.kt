package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSettings
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
import java.io.File
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private val chatsFolder by lazy { temp.newFolder("chats") }
    private val chats by lazy { AssistantChats(AssistantChatStore(chatsFolder)) }
    private val app by lazy { temp.newFolder("app").path }
    private var script: suspend (AssistantTurnRequest, AssistantEvents) -> Unit = { _, _ -> }
    private val requests = Collections.synchronizedList(mutableListOf<AssistantTurnRequest>())
    private val compactions = Collections.synchronizedList(mutableListOf<List<AssistantMessage>>())
    private val ran = Collections.synchronizedList(mutableListOf<String>())

    private val engine = object : AssistantEngine {
        override val catalogue = mapOf(AssistantProviderKind.OpenAI to listOf(AssistantModel("gpt-5", 400_000)))
        override fun model(kind: AssistantProviderKind, id: String, contextWindow: Long?, maxOutput: Long?) = AssistantModel(
            id, contextWindow ?: 1_000, maxOutput,
            efforts = if (id.startsWith("o")) listOf(AssistantEffort.Low, AssistantEffort.High) else emptyList(),
        )
        override suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents) {
            requests += turn
            script(turn, events)
        }
        override suspend fun compact(connection: AssistantConnection, model: AssistantModel, instructions: String, history: List<AssistantMessage>): String {
            compactions += history
            return "Summary of ${history.size}"
        }
    }

    private val coordinator by lazy {
        AssistantCoordinator(
            workbench, scope, engine, providers, chats,
            tools = { listOf(FakeTool("read_file", AssistantToolKind.Read), FakeTool("edit_file", AssistantToolKind.Change)) },
            activeRoot = { app },
            describe = { WorkspaceRoot("app", it, "/workspace/app") },
            readText = { File(it).readText() },
        )
    }

    @Before fun start() {
        BackendLog.writer = { _, _, _ -> }
        scope.launch { workbench.effects.collect { effects += it } }
        coordinator.start()
        until { state().settings != null && state().catalogue.isNotEmpty() }
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

    private fun said(text: String) {
        val before = requests.size
        script = { _, events -> events.replied(AssistantMessage.Reply("Re: $text")) }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage(text))
        until { requests.size == before + 1 && state().turn == null }
    }

    private val edit = AssistantCall("c1", "edit_file", """{"path":"a.kt"}""", "Edit a.kt", AssistantToolKind.Change)

    /** A turn as the engine runs one: a reply calling [edit], its result as Koog's runner gives it, then "Done." */
    private fun editTurn() {
        script = { turn, events ->
            events.used(700)
            events.replied(AssistantMessage.Reply("Let me edit.", listOf(edit)))
            events.calling(edit)
            val tool = turn.tools.single { it.name == "edit_file" }
            // As Koog's runner does: a refusal is the result, a cancellation is not.
            val output = try {
                tool.run(JSONObject(edit.arguments)) to false
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (refused: Exception) {
                refused.message!! to true
            }
            events.answered(AssistantResult("c1", "edit_file", output.first, output.second))
            events.replied(AssistantMessage.Reply("Done."))
        }
    }

    @Test fun `a change waits for Allow, runs, and the chat is kept and listed`() {
        editTurn()
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("  Rename x  "))
        until { state().approval != null }
        assertEquals("Edit a.kt", state().approval!!.call.summary)
        assertEquals("Waiting for your answer", state().turn!!.step)
        assertTrue(ran.isEmpty())
        coordinator.handle(WorkbenchIntent.AnswerAssistant("c1", AssistantAnswer.Allow))
        until { state().turn == null }
        assertEquals(listOf("edit_file"), ran)
        assertEquals(AssistantMessage.User("Rename x"), requests.single().input)
        assertEquals(4, state().messages.size)
        assertEquals("Done.", (state().messages.last() as AssistantMessage.Reply).text)
        assertEquals(700L, state().contextTokens)
        val session = state().session!!
        assertEquals(listOf("Rename x"), state().sessions.map { it.title })
        assertEquals(AssistantChat(state().messages, 700), AssistantChatStore(chatsFolder).load(app, session))
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

    @Test fun `Allow All answers this chat's next changes, a new chat asks again, and the chat page's permissions answer all`() {
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
        until { state().session == null && state().messages.isEmpty() }
        assertFalse(state().allowAll)
        coordinator.handle(WorkbenchIntent.SetAssistantAskFirst(false))
        until { state().settings?.askBeforeChanges == false }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("three"))
        until { requests.size == 3 && state().turn == null }
        assertEquals(3, ran.size)
        assertEquals(2, state().sessions.size)
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
        coordinator.handle(WorkbenchIntent.NewAssistantChat)
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("third"))
        until { state().failure != null }
        assertTrue(state().failure!!.startsWith("Set up the assistant first"))
        assertEquals(1, requests.size)
        assertEquals("nothing was said in a chat that never started", 1, state().sessions.size)
    }

    @Test fun `each chat is its own, opened from the history and deleted from it, and the page reopens on the one shown`() {
        said("first chat")
        val first = state().session!!
        coordinator.handle(WorkbenchIntent.NewAssistantChat)
        until { state().session == null }
        said("second chat")
        val second = state().session!!
        assertEquals(listOf(second, first), state().sessions.map { it.id })
        // The second turn sent nothing of the first chat.
        assertEquals(emptyList<AssistantMessage>(), requests.last().history)

        coordinator.handle(WorkbenchIntent.OpenAssistantSession(first))
        until { state().session == first }
        assertEquals("Re: first chat", (state().messages.last() as AssistantMessage.Reply).text)
        coordinator.handle(WorkbenchIntent.OpenAssistant)
        Thread.sleep(50)
        assertEquals(first, state().session)

        coordinator.handle(WorkbenchIntent.DeleteAssistantSession(first))
        until { state().sessions.size == 1 }
        assertNull(state().session)
        assertEquals(second, state().sessions.single().id)
    }

    @Test fun `a chat in use is not deleted`() {
        editTurn()
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("Rename x"))
        until { state().approval != null }
        coordinator.handle(WorkbenchIntent.DeleteAssistantSession(state().session!!))
        until { effects.any { it is WorkbenchEffect.Message && it.text == "Stop the assistant before deleting this chat" } }
        coordinator.handle(WorkbenchIntent.StopAssistant)
        until { state().turn == null }
        assertEquals(1, state().sessions.size)
    }

    @Test fun `attached files go with the message — an open tab's text as it is — and none from outside`() {
        File(app, "a.kt").writeText("val a = 1")
        File(app, "b.kt").writeText("val b = 1 // saved")
        val b = File(app, "b.kt").canonicalPath
        workbench.update { it.copy(openDocuments = listOf(OpenDocument("b.kt", b, "val b = 2 // unsaved", TextEncoding.Utf8, LineEnding.Lf, dirty = true, readOnly = false))) }
        said("Compare")
        script = { _, events -> events.replied(AssistantMessage.Reply("ok")) }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("Compare", listOf(File(app, "a.kt").path, b)))
        until { requests.size == 2 && state().turn == null }
        assertEquals(
            listOf(dev.icode.mobile.domain.AssistantAttachment("a.kt", "val a = 1"), dev.icode.mobile.domain.AssistantAttachment("b.kt", "val b = 2 // unsaved")),
            requests.last().input.attachments,
        )

        val outside = temp.newFile("secret.txt").apply { writeText("no") }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("Leak", listOf(outside.path)))
        until { state().failure != null }
        assertEquals("secret.txt is not in this workspace", state().failure)
        assertEquals(2, requests.size)
    }

    @Test fun `Ask offers only the tools that read`() {
        coordinator.handle(WorkbenchIntent.ChooseAssistantMode(AssistantMode.Ask))
        until { state().settings?.mode == AssistantMode.Ask }
        said("Explain")
        assertEquals(listOf("read_file"), requests.single().tools.map { it.name })
        assertTrue("Ask" in requests.single().instructions || "read the workspace" in requests.single().instructions)
    }

    @Test fun `the model chosen on the page is used, kept first among the recent ones, and its effort sent only where offered`() {
        coordinator.handle(WorkbenchIntent.ChooseAssistantEffort(AssistantEffort.High))
        until { state().settings?.effort == AssistantEffort.High }
        said("plain")
        assertNull("gpt-5 here offers no effort", requests.last().effort)

        coordinator.handle(WorkbenchIntent.ChooseAssistantModel("o3"))
        until { state().settings?.model == "o3" }
        assertEquals(listOf("o3", "gpt-5"), state().recentModels)
        assertEquals(listOf(AssistantEffort.Low, AssistantEffort.High), state().model?.efforts)
        said("reasoned")
        assertEquals("o3", requests.last().model.id)
        assertEquals(AssistantEffort.High, requests.last().effort)
    }

    @Test fun `Compact summarizes the chat, and later turns send the summary and what came after`() {
        said("one")
        said("two")
        coordinator.handle(WorkbenchIntent.CompactAssistantChat)
        until { state().messages.lastOrNull() is AssistantMessage.Compacted && state().turn == null }
        assertEquals(4, compactions.single().size)
        assertNull("the meter starts again", state().contextTokens)
        said("three")
        assertEquals(emptyList<AssistantMessage>(), requests.last().history)
        assertTrue("Summary of 4" in requests.last().instructions)
        // The page still shows everything.
        assertEquals(7, state().messages.size)
    }

    @Test fun `a chat near its model's window is compacted before the next message is sent`() {
        script = { _, events ->
            events.used(850)
            events.replied(AssistantMessage.Reply("long"))
        }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("first"))
        until { requests.size == 1 && state().turn == null }
        assertEquals(850L, state().contextTokens)
        said("second")
        assertEquals(listOf(AssistantMessage.User("first"), AssistantMessage.Reply("long")), compactions.single())
        assertEquals(emptyList<AssistantMessage>(), requests.last().history)
        // The marker comes before the message it was made for.
        val kinds = state().messages.map { it.javaClass.simpleName }
        assertEquals(listOf("User", "Reply", "Compacted", "User", "Reply"), kinds)
    }

    @Test fun `a workspace's rename stops its turn first, then its chats follow, and its delete takes them`() = runBlocking {
        val asked = CompletableDeferred<Unit>()
        script = { _, events ->
            events.replied(AssistantMessage.Reply("Working"))
            asked.complete(Unit)
            awaitCancellation()
        }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("Hello"))
        asked.await()
        val session = state().session!!
        coordinator.workspaceMoved(app, "$app-renamed")
        assertNull(state().turn)
        assertEquals("$app-renamed", state().workspace)
        assertEquals(session, state().session)
        assertEquals(2, chats.of("$app-renamed", session).messages.size)
        assertTrue(chats.sessions(app).isEmpty())
        coordinator.workspaceForgotten("$app-renamed")
        assertNull(state().workspace)
        assertTrue(chats.sessions("$app-renamed").isEmpty())
    }

    @Test fun `settings are checked before they are saved, limits with them`() {
        coordinator.handle(WorkbenchIntent.SaveAssistantSettings(AssistantProviderKind.Anthropic, "", "claude-sonnet-4-5", null))
        until { effects.any { it is WorkbenchEffect.Message && it.text == "Enter the API key" } }
        assertEquals(AssistantProviderKind.OpenAI, state().settings!!.kind)
        coordinator.handle(WorkbenchIntent.SaveAssistantSettings(AssistantProviderKind.OpenAICompatible, "https://x/v1", "local", null, contextWindow = "lots"))
        until { effects.any { it is WorkbenchEffect.Message && it.text == "The context window is a number of tokens" } }
        coordinator.handle(WorkbenchIntent.SaveAssistantSettings(AssistantProviderKind.OpenAICompatible, "https://x/v1", "local", null, contextWindow = "32k", maxOutput = "4k"))
        until { state().settings!!.kind == AssistantProviderKind.OpenAICompatible }
        assertEquals(AssistantModel("local", 32_000, 4_000), state().model)
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
        val recent = mutableListOf("gpt-5")
        override fun settings() = saved
        override fun save(kind: AssistantProviderKind, baseUrl: String?, model: String, key: String?, contextWindow: Long?, maxOutput: Long?) {
            if (key != null) this.key = key
            saved = (saved ?: AssistantSettings(kind, baseUrl, model, false))
                .copy(kind = kind, baseUrl = baseUrl, model = model, hasKey = this.key != null, contextWindow = contextWindow, maxOutput = maxOutput)
        }
        override fun chooseModel(model: String) {
            saved = saved?.copy(model = model)
            recent.remove(model)
            recent.add(0, model)
        }
        override fun chooseEffort(effort: AssistantEffort?) {
            saved = saved?.copy(effort = effort)
        }
        override fun chooseMode(mode: AssistantMode) {
            saved = saved?.copy(mode = mode)
        }
        override fun setAskFirst(ask: Boolean) {
            saved = saved?.copy(askBeforeChanges = ask)
        }
        override fun recentModels() = recent.toList()
        override fun clear() {
            saved = null
            key = null
        }
        override fun connection() = saved?.let { AssistantConnection(it.kind, it.baseUrl, it.model, key) }
    }
}
