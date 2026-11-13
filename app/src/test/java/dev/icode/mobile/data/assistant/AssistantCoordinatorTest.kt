package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.runtime.TerminalRuns
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantCommitMessage
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantExports
import dev.icode.mobile.domain.AssistantLive
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantPermission
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantReach
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantTurnEnd
import dev.icode.mobile.domain.LineChanges
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.Said
import dev.icode.mobile.domain.said
import dev.icode.mobile.domain.notices.NoticeTone
import java.io.File
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
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
    private val stateHolder = ICodeStateHolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Read while the coordinator still adds to it: iterated as a snapshot, never mid-change.
    private val effects = CopyOnWriteArrayList<ICodeEffect>()
    /** The real store, over preferences in memory; a key "encrypted" as itself, marked. */
    private val providers = SharedPreferencesAssistantProviderStore(InMemoryPreferences(), { "enc:$it" }, { it.removePrefix("enc:") })
    private val openAI by lazy {
        providers.saveProvider("", AssistantProviderKind.OpenAI, "OpenAI", null, "sk-test", listOf(AssistantModelSpec("gpt-5"), AssistantModelSpec("o3")))
    }
    private val phoneFiles = mutableMapOf<String, PhoneFile>()
    private var listed: List<AssistantModel> = emptyList()
    /** No workspace is open: the home screen. */
    private var home = false
    /** The name of each place a turn's tools were made for. */
    private val places = Collections.synchronizedList(mutableListOf<String>())
    private val chatsFolder by lazy { temp.newFolder("chats") }
    private val chats by lazy { AssistantChats(AssistantChatStore(chatsFolder)) }
    private val runtime by lazy { TestRuntime(temp.newFolder("runtime")) }
    private val app by lazy { runtime.root.path }
    private var script: suspend (AssistantTurnRequest, AssistantEvents) -> Unit = { _, _ -> }
    private val requests = Collections.synchronizedList(mutableListOf<AssistantTurnRequest>())
    private val compactions = Collections.synchronizedList(mutableListOf<List<AssistantMessage>>())
    private val ran = Collections.synchronizedList(mutableListOf<String>())
    /** Each one-shot request: the model asked, and what it was asked. */
    private val answers = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
    private var answer: suspend () -> String = { "feat: add login" }
    private var staged = "3\t1\tsrc/a.kt\n"
    private var log = CliGitService.CommandResult(0, "feat: first\nfix: second\n", "")

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
        override suspend fun answer(connection: AssistantConnection, model: AssistantModel, instructions: String, request: String): String {
            answers += model.id to request
            return answer()
        }
    }

    private val coordinator by lazy {
        AssistantCoordinator(
            stateHolder, scope, engine, providers, chats,
            tools = { workspace, _, _ ->
                places += workspace.name
                listOf(FakeTool("read_file", AssistantToolKind.Read, workspace), FakeTool("edit_file", AssistantToolKind.Change, workspace))
            },
            activeRoot = { app.takeUnless { home } },
            describe = { WorkspaceRoot("app", it, "/workspace/app") },
            paths = runtime.paths,
            // Images pass as they are: Android's decoding is not on a JVM.
            attachments = { openText -> AssistantAttachments({ phoneFiles[it] }, { bytes -> bytes }, openText, readText = { File(it).readText() }) },
            git = { _, arguments ->
                when {
                    "--numstat" in arguments -> CliGitService.CommandResult(0, staged, "")
                    arguments.first() == "diff" -> CliGitService.CommandResult(0, "diff --git a/src/a.kt b/src/a.kt\n+fun a() = 1\n", "")
                    else -> log
                }
            },
            storageAccess = { true },
            forget = { workspace, chat -> forgotten += workspace to chat },
            openTab = { workspace, chat ->
                opened += workspace to chat
                tabRefusal?.let { throw TerminalRuns.Refused(it) } ?: "session-1"
            },
        )
    }
    private val forgotten = CopyOnWriteArrayList<Pair<String?, String>>()
    private val opened = CopyOnWriteArrayList<Pair<String?, String>>()
    private var tabRefusal: String? = null

    @Before fun start() {
        BackendLog.writer = { _, _, _ -> }
        scope.launch { stateHolder.effects.collect { effects += it } }
        openAI
        coordinator.start()
        until { state().settings.ready && state().catalogue.isNotEmpty() }
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = true))
        until { state().workspace == app }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun state(): AssistantState = stateHolder.current.assistant

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
        coordinator.handle(ICodeIntent.SendAssistantMessage(text))
        until { requests.size == before + 1 && state().turn == null }
    }

    private val edit = AssistantCall("c1", "edit_file", """{"path":"a.kt"}""", "Edit a.kt", AssistantToolKind.Change)

    /** A turn as the engine runs one: a reply calling [edit], its result as Koog's runner gives it, then "Done." */
    private fun editTurn() = callTurn(edit)

    /** A turn reading [path], a runtime path. */
    private fun readTurn(path: String) = callTurn(AssistantCall("c1", "read_file", JSONObject().put("path", path).toString(), "Read $path", AssistantToolKind.Read))

    private fun callTurn(call: AssistantCall) {
        script = { turn, events ->
            events.used(700)
            events.replied(AssistantMessage.Reply("Let me look.", listOf(call)))
            events.calling(call)
            val tool = turn.tools.single { it.name == call.tool }
            // As Koog's runner does: a refusal is the result, a cancellation is not.
            val output = try {
                tool.run(JSONObject(call.arguments)) to false
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (refused: Exception) {
                refused.message!! to true
            }
            events.answered(AssistantResult(call.id, call.tool, output.first, output.second))
            events.replied(AssistantMessage.Reply("Done."))
        }
    }

    @Test fun `away from the workspace, the page and the bubble stay in the chat the user was in`() {
        said("one")
        val chat = state().session
        home = true
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = true))
        said("two")
        assertEquals(app, state().workspace)
        assertEquals(chat, state().session)
        assertEquals(listOf("app", "app"), places)
        assertEquals(1, chats.sessions(app).size)
    }

    @Test fun `outside the workspace the page shows the chats in none, and one sent there works from home, apart from the workspace's`() {
        said("in app")
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = false))
        until { state().opened && state().workspace == null }
        assertTrue(state().sessions.isEmpty())
        said("from home")
        assertNull(state().workspace)
        assertEquals(listOf("from home"), chats.sessions(null).map { it.title })
        assertEquals(listOf("in app"), chats.sessions(app).map { it.title })
        assertEquals("~", places.last())
        assertTrue(requests.last().instructions, requests.last().instructions.contains("You work in no workspace, from /root, root's home,"))
        assertEquals(AssistantTurnEnd(null, 2, changedFiles = false), state().ended)
        // Inside the workspace again, its own chats.
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = true))
        until { state().workspace == app }
        assertEquals(listOf("in app"), state().sessions.map { it.title })
    }

    @Test fun `in no workspace a change asks under Auto in workspace too, with nothing to allow it in, and runs once allowed`() {
        coordinator.handle(ICodeIntent.SetAssistantPermission(AssistantPermission.AutoInWorkspace))
        until { state().settings.permission == AssistantPermission.AutoInWorkspace }
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = false))
        until { state().opened && state().workspace == null }
        editTurn()
        coordinator.handle(ICodeIntent.SendAssistantMessage("Rename x"))
        until { state().approval != null }
        assertNull(state().approval!!.reach)
        assertNull(AssistantPermissions.allowIn(state().approval!!, null))
        assertTrue(ran.isEmpty())
        coordinator.handle(ICodeIntent.AnswerAssistant("c1", AssistantAnswer.Allow))
        until { state().turn == null }
        assertEquals(listOf("edit_file"), ran)
    }

    @Test fun `in no workspace Full access asks nothing`() {
        coordinator.handle(ICodeIntent.SetAssistantPermission(AssistantPermission.FullAccess))
        until { state().settings.permission == AssistantPermission.FullAccess }
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = false))
        until { state().opened && state().workspace == null }
        editTurn()
        coordinator.handle(ICodeIntent.SendAssistantMessage("Rename x"))
        until { requests.size == 1 && state().turn == null }
        assertEquals(listOf("edit_file"), ran)
    }

    @Test fun `a new chat away from the workspace starts in that same workspace`() {
        said("one")
        home = true
        coordinator.handle(ICodeIntent.NewAssistantChat)
        until { state().session == null }
        said("two")
        assertEquals(app, state().workspace)
        assertEquals(2, chats.sessions(app).size)
    }

    @Test fun `at the step limit the turn asks to keep going, and Continue goes on from the chat with no new message`() {
        script = { turn, events ->
            if (turn.input != null) {
                events.replied(AssistantMessage.Reply("Working…"))
                throw AssistantStepLimitException()
            }
            events.replied(AssistantMessage.Reply("Done."))
        }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Do the long thing"))
        until { state().pause != null }
        assertEquals(AssistantPause.Kind.StepLimit, state().pause!!.kind)
        assertEquals("Waiting for your answer", state().turn!!.step)
        coordinator.handle(ICodeIntent.ContinueAssistant)
        until { state().turn == null }
        assertEquals(2, requests.size)
        assertNull(requests[1].input)
        assertEquals(listOf(AssistantMessage.User("Do the long thing"), AssistantMessage.Reply("Working…")), requests[1].history)
        assertEquals(AssistantMessage.Reply("Done."), state().messages.last())
        assertNull(state().pause)
        assertNull(state().failure)
    }

    @Test fun `a message sent by voice or from the bubble marks its turn to be answered aloud, through its pauses`() {
        script = { turn, events ->
            if (turn.input != null) throw AssistantStepLimitException()
            events.replied(AssistantMessage.Reply("Done."))
        }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Turn on Wi-Fi", aloud = true))
        until { state().pause != null }
        assertTrue(state().turn!!.aloud)
        coordinator.handle(ICodeIntent.ContinueAssistant)
        until { state().turn == null }
        coordinator.handle(ICodeIntent.SendAssistantMessage("And Bluetooth"))
        until { state().pause != null }
        assertFalse(state().turn!!.aloud)
        coordinator.handle(ICodeIntent.StopAssistant)
        until { state().turn == null }
    }

    @Test fun `Stop at the step limit ends the turn, and what was done stays`() {
        script = { _, events ->
            events.replied(AssistantMessage.Reply("Working…"))
            throw AssistantStepLimitException()
        }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Do the long thing"))
        until { state().pause != null }
        coordinator.handle(ICodeIntent.StopAssistant)
        until { state().turn == null }
        assertNull(state().pause)
        assertEquals(1, requests.size)
        assertEquals(AssistantMessage.Reply("Working…"), state().messages.last())
    }

    @Test fun `a change waits for Allow, runs, and the chat is kept and listed`() {
        editTurn()
        coordinator.handle(ICodeIntent.SendAssistantMessage("  Rename x  "))
        until { state().approval != null }
        assertEquals("Edit a.kt", state().approval!!.call.summary)
        assertEquals("Waiting for your answer", state().turn!!.step)
        assertTrue(ran.isEmpty())
        coordinator.handle(ICodeIntent.AnswerAssistant("c1", AssistantAnswer.Allow))
        until { state().turn == null }
        assertEquals(listOf("edit_file"), ran)
        assertEquals(AssistantMessage.User("Rename x"), requests.single().input)
        assertEquals(4, state().messages.size)
        // The change's result says by how many lines it changed the file.
        assertEquals(LineChanges(2, 1), (state().messages[2] as AssistantMessage.Results).results.single().changes)
        assertEquals("Done.", (state().messages.last() as AssistantMessage.Reply).text)
        assertEquals(700L, state().contextTokens)
        val session = state().session!!
        assertEquals(listOf("Rename x"), state().sessions.map { it.title })
        assertEquals(AssistantChat(state().messages, 700), AssistantChatStore(chatsFolder).load(app, session))
        assertNull(state().failure)
        // Its end says it changed a file: the dock suggests Source Control (docs/dock-navigation-plan.md D8).
        assertEquals(AssistantTurnEnd(app, 1, changedFiles = true), state().ended)
    }

    @Test fun `Deny tells the model the change was refused, and nothing runs`() {
        editTurn()
        coordinator.handle(ICodeIntent.SendAssistantMessage("Rename x"))
        until { state().approval != null }
        coordinator.handle(ICodeIntent.AnswerAssistant("c1", AssistantAnswer.Deny))
        until { state().turn == null }
        assertTrue(ran.isEmpty())
        val result = (state().messages[2] as AssistantMessage.Results).results.single()
        assertTrue(result.failed)
        assertEquals("The user did not allow this change", result.output)
        assertEquals(AssistantTurnEnd(app, 1, changedFiles = false), state().ended)
    }

    @Test fun `Allow in the workspace answers this chat's next changes, a new chat asks again, and the mode chosen answers all`() {
        editTurn()
        coordinator.handle(ICodeIntent.SendAssistantMessage("one"))
        until { state().approval != null }
        assertNull(state().approval!!.reach)
        coordinator.handle(ICodeIntent.AnswerAssistant("c1", AssistantAnswer.AllowInChat))
        until { state().turn == null }
        assertTrue(state().allowedInWorkspace)
        assertEquals(AssistantPermission.AutoInWorkspace, state().permission)
        coordinator.handle(ICodeIntent.SendAssistantMessage("two"))
        until { requests.size == 2 && state().turn == null }
        assertEquals(listOf("edit_file", "edit_file"), ran)

        coordinator.handle(ICodeIntent.NewAssistantChat)
        until { state().session == null && state().messages.isEmpty() }
        assertFalse(state().allowedInWorkspace)
        coordinator.handle(ICodeIntent.SetAssistantPermission(AssistantPermission.AutoInWorkspace))
        until { state().settings.permission == AssistantPermission.AutoInWorkspace }
        coordinator.handle(ICodeIntent.SendAssistantMessage("three"))
        until { requests.size == 3 && state().turn == null }
        assertEquals(3, ran.size)
        assertEquals(2, state().sessions.size)
    }

    @Test fun `a mode chosen for the chat replaces its own answer`() {
        editTurn()
        coordinator.handle(ICodeIntent.SendAssistantMessage("one"))
        until { state().approval != null }
        coordinator.handle(ICodeIntent.AnswerAssistant("c1", AssistantAnswer.AllowInChat))
        until { state().turn == null && state().allowedInWorkspace }
        coordinator.handle(ICodeIntent.SetAssistantPermission(AssistantPermission.AskFirst))
        until { !state().allowedInWorkspace }
        coordinator.handle(ICodeIntent.SendAssistantMessage("two"))
        until { state().approval != null }
        assertEquals(listOf("edit_file"), ran)
    }

    @Test fun `a read outside the workspace asks, with what it reaches and the folder Allow in Chat opens`() {
        readTurn("/etc/hosts")
        coordinator.handle(ICodeIntent.SendAssistantMessage("hosts?"))
        until { state().approval != null }
        assertEquals(AssistantReach(listOf("/etc/hosts"), listOf("/etc")), state().approval!!.reach)
        assertTrue(ran.isEmpty())
        coordinator.handle(ICodeIntent.AnswerAssistant("c1", AssistantAnswer.Deny))
        until { state().turn == null }
        val result = (state().messages[2] as AssistantMessage.Results).results.single()
        assertEquals("The user did not allow reaching /etc/hosts", result.output)
        assertTrue(ran.isEmpty())
    }

    @Test fun `Allow in Chat opens that folder for the chat, and a reach elsewhere asks again`() {
        readTurn("/etc/hosts")
        coordinator.handle(ICodeIntent.SendAssistantMessage("hosts?"))
        until { state().approval != null }
        coordinator.handle(ICodeIntent.AnswerAssistant("c1", AssistantAnswer.AllowInChat))
        until { state().turn == null }
        readTurn("/etc/passwd")
        coordinator.handle(ICodeIntent.SendAssistantMessage("passwd?"))
        until { requests.size == 2 && state().turn == null }
        assertEquals(listOf("read_file", "read_file"), ran)
        readTurn("~/notes.txt")
        coordinator.handle(ICodeIntent.SendAssistantMessage("notes?"))
        until { state().approval != null }
        assertEquals(listOf("/root"), state().approval!!.reach!!.roots)
    }

    @Test fun `a reach outside asks in Auto in workspace too, and in Full access nothing asks`() {
        coordinator.handle(ICodeIntent.SetAssistantPermission(AssistantPermission.AutoInWorkspace))
        until { state().settings.permission == AssistantPermission.AutoInWorkspace }
        readTurn("/etc/hosts")
        coordinator.handle(ICodeIntent.SendAssistantMessage("hosts?"))
        until { state().approval != null }
        coordinator.handle(ICodeIntent.AnswerAssistant("c1", AssistantAnswer.Allow))
        until { state().turn == null }
        coordinator.handle(ICodeIntent.SetAssistantPermission(AssistantPermission.FullAccess))
        until { state().settings.permission == AssistantPermission.FullAccess }
        coordinator.handle(ICodeIntent.SendAssistantMessage("again"))
        until { requests.size == 2 && state().turn == null }
        editTurn()
        coordinator.handle(ICodeIntent.SendAssistantMessage("edit"))
        until { requests.size == 3 && state().turn == null }
        assertEquals(listOf("read_file", "read_file", "edit_file"), ran)
    }

    @Test fun `Stop ends the turn where it is, and an open call is answered as stopped`() {
        editTurn()
        coordinator.handle(ICodeIntent.SendAssistantMessage("Rename x"))
        until { state().approval != null }
        coordinator.handle(ICodeIntent.StopAssistant)
        until { state().turn == null }
        assertNull(state().approval)
        assertNull("a stop is not a failure", state().failure)
        val results = (state().messages[2] as AssistantMessage.Results).results.single()
        assertEquals(AssistantRules.STOPPED, results.output)
        assertTrue(ran.isEmpty())
    }

    @Test fun `a refusal from the provider is said on the page, and what the user wrote stays`() {
        script = { _, _ -> throw AssistantTurnException("The provider refused the API key — check it in Assistant settings") }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Hello"))
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
        coordinator.handle(ICodeIntent.SendAssistantMessage("What does a.kt do?"))
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
        coordinator.handle(ICodeIntent.SendAssistantMessage("first"))
        coordinator.handle(ICodeIntent.SendAssistantMessage("at once"))
        until { state().approval != null }
        coordinator.handle(ICodeIntent.SendAssistantMessage("second"))
        until { effects.any { it.said() == Said(NoticeTone.HeadsUp, "Wait for the assistant to finish", "It is working in app. Or stop it there.") } }
        assertEquals(1, requests.size)
        coordinator.handle(ICodeIntent.StopAssistant)
        until { state().turn == null }

        coordinator.handle(ICodeIntent.RemoveAssistantProvider(openAI))
        until { !state().settings.ready }
        coordinator.handle(ICodeIntent.NewAssistantChat)
        coordinator.handle(ICodeIntent.SendAssistantMessage("third"))
        until { state().failure != null }
        assertTrue(state().failure!!.startsWith("Set up the assistant first"))
        assertEquals(1, requests.size)
        assertEquals("nothing was said in a chat that never started", 1, state().sessions.size)
    }

    @Test fun `each chat is its own, opened from the history and deleted from it, and the page reopens on the one shown`() {
        said("first chat")
        val first = state().session!!
        coordinator.handle(ICodeIntent.NewAssistantChat)
        until { state().session == null }
        said("second chat")
        val second = state().session!!
        assertEquals(listOf(second, first), state().sessions.map { it.id })
        // The second turn sent nothing of the first chat.
        assertEquals(emptyList<AssistantMessage>(), requests.last().history)

        coordinator.handle(ICodeIntent.OpenAssistantSession(first))
        until { state().session == first }
        assertEquals("Re: first chat", (state().messages.last() as AssistantMessage.Reply).text)
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = true))
        Thread.sleep(50)
        assertEquals(first, state().session)

        coordinator.handle(ICodeIntent.DeleteAssistantSession(first))
        until { state().sessions.size == 1 }
        assertNull(state().session)
        assertEquals(second, state().sessions.single().id)
    }

    @Test fun `a chat in use is not deleted`() {
        editTurn()
        coordinator.handle(ICodeIntent.SendAssistantMessage("Rename x"))
        until { state().approval != null }
        coordinator.handle(ICodeIntent.DeleteAssistantSession(state().session!!))
        until { effects.any { it.said()?.title == "Stop the assistant first" } }
        coordinator.handle(ICodeIntent.StopAssistant)
        until { state().turn == null }
        assertEquals(1, state().sessions.size)
    }

    @Test fun `attached files go with the message — an open tab's text as it is — and none from outside`() {
        File(app, "a.kt").writeText("val a = 1")
        File(app, "b.kt").writeText("val b = 1 // saved")
        val b = File(app, "b.kt").canonicalPath
        stateHolder.update { it.copy(openDocuments = listOf(OpenDocument("b.kt", b, "val b = 2 // unsaved", TextEncoding.Utf8, LineEnding.Lf, dirty = true, readOnly = false))) }
        said("Compare")
        script = { _, events -> events.replied(AssistantMessage.Reply("ok")) }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Compare", listOf(File(app, "a.kt").path, b)))
        until { requests.size == 2 && state().turn == null }
        assertEquals(
            listOf(AssistantAttachment("a.kt", text = "val a = 1", inWorkspace = true), AssistantAttachment("b.kt", text = "val b = 2 // unsaved", inWorkspace = true)),
            requests.last().input!!.attachments,
        )

        val outside = temp.newFile("secret.txt").apply { writeText("no") }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Leak", listOf(outside.path)))
        until { state().failure != null }
        assertEquals("secret.txt is not in this workspace", state().failure)
        assertEquals(2, requests.size)
    }

    @Test fun `Ask offers only the tools that read`() {
        coordinator.handle(ICodeIntent.ChooseAssistantMode(AssistantMode.Ask))
        until { state().settings.mode == AssistantMode.Ask }
        said("Explain")
        assertEquals(listOf("read_file"), requests.single().tools.map { it.name })
        assertTrue("You CANNOT change files or run commands" in requests.single().instructions)
    }

    @Test fun `Plan offers only the tools that read, and plans rather than changes`() {
        coordinator.handle(ICodeIntent.ChooseAssistantMode(AssistantMode.Plan))
        until { state().settings.mode == AssistantMode.Plan }
        said("How should authentication work?")
        assertEquals(listOf("read_file"), requests.single().tools.map { it.name })
        assertTrue("you never implement" in requests.single().instructions)
    }

    @Test fun `Orchestrator keeps every tool and leads a task step by step`() {
        coordinator.handle(ICodeIntent.ChooseAssistantMode(AssistantMode.Orchestrator))
        until { state().settings.mode == AssistantMode.Orchestrator }
        said("Add settings")
        assertEquals(listOf("read_file", "edit_file"), requests.last().tools.map { it.name })
        assertTrue("implements and verifies" in requests.last().instructions)
    }

    @Test fun `a chat is shared as its transcript, head and answers`() {
        said("Rename x")
        val session = state().session!!
        coordinator.handle(ICodeIntent.ExportAssistantSession(session))
        until { effects.any { it is ICodeEffect.ShareChat } }
        val effect = effects.filterIsInstance<ICodeEffect.ShareChat>().last()
        assertEquals("Rename x", effect.title)
        assertTrue("## Assistant" in effect.text)
        assertTrue("Re: Rename x" in effect.text)
        assertTrue("Worked in app" in effect.text)
    }

    @Test fun `a chat with nothing in it is not shared`() {
        coordinator.handle(ICodeIntent.ExportAssistantSession("ghost"))
        until { effects.any { it is ICodeEffect.Told } }
        assertTrue(effects.any { it.said()?.title == "Nothing to share yet" })
        assertTrue(effects.none { it is ICodeEffect.ShareChat })
    }

    @Test fun `a chat's transcript shows its steps, their results and the compaction en route`() {
        editTurn()
        coordinator.handle(ICodeIntent.SendAssistantMessage("Add a helper"))
        until { state().approval != null }
        coordinator.handle(ICodeIntent.AnswerAssistant("c1", AssistantAnswer.Allow))
        until { state().turn == null && state().messages.lastOrNull() is AssistantMessage.Reply }
        coordinator.handle(ICodeIntent.ExportAssistantSession(state().session!!))
        until { effects.any { it is ICodeEffect.ShareChat } }
        val text = effects.filterIsInstance<ICodeEffect.ShareChat>().last().text
        assertTrue("### Edit a.kt" in text)
        assertTrue("+2 −1" in text)
        assertTrue("ok" in text)
    }

    @Test fun `the model chosen on the page is one of a provider's, and its effort is sent only where offered`() {
        coordinator.handle(ICodeIntent.ChooseAssistantEffort(AssistantEffort.High))
        until { state().settings.effort == AssistantEffort.High }
        said("plain")
        assertNull("gpt-5 here offers no effort", requests.last().effort)

        coordinator.handle(ICodeIntent.ChooseAssistantModel(openAI, "o3"))
        until { state().settings.model?.id == "o3" }
        assertEquals(listOf(AssistantEffort.Low, AssistantEffort.High), state().settings.model?.efforts)
        said("reasoned")
        assertEquals("o3", requests.last().model.id)
        assertEquals(AssistantEffort.High, requests.last().effort)
        assertEquals("sk-test", requests.last().connection.apiKey)

        // A model the provider does not list is not chosen.
        coordinator.handle(ICodeIntent.ChooseAssistantModel(openAI, "gpt-9"))
        coordinator.handle(ICodeIntent.ChooseAssistantMode(AssistantMode.Code))
        Thread.sleep(50)
        assertEquals("o3", state().settings.model?.id)
    }

    @Test fun `Compact summarizes the chat, and later turns send the summary and what came after`() {
        said("one")
        said("two")
        coordinator.handle(ICodeIntent.CompactAssistantChat)
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
        coordinator.handle(ICodeIntent.SendAssistantMessage("first"))
        until { requests.size == 1 && state().turn == null }
        assertEquals(850L, state().contextTokens)
        said("second")
        assertEquals(listOf(AssistantMessage.User("first"), AssistantMessage.Reply("long")), compactions.single())
        assertEquals(emptyList<AssistantMessage>(), requests.last().history)
        // The marker comes before the message it was made for.
        val kinds = state().messages.map { it.javaClass.simpleName }
        assertEquals(listOf("User", "Reply", "Compacted", "User", "Reply"), kinds)
    }

    @Test fun `a workspace's rename stops its turn first, then its chats and their answers follow, and its delete takes them`() = runBlocking {
        val asked = CompletableDeferred<Unit>()
        script = { _, events ->
            events.replied(AssistantMessage.Reply("Working"))
            asked.complete(Unit)
            awaitCancellation()
        }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Hello"))
        asked.await()
        val session = state().session!!
        chats.allowInWorkspace(app, session)
        chats.open(app, session, listOf("/etc"))
        coordinator.workspaceMoved(app, "$app-renamed")
        assertNull(state().turn)
        assertEquals("$app-renamed", state().workspace)
        assertEquals(session, state().session)
        assertEquals(2, chats.of("$app-renamed", session).messages.size)
        assertTrue(chats.sessions(app).isEmpty())
        assertTrue(chats.allowedInWorkspace("$app-renamed", session))
        assertEquals(setOf("/etc"), chats.roots("$app-renamed", session))
        coordinator.workspaceForgotten("$app-renamed")
        assertNull(state().workspace)
        assertTrue(chats.sessions("$app-renamed").isEmpty())
        assertFalse(chats.allowedInWorkspace("$app-renamed", session))
    }

    @Test fun `a provider is checked, saved with its models and its own key, and its server asked for its models`() {
        coordinator.handle(ICodeIntent.SaveAssistantProvider("", AssistantProviderKind.Anthropic, "Anthropic", "", null, listOf(AssistantModelSpec("claude-sonnet-4-5"))))
        until { effects.any { it.said()?.title == "Enter the API key" } }
        coordinator.handle(ICodeIntent.SaveAssistantProvider("", AssistantProviderKind.OpenAICompatible, "DeepSeek", "https://api.deepseek.com/v1", null, emptyList()))
        until { effects.any { it.said()?.title == "Add at least one model" } }
        coordinator.handle(
            ICodeIntent.SaveAssistantProvider(
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

        coordinator.handle(ICodeIntent.ChooseAssistantModel(deepSeek.id, "deepseek-chat"))
        until { state().settings.provider?.id == deepSeek.id }
        said("hello")
        assertEquals(AssistantConnection(AssistantProviderKind.OpenAICompatible, "https://api.deepseek.com/v1", "sk-deep"), requests.last().connection)

        // Its server's models: with the saved key of the provider, or the one typed.
        listed = listOf(AssistantModel("gpt-5", known = true), AssistantModel("gpt-6-preview"))
        coordinator.handle(ICodeIntent.EditAssistantProvider(openAI))
        coordinator.handle(ICodeIntent.ListAssistantModels(openAI, AssistantProviderKind.OpenAI, "", null))
        until { state().available?.loading == false }
        assertEquals(listed, state().available?.models)
        coordinator.handle(ICodeIntent.ListAssistantModels(openAI, AssistantProviderKind.OpenAI, "", "sk-wrong"))
        until { state().available?.failure != null }
        assertEquals("The provider refused the API key — check it in the provider's settings", state().available?.failure)

        // Removing the chosen provider moves the chat to the first model left.
        coordinator.handle(ICodeIntent.RemoveAssistantProvider(deepSeek.id))
        until { state().settings.providers.size == 1 }
        assertEquals(AssistantChoice(openAI, "gpt-5"), state().settings.choice)
    }

    @Test fun `a photo from the phone goes to a model that sees, kept in its chat, and is refused by one that does not`() {
        phoneFiles["content://photos/1"] = PhoneFile("IMG_1.jpg", "image/jpeg") { byteArrayOf(9, 9, 9).inputStream() }
        coordinator.handle(ICodeIntent.SendAssistantMessage("What is this?", listOf("content://photos/1")))
        until { state().failure != null }
        assertEquals("gpt-5 does not read images — choose a model that does", state().failure)
        assertTrue("nothing was sent", requests.isEmpty())

        providers.saveProvider(openAI, AssistantProviderKind.OpenAI, "OpenAI", null, null, listOf(AssistantModelSpec("gpt-5"), AssistantModelSpec("gpt-5-vision")))
        coordinator.handle(ICodeIntent.ChooseAssistantModel(openAI, "gpt-5-vision"))
        until { state().settings.model?.id == "gpt-5-vision" }
        said("ok")
        script = { _, events -> events.replied(AssistantMessage.Reply("A cat.")) }
        coordinator.handle(ICodeIntent.SendAssistantMessage("What is this?", listOf("content://photos/1")))
        until { requests.size == 2 && state().turn == null }
        val photo = requests.last().input!!.attachments.single()
        assertEquals("IMG_1.jpg", photo.name)
        assertEquals(AssistantAttachmentKind.Image, photo.kind)
        assertEquals(listOf<Byte>(9, 9, 9), File(photo.file!!).readBytes().toList())
        assertTrue("kept in the chat's own folder", File(photo.file!!).parentFile!!.name == "${state().session}.files")

        // A grant gone with the process: said, nothing sent.
        coordinator.handle(ICodeIntent.SendAssistantMessage("Again", listOf("content://photos/gone")))
        until { state().failure != null }
        assertEquals("A file from the phone can no longer be read — attach it again", state().failure)
    }

    @Test fun `a photo alone is a message and names its chat, while nothing at all is not sent`() {
        coordinator.handle(ICodeIntent.SendAssistantMessage("  "))
        phoneFiles["content://photos/2"] = PhoneFile("IMG_2.jpg", "image/jpeg") { byteArrayOf(7).inputStream() }
        providers.saveProvider(openAI, AssistantProviderKind.OpenAI, "OpenAI", null, null, listOf(AssistantModelSpec("gpt-5-vision")))
        coordinator.handle(ICodeIntent.ChooseAssistantModel(openAI, "gpt-5-vision"))
        until { state().settings.model?.id == "gpt-5-vision" }
        script = { _, events -> events.replied(AssistantMessage.Reply("A receipt.")) }
        coordinator.handle(ICodeIntent.SendAssistantMessage("", listOf("content://photos/2")))
        until { requests.size == 1 && state().turn == null }
        assertEquals("", requests.single().input!!.text)
        assertEquals("IMG_2.jpg", requests.single().input!!.attachments.single().name)
        assertEquals("IMG_2.jpg", state().sessions.single().title)
    }

    @Test fun `Try Again sends a message that failed before any reply once more, not twice`() {
        script = { _, _ -> throw AssistantTurnException("The provider is limiting requests — try again shortly") }
        coordinator.handle(ICodeIntent.SendAssistantMessage("Hello"))
        until { state().failure != null }
        script = { _, events -> events.replied(AssistantMessage.Reply("Hi.")) }
        coordinator.handle(ICodeIntent.RetryAssistantMessage)
        until { requests.size == 2 && state().turn == null }
        assertNull(state().failure)
        assertEquals(listOf(AssistantMessage.User("Hello"), AssistantMessage.Reply("Hi.")), state().messages)
        assertEquals(AssistantMessage.User("Hello"), requests.last().input)
        coordinator.handle(ICodeIntent.RetryAssistantMessage)
        until { effects.any { it.said()?.title == "Nothing to send again" } }
    }

    @Test fun `a commit message is written from the staged changes by the chat's model, and taken once`() {
        coordinator.handle(ICodeIntent.WriteCommitMessage)
        until { state().commitMessage?.message != null }
        assertEquals(AssistantCommitMessage(app, message = "feat: add login"), state().commitMessage)
        val (model, request) = answers.single()
        assertEquals("gpt-5", model)
        assertTrue(request, "- feat: first\n- fix: second" in request)
        assertTrue(request, "3\t1\tsrc/a.kt" in request)
        assertTrue(request, "+fun a() = 1" in request)
        coordinator.handle(ICodeIntent.CommitMessageTaken)
        until { state().commitMessage == null }
    }

    @Test fun `commit messages are written by the model chosen for them, and nothing staged is said`() {
        coordinator.handle(ICodeIntent.ChooseCommitModel(AssistantChoice(openAI, "o3")))
        until { state().settings.commitChoice != null }
        assertEquals("o3", state().settings.commitWriter?.second?.id)
        coordinator.handle(ICodeIntent.WriteCommitMessage)
        until { state().commitMessage?.message != null }
        assertEquals("o3", answers.single().first)
        staged = ""
        coordinator.handle(ICodeIntent.WriteCommitMessage)
        until { state().commitMessage?.failure != null }
        assertEquals("Nothing is staged: stage the changes to describe first", state().commitMessage?.failure)
        assertEquals(1, answers.size)
    }

    @Test fun `a model's refusal is said, and a repository without commits follows no style`() {
        log = CliGitService.CommandResult(128, "", "fatal: your current branch 'main' does not have any commits yet")
        answer = { throw AssistantTurnException("The provider refused the API key — check it in the provider's settings") }
        coordinator.handle(ICodeIntent.WriteCommitMessage)
        until { state().commitMessage?.failure != null }
        assertEquals("The provider refused the API key — check it in the provider's settings", state().commitMessage?.failure)
        assertTrue(answers.single().second.startsWith("This repository has no commits yet."))
        // Read, the failure goes; the next tap tries again.
        coordinator.handle(ICodeIntent.CommitMessageTaken)
        until { state().commitMessage == null }
    }

    /** Reaching its `path`, as the file tools do. */
    private inner class FakeTool(override val name: String, override val kind: AssistantToolKind, private val workspace: AssistantWorkspace) : AssistantTool, AssistantFileChanger {
        override val description = name
        override fun reach(arguments: JSONObject) = workspace.reach(arguments.optString("path").ifEmpty { null })
        override val parameters = emptyList<AssistantParameter>()
        override var lastChanges: LineChanges? = null
        override fun summary(arguments: JSONObject) = name
        override suspend fun run(arguments: JSONObject): String {
            ran += name
            // A change counts its lines as the write and edit tools do.
            if (kind == AssistantToolKind.Change) lastChanges = LineChanges(2, 1)
            return "ok"
        }
    }

    @Test fun `the menu's Terminal opens the chat's own tab, a new chat given its ID that its first message keeps`() {
        assertNull(state().session)
        coordinator.handle(ICodeIntent.OpenAssistantTerminal)
        until { state().tab == "session-1" }
        val chat = state().session!!
        assertEquals(listOf(app to chat), opened)
        said("one")
        assertEquals(chat, state().session)
        assertEquals(chat, chats.sessions(app).single().id)
        // Another chat shown: not this one's tab.
        coordinator.handle(ICodeIntent.NewAssistantChat)
        until { state().session == null }
        assertNull(state().tab)
    }

    @Test fun `a terminal that cannot open is said, and a deleted chat's tabs close with it`() {
        tabRefusal = "The Linux runtime is not installed"
        coordinator.handle(ICodeIntent.OpenAssistantTerminal)
        until { effects.any { it.said() == Said(NoticeTone.Problem, "Couldn't open the chat's terminal", output = "The Linux runtime is not installed") } }
        assertNull(state().tab)
        said("one")
        val chat = state().session!!
        coordinator.handle(ICodeIntent.DeleteAssistantSession(chat))
        until { forgotten.contains(app to chat) }
    }
}
