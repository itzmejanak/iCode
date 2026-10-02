package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantLive
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
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
import kotlinx.coroutines.withTimeout
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
    /** The real store, over preferences in memory; a key "encrypted" as itself, marked. */
    private val providers = SharedPreferencesAssistantProviderStore(InMemoryPreferences(), { "enc:$it" }, { it.removePrefix("enc:") })
    private val openAI by lazy {
        providers.saveProvider("", AssistantProviderKind.OpenAI, "OpenAI", null, "sk-test", listOf(AssistantModelSpec("gpt-5"), AssistantModelSpec("o3")))
    }
    private val phoneFiles = mutableMapOf<String, PhoneFile>()
    private var listed: List<AssistantModel> = emptyList()
    private val chatsFolder by lazy { temp.newFolder("chats") }
    private val chats by lazy { AssistantChats(AssistantChatStore(chatsFolder)) }
    private val app by lazy { temp.newFolder("app").path }
    private var script: suspend (AssistantTurnRequest, AssistantEvents) -> Unit = { _, _ -> }
    private val requests = Collections.synchronizedList(mutableListOf<AssistantTurnRequest>())
    private val compactions = Collections.synchronizedList(mutableListOf<List<AssistantMessage>>())
    private val ran = Collections.synchronizedList(mutableListOf<String>())

    private val engine = object : AssistantEngine {
        override val catalogue = mapOf(AssistantProviderKind.OpenAI to listOf(AssistantModel("gpt-5", 400_000, known = true)))
        override fun model(kind: AssistantProviderKind, spec: AssistantModelSpec) = AssistantModel(
            spec.id, spec.contextWindow ?: 1_000, spec.maxOutput,
            efforts = if (spec.id.startsWith("o") || spec.reasoning) listOf(AssistantEffort.Low, AssistantEffort.High) else emptyList(),
            images = spec.images || spec.id.endsWith("-vision"),
        )
        override suspend fun available(connection: AssistantConnection): List<AssistantModel> {
            if (connection.apiKey != "sk-test") throw AssistantTurnException("The provider refused the API key — check it in the provider's settings")
            return listed
        }
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
            tools = { _, _ -> listOf(FakeTool("read_file", AssistantToolKind.Read), FakeTool("edit_file", AssistantToolKind.Change)) },
            activeRoot = { app },
            describe = { WorkspaceRoot("app", it, "/workspace/app") },
        ) { openText ->
            // Images pass as they are: Android's decoding is not on a JVM.
            AssistantAttachments({ phoneFiles[it] }, { bytes -> bytes }, openText, readText = { File(it).readText() })
        }
    }

    @Before fun start() {
        BackendLog.writer = { _, _, _ -> }
        scope.launch { workbench.effects.collect { effects += it } }
        openAI
        coordinator.start()
        until { state().settings.ready && state().catalogue.isNotEmpty() }
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
        until { !state().settings.askBeforeChanges }
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

    @Test fun `a reply being written shows on its turn, and the recorded reply replaces it`() {
        val shown = CompletableDeferred<Unit>()
        val go = CompletableDeferred<Unit>()
        // The page as the recorded reply left it, while the turn still runs.
        val recorded = CompletableDeferred<AssistantState>()
        script = { _, events ->
            events.writing("Check a.kt.", "It sta")
            shown.complete(Unit)
            go.await()
            events.replied(AssistantMessage.Reply("It starts the app.", reasoning = "Check a.kt."))
            recorded.complete(state())
        }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("What does a.kt do?"))
        runBlocking { withTimeout(5_000) { shown.await() } }
        assertEquals(AssistantLive("Check a.kt.", "It sta"), state().turn?.live)
        go.complete(Unit)
        val after = runBlocking { withTimeout(5_000) { recorded.await() } }
        assertNull(after.turn?.live)
        assertEquals(AssistantMessage.Reply("It starts the app.", reasoning = "Check a.kt."), after.messages.last())
        until { state().turn == null }
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

        coordinator.handle(WorkbenchIntent.RemoveAssistantProvider(openAI))
        until { !state().settings.ready }
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
            listOf(AssistantAttachment("a.kt", text = "val a = 1"), AssistantAttachment("b.kt", text = "val b = 2 // unsaved")),
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
        until { state().settings.mode == AssistantMode.Ask }
        said("Explain")
        assertEquals(listOf("read_file"), requests.single().tools.map { it.name })
        assertTrue("Ask" in requests.single().instructions || "read the workspace" in requests.single().instructions)
    }

    @Test fun `the model chosen on the page is one of a provider's, and its effort is sent only where offered`() {
        coordinator.handle(WorkbenchIntent.ChooseAssistantEffort(AssistantEffort.High))
        until { state().settings.effort == AssistantEffort.High }
        said("plain")
        assertNull("gpt-5 here offers no effort", requests.last().effort)

        coordinator.handle(WorkbenchIntent.ChooseAssistantModel(openAI, "o3"))
        until { state().settings.model?.id == "o3" }
        assertEquals(listOf(AssistantEffort.Low, AssistantEffort.High), state().settings.model?.efforts)
        said("reasoned")
        assertEquals("o3", requests.last().model.id)
        assertEquals(AssistantEffort.High, requests.last().effort)
        assertEquals("sk-test", requests.last().connection.apiKey)

        // A model the provider does not list is not chosen.
        coordinator.handle(WorkbenchIntent.ChooseAssistantModel(openAI, "gpt-9"))
        coordinator.handle(WorkbenchIntent.ChooseAssistantMode(AssistantMode.Agent))
        Thread.sleep(50)
        assertEquals("o3", state().settings.model?.id)
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

    @Test fun `a provider is checked, saved with its models and its own key, and its server asked for its models`() {
        coordinator.handle(WorkbenchIntent.SaveAssistantProvider("", AssistantProviderKind.Anthropic, "Anthropic", "", null, listOf(AssistantModelSpec("claude-sonnet-4-5"))))
        until { effects.any { it is WorkbenchEffect.Message && it.text == "Enter the API key" } }
        coordinator.handle(WorkbenchIntent.SaveAssistantProvider("", AssistantProviderKind.OpenAICompatible, "DeepSeek", "https://api.deepseek.com/v1", null, emptyList()))
        until { effects.any { it is WorkbenchEffect.Message && it.text == "Add at least one model" } }
        coordinator.handle(
            WorkbenchIntent.SaveAssistantProvider(
                "", AssistantProviderKind.OpenAICompatible, "DeepSeek", "https://api.deepseek.com/v1/", "sk-deep",
                listOf(AssistantModelSpec("deepseek-chat", 128_000, 8_000), AssistantModelSpec("deepseek-reasoner", reasoning = true)),
            ),
        )
        until { state().settings.providers.size == 2 }
        val deepSeek = state().settings.providers.last()
        assertEquals("https://api.deepseek.com/v1", deepSeek.baseUrl)
        assertEquals(listOf(AssistantModel("deepseek-chat", 128_000, 8_000), AssistantModel("deepseek-reasoner", 1_000, efforts = listOf(AssistantEffort.Low, AssistantEffort.High))), deepSeek.models)
        assertTrue(deepSeek.hasKey)
        assertEquals("the chat keeps its model", "gpt-5", state().settings.model?.id)

        coordinator.handle(WorkbenchIntent.ChooseAssistantModel(deepSeek.id, "deepseek-chat"))
        until { state().settings.provider?.id == deepSeek.id }
        said("hello")
        assertEquals(AssistantConnection(AssistantProviderKind.OpenAICompatible, "https://api.deepseek.com/v1", "sk-deep"), requests.last().connection)

        // Its server's models: with the saved key of the provider, or the one typed.
        listed = listOf(AssistantModel("gpt-5", known = true), AssistantModel("gpt-6-preview"))
        coordinator.handle(WorkbenchIntent.EditAssistantProvider(openAI))
        coordinator.handle(WorkbenchIntent.ListAssistantModels(openAI, AssistantProviderKind.OpenAI, "", null))
        until { state().available?.loading == false }
        assertEquals(listed, state().available?.models)
        coordinator.handle(WorkbenchIntent.ListAssistantModels(openAI, AssistantProviderKind.OpenAI, "", "sk-wrong"))
        until { state().available?.failure != null }
        assertEquals("The provider refused the API key — check it in the provider's settings", state().available?.failure)

        // Removing the chosen provider moves the chat to the first model left.
        coordinator.handle(WorkbenchIntent.RemoveAssistantProvider(deepSeek.id))
        until { state().settings.providers.size == 1 }
        assertEquals(AssistantChoice(openAI, "gpt-5"), state().settings.choice)
    }

    @Test fun `a photo from the phone goes to a model that sees, kept in its chat, and is refused by one that does not`() {
        phoneFiles["content://photos/1"] = PhoneFile("IMG_1.jpg", "image/jpeg") { byteArrayOf(9, 9, 9).inputStream() }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("What is this?", listOf("content://photos/1")))
        until { state().failure != null }
        assertEquals("gpt-5 does not read images — choose a model that does", state().failure)
        assertTrue("nothing was sent", requests.isEmpty())

        providers.saveProvider(openAI, AssistantProviderKind.OpenAI, "OpenAI", null, null, listOf(AssistantModelSpec("gpt-5"), AssistantModelSpec("gpt-5-vision")))
        coordinator.handle(WorkbenchIntent.ChooseAssistantModel(openAI, "gpt-5-vision"))
        until { state().settings.model?.id == "gpt-5-vision" }
        said("ok")
        script = { _, events -> events.replied(AssistantMessage.Reply("A cat.")) }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("What is this?", listOf("content://photos/1")))
        until { requests.size == 2 && state().turn == null }
        val photo = requests.last().input.attachments.single()
        assertEquals("IMG_1.jpg", photo.name)
        assertEquals(AssistantAttachmentKind.Image, photo.kind)
        assertEquals(listOf<Byte>(9, 9, 9), File(photo.file!!).readBytes().toList())
        assertTrue("kept in the chat's own folder", File(photo.file!!).parentFile!!.name == "${state().session}.files")

        // A grant gone with the process: said, nothing sent.
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("Again", listOf("content://photos/gone")))
        until { state().failure != null }
        assertEquals("A file from the phone can no longer be read — attach it again", state().failure)
    }

    @Test fun `a photo alone is a message and names its chat, while nothing at all is not sent`() {
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("  "))
        phoneFiles["content://photos/2"] = PhoneFile("IMG_2.jpg", "image/jpeg") { byteArrayOf(7).inputStream() }
        providers.saveProvider(openAI, AssistantProviderKind.OpenAI, "OpenAI", null, null, listOf(AssistantModelSpec("gpt-5-vision")))
        coordinator.handle(WorkbenchIntent.ChooseAssistantModel(openAI, "gpt-5-vision"))
        until { state().settings.model?.id == "gpt-5-vision" }
        script = { _, events -> events.replied(AssistantMessage.Reply("A receipt.")) }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("", listOf("content://photos/2")))
        until { requests.size == 1 && state().turn == null }
        assertEquals("", requests.single().input.text)
        assertEquals("IMG_2.jpg", requests.single().input.attachments.single().name)
        assertEquals("IMG_2.jpg", state().sessions.single().title)
    }

    @Test fun `Try Again sends a message that failed before any reply once more, not twice`() {
        script = { _, _ -> throw AssistantTurnException("The provider is limiting requests — try again shortly") }
        coordinator.handle(WorkbenchIntent.SendAssistantMessage("Hello"))
        until { state().failure != null }
        script = { _, events -> events.replied(AssistantMessage.Reply("Hi.")) }
        coordinator.handle(WorkbenchIntent.RetryAssistantMessage)
        until { requests.size == 2 && state().turn == null }
        assertNull(state().failure)
        assertEquals(listOf(AssistantMessage.User("Hello"), AssistantMessage.Reply("Hi.")), state().messages)
        assertEquals(AssistantMessage.User("Hello"), requests.last().input)
        coordinator.handle(WorkbenchIntent.RetryAssistantMessage)
        until { effects.any { it is WorkbenchEffect.Message && it.text == "Nothing to send again" } }
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

}
