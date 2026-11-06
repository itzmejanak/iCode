package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.TestRuntime
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantElsewhere
import dev.icode.mobile.domain.AssistantPermission
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantToolKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * An act in an app on the phone asks about the app, as a folder outside does (docs/phone-agent-plan.md L6);
 * an MCP server's tool asks about its server the same way (docs/mcp-plan.md §6.4), and a command in the
 * user's own terminal tab about that tab (docs/agent-control-plan.md §4.3).
 */
class AssistantGateTest {
    @get:Rule val temp = TemporaryFolder()

    private val stateHolder = ICodeStateHolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chats by lazy { AssistantChats(AssistantChatStore(temp.newFolder("chats"))) }
    private val page by lazy { AssistantPage(stateHolder, scope, chats) }
    private val questions by lazy { AssistantQuestions(page) }
    private val runtime by lazy { TestRuntime(temp.newFolder("runtime")) }
    private var permission = AssistantPermission.AskFirst
    private val tap = AssistantCall("c1", "phone_tap", "{}", "Tap [2] “Send”", AssistantToolKind.Change)
    private val ran = mutableListOf<String>()
    private var elsewhere: AssistantElsewhere? = AssistantElsewhere("WhatsApp", AssistantPermissions.appRoot("com.whatsapp"), AssistantElsewhere.Kind.App)

    private val actor = object : AssistantTool, AssistantPlaceActor {
        override val name = "phone_tap"
        override val description = "Taps"
        override val parameters = emptyList<AssistantParameter>()
        override val kind = AssistantToolKind.Change
        override fun summary(arguments: JSONObject) = "Tap [2] “Send”"
        override fun place(arguments: JSONObject) = elsewhere
        override suspend fun run(arguments: JSONObject): String = "Tapped".also { ran += it }
    }

    private fun gate(session: String = "s1") =
        AssistantGate(actor, runtime.workspace, chats, page, questions, runtime.root.path, session, { permission }, { tap }) { _, _ -> }

    @After fun stop() = scope.cancel()

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(5)
        }
    }

    @Test fun `an act asks about its app, and Allow in it answers the chat's later acts there`() = runBlocking {
        val first = scope.async { gate().run(JSONObject()) }
        until { stateHolder.current.assistant.approval != null }
        val approval = stateHolder.current.assistant.approval!!
        assertEquals("WhatsApp", approval.reach!!.place?.name)
        assertEquals(listOf("app:com.whatsapp"), approval.reach!!.roots)
        assertEquals("Act in WhatsApp?", AssistantPermissions.question(approval, "Home"))
        assertEquals("Allow in WhatsApp", AssistantPermissions.allowIn(approval, "Home"))
        assertEquals("Allow Once", AssistantPermissions.allowOnce(approval))
        questions.answer("c1", AssistantAnswer.AllowInChat)
        assertEquals("Tapped", first.await())
        assertEquals("Tapped", gate().run(JSONObject()))
        assertEquals(2, ran.size)
    }

    @Test fun `Deny tells the model it may not act in the app, Auto in workspace asks too, and Full access asks nothing`() = runBlocking {
        permission = AssistantPermission.AutoInWorkspace
        val denied = scope.async { runCatching { gate("s2").run(JSONObject()) } }
        until { stateHolder.current.assistant.approval != null }
        questions.answer("c1", AssistantAnswer.Deny)
        val refusal = denied.await().exceptionOrNull()
        assertTrue(refusal is AssistantToolException && refusal.message == "The user did not allow acting in WhatsApp")
        assertTrue(ran.isEmpty())
        permission = AssistantPermission.FullAccess
        assertEquals("Tapped", gate("s3").run(JSONObject()))
    }

    @Test fun `a server's tool asks to use the server, and one set to run asks nothing`() = runBlocking {
        elsewhere = AssistantElsewhere("GitHub", AssistantPermissions.serverRoot("m-1"), AssistantElsewhere.Kind.Server)
        val asked = scope.async { runCatching { gate("s4").run(JSONObject()) } }
        until { stateHolder.current.assistant.approval != null }
        val approval = stateHolder.current.assistant.approval!!
        assertEquals(listOf("mcp:m-1"), approval.reach!!.roots)
        assertEquals("Use GitHub?", AssistantPermissions.question(approval, "Home"))
        assertEquals("Allow in GitHub", AssistantPermissions.allowIn(approval, "Home"))
        questions.answer("c1", AssistantAnswer.Deny)
        val refusal = asked.await().exceptionOrNull()
        assertTrue(refusal is AssistantToolException && refusal.message == "The user did not allow using GitHub")
        // The user's Runs: no place to ask about, in any mode.
        elsewhere = null
        assertEquals("Tapped", gate("s5").run(JSONObject()))
    }

    @Test fun `a command in the user's terminal tab asks to use it`() = runBlocking {
        elsewhere = AssistantElsewhere("bash 2", AssistantPermissions.terminalRoot("session-4"), AssistantElsewhere.Kind.Terminal)
        val asked = scope.async { runCatching { gate("s6").run(JSONObject()) } }
        until { stateHolder.current.assistant.approval != null }
        val approval = stateHolder.current.assistant.approval!!
        assertEquals(listOf("terminal:session-4"), approval.reach!!.roots)
        assertEquals("Use your terminal bash 2?", AssistantPermissions.question(approval, "Home"))
        assertEquals("Allow in bash 2", AssistantPermissions.allowIn(approval, "Home"))
        questions.answer("c1", AssistantAnswer.Deny)
        val refusal = asked.await().exceptionOrNull()
        assertTrue(refusal is AssistantToolException && refusal.message == "The user did not allow using their terminal bash 2")
    }
}
