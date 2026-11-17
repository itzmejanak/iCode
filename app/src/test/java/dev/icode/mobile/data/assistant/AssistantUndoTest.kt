package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceRoot
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.AssistantUndo
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.UndoIntent
import dev.icode.mobile.domain.said
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Undo back to before one of a chat's messages: its place's files, the chat, or both (docs/assistant-and-mcp.md). */
class AssistantUndoTest {
    @get:Rule val temp = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val stateHolder = ICodeStateHolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val effects = CopyOnWriteArrayList<ICodeEffect>()
    private val providers = SharedPreferencesAssistantProviderStore(InMemoryPreferences(), { "enc:$it" }, { it.removePrefix("enc:") })
    private val runtime by lazy { TestRuntime(temp.newFolder("runtime")) }
    private val root by lazy { runtime.root }
    private val checkpoints by lazy { File(temp.root, "checkpoints") }
    private var script: suspend (AssistantEvents) -> Unit = { }
    @Volatile private var runs = 0

    private val engine = object : AssistantEngine {
        override val catalogue = mapOf(AssistantProviderKind.OpenAI to listOf(AssistantModel("gpt-5", 400_000, known = true)))
        override fun model(kind: AssistantProviderKind, spec: AssistantModelSpec) = AssistantModel(spec.id, spec.contextWindow ?: 400_000)
        override suspend fun available(connection: AssistantConnection): List<AssistantModel> = emptyList()
        override suspend fun run(turn: AssistantTurnRequest, events: AssistantEvents): AssistantRunEnd {
            runs++
            script(events)
            return AssistantRunEnd.Answered
        }
        override suspend fun compact(connection: AssistantConnection, model: AssistantModel, instructions: String, history: List<AssistantMessage>) = "Summary"
        override suspend fun answer(connection: AssistantConnection, model: AssistantModel, instructions: String, request: String) = ""
    }

    private val coordinator by lazy {
        AssistantCoordinator(
            stateHolder, scope, engine, providers, AssistantChats(AssistantChatStore(temp.newFolder("chats"))),
            tools = { _, _, _ -> emptyList() },
            activeRoot = { root.path },
            describe = { WorkspaceRoot("app", it, "/workspace/app") },
            paths = runtime.paths,
            attachments = { openText -> AssistantAttachments({ null }, { it }, openText, readText = { File(it).readText() }) },
            git = { _, _ -> CliGitService.CommandResult(0, "", "") },
            storageAccess = { true },
            shares = File(temp.root, "shares"),
            checkpoints = checkpoints,
        )
    }

    @Before fun start() {
        BackendLog.writer = { _, _, _ -> }
        scope.launch { stateHolder.effects.collect { effects += it } }
        providers.saveProvider("", AssistantProviderKind.OpenAI, "OpenAI", null, "sk-test", listOf(AssistantModelSpec("gpt-5")))
        coordinator.start()
        until { state().settings.ready && state().catalogue.isNotEmpty() }
        coordinator.handle(ICodeIntent.OpenAssistant(inWorkspace = true))
        until { state().workspace == root.path }
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

    private fun write(path: String, text: String) = File(root, path).apply { parentFile.mkdirs(); writeText(text) }

    private fun files(): Map<String, String> =
        root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }

    /** [text] sent, and answered by a turn that does [work] to the workspace's files. */
    private fun said(text: String, work: () -> Unit = {}) {
        val before = runs
        script = { events ->
            work()
            events.replied(AssistantMessage.Reply("Re: $text"))
        }
        coordinator.handle(ICodeIntent.SendAssistantMessage(text))
        until { runs == before + 1 && !state().running }
    }

    private fun undo(index: Int, files: Boolean, chat: Boolean) = coordinator.handle(UndoIntent.Message(index, files, chat))

    private fun told(title: String) = until { effects.any { it.said()?.title == title } }

    private fun texts() = state().messages.map { (it as? AssistantMessage.User)?.text ?: (it as AssistantMessage.Reply).text }

    @Test fun `files and chat go back to before a message, and its words return to the composer`() {
        write("a.txt", "mine")
        said("one") { write("a.txt", "by one") }
        said("two") {
            write("a.txt", "by two")
            write("src/b.txt", "new")
        }
        undo(2, files = true, chat = true)
        told("Files and chat back to before that message")
        assertEquals(mapOf("a.txt" to "by one"), files())
        assertEquals(listOf("one", "Re: one"), texts())
        assertEquals("two", state().takenBack)
        coordinator.handle(UndoIntent.Taken)
        until { state().takenBack == null }
    }

    @Test fun `files only keeps the chat, chat only keeps the files`() {
        said("one") { write("a.txt", "by one") }
        undo(0, files = true, chat = false)
        told("Files back to before that message")
        assertEquals(emptyMap<String, String>(), files())
        assertEquals(listOf("one", "Re: one"), texts())
        write("a.txt", "again")
        undo(0, files = false, chat = true)
        told("Chat back to before that message")
        assertEquals(mapOf("a.txt" to "again"), files())
        assertEquals(emptyList<String>(), texts())
        assertNull("a chat left empty is a new one", state().session)
    }

    @Test fun `files changed outside the chat since are listed and asked about first`() {
        said("one") { write("a.txt", "by one") }
        write("notes.txt", "the user's")
        said("two") { write("a.txt", "by two") }
        undo(0, files = true, chat = false)
        until { state().undo != null }
        assertEquals(AssistantUndo(0, files = true, chat = false, changed = listOf("notes.txt")), state().undo)
        assertEquals("nothing is undone while it asks", "the user's", File(root, "notes.txt").readText())
        coordinator.handle(UndoIntent.Dismiss)
        until { state().undo == null }
        assertEquals("by two", File(root, "a.txt").readText())
        coordinator.handle(UndoIntent.Message(0, files = true, chat = false, confirmed = true))
        told("Files back to before that message")
        assertEquals(emptyMap<String, String>(), files())
    }

    @Test fun `nothing is undone while a chat works in that place`() {
        said("one") { write("a.txt", "by one") }
        val release = CompletableDeferred<Unit>()
        script = { release.await() }
        coordinator.handle(ICodeIntent.SendAssistantMessage("two"))
        until { state().running }
        undo(0, files = true, chat = true)
        told("Stop the work there first")
        release.complete(Unit)
        until { !state().running }
        assertEquals("by one", File(root, "a.txt").readText())
        assertEquals(listOf("one", "Re: one", "two"), texts())
    }

    @Test fun `a message sent again keeps its checkpoint, and its new turn is the chat's own work`() {
        script = { write("a.txt", "half"); throw AssistantTurnException("The provider is limiting requests — try again shortly") }
        coordinator.handle(ICodeIntent.SendAssistantMessage("one"))
        until { state().failure != null }
        val kept = (state().messages.single() as AssistantMessage.User).checkpoint
        script = { events -> write("a.txt", "done"); events.replied(AssistantMessage.Reply("Re: one")) }
        coordinator.handle(ICodeIntent.RetryAssistantMessage)
        until { runs == 2 && !state().running }
        assertEquals(kept, (state().messages.first() as AssistantMessage.User).checkpoint)
        undo(0, files = true, chat = false)
        told("Files back to before that message")
        assertNull("not asked: what the turns changed is the chat's", state().undo)
        assertEquals(emptyMap<String, String>(), files())
    }

    @Test fun `a deleted chat's checkpoints go with it`() {
        said("one") { write("a.txt", "by one") }
        val place = File(checkpoints, Sha256.hex(root.path))
        assertFalse(place.listFiles { file -> file.name.endsWith(".json") }.isNullOrEmpty())
        coordinator.handle(ICodeIntent.DeleteAssistantSession(state().session!!))
        until { place.listFiles { file -> file.name.endsWith(".json") }.isNullOrEmpty() && File(place, "blobs").listFiles().isNullOrEmpty() }
    }
}
