package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.InMemoryPreferences
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpStatus
import dev.icode.mobile.domain.McpToolChoice
import dev.icode.mobile.domain.McpTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The agent's MCP tools (docs/agent-control-plan.md §5) over the pages' own operations: what it adds,
 * changes and removes is what the pages show, a value is never read back, and a workspace's server
 * stays its file's.
 */
class McpServerToolsTest {
    private val workspace = Files.createTempDirectory("workspace").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateHolder = ICodeStateHolder()
    private val store = McpServerStore(InMemoryPreferences(), { "sealed:$it" }, { it.removePrefix("sealed:") })
    private val http = HttpClient(OkHttp)
    private val servers = McpServers(
        stateHolder, scope, store, local = null, McpRemote(http),
        McpWorkspaceServers(store, { "/workspace/${File(it).name}" }) { emptyMap() },
        McpSignIn(store, http, "dev.icode.mobile:/mcp-sign-in", website = ""), McpRegistry(http),
        runtimeInstalled = { false }, activeRoot = { workspace.path },
    )
    private val list = ListMcpServersTool(workspace.path, servers)
    private val save = SaveMcpServerTool(workspace.path, servers)
    private val remove = RemoveMcpServerTool(workspace.path, servers)

    @After
    fun clean() {
        scope.cancel()
        workspace.deleteRecursively()
    }

    private fun run(tool: dev.icode.mobile.data.assistant.AssistantTool, arguments: JSONObject): String = runBlocking { tool.run(arguments) }

    private fun refusal(tool: dev.icode.mobile.data.assistant.AssistantTool, arguments: JSONObject): String = try {
        run(tool, arguments)
        fail("expected a refusal")
        ""
    } catch (refused: AssistantToolException) {
        refused.message.orEmpty()
    }

    /** Until [id]'s connection has settled: a local server, with no runtime here, fails at once. */
    private fun settled(id: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (servers.listed(workspace.path).first { it.first.id == id }.second.status == McpStatus.Starting ||
            servers.listed(workspace.path).first { it.first.id == id }.second.status == McpStatus.Off) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            Thread.sleep(5)
        }
    }

    private val github = JSONObject().put("name", "GitHub").put("command", "npx")
        .put("args", JSONArray().put("-y").put("@github/mcp")).put("env", JSONObject().put("GITHUB_TOKEN", "ghp_secret"))

    @Test
    fun `a server is added from fields, listed as the pages show it, and its value never read back`() {
        assertEquals("No MCP server is added. save_mcp_server adds one.", run(list, JSONObject()))
        val added = run(save, github)
        val id = store.servers().single().id
        assertEquals("Added GitHub ($id), on: list_mcp_servers says how it is, and its tools are offered from the next turn.", added)
        settled(id)
        assertEquals(
            "$id · GitHub · on · Failed: Needs the Linux runtime\nlocal: npx -y @github/mcp\nenv: GITHUB_TOKEN (set)",
            run(list, JSONObject()),
        )
        // Kept as the pages keep one: the value sealed, and only there.
        assertEquals("ghp_secret", store.reveal(store.servers().single()).single().value)
        assertEquals(listOf(store.servers().single()), stateHolder.current.mcp.servers)
        // What the user is asked names the value, never shows it.
        val asked = save.detail(github)
        assertTrue(asked, asked.contains("“GitHub” · npx -y @github/mcp · values: GITHUB_TOKEN") && !asked.contains("ghp_secret"))
    }

    @Test
    fun `a server is changed by its id, only in what is given`() {
        run(save, github)
        val id = store.servers().single().id
        val changed = run(save, JSONObject().put("id", id).put("enabled", false).put("tools", JSONObject().put("delete_repo", "off")).put("unset", JSONArray().put("GITHUB_TOKEN")))
        assertEquals("Changed GitHub ($id), off.", changed)
        val kept = store.servers().single()
        assertFalse(kept.enabled)
        assertEquals(mapOf("delete_repo" to McpToolChoice.Off), kept.tools)
        assertEquals(McpTransport.Local("npx", listOf("-y", "@github/mcp")), kept.transport)
        assertEquals("$id · GitHub · off\nlocal: npx -y @github/mcp\ntools, as chosen: delete_repo (off)", run(list, JSONObject()))
        assertEquals("Change the MCP server GitHub", save.summary(JSONObject().put("id", id)))
    }

    @Test
    fun `a configuration adds each server it names, one missing a value off and saying which`() {
        val config = """{"mcpServers": {"linear": {"url": "https://mcp.linear.app/mcp", "headers": {"Authorization": "Bearer ${'$'}{LINEAR_API_KEY}"}, "timeout": 30}}}"""
        val added = run(save, JSONObject().put("config", config))
        val id = store.servers().single().id
        assertEquals("Added linear ($id, off). Its tools are offered from the next turn; list_mcp_servers says how each is.\nNot read: timeout", added)
        assertEquals("$id · linear · off: Enter a value for Authorization\nremote: https://mcp.linear.app/mcp\nheaders: Authorization (not set)", run(list, JSONObject()))
        assertEquals("Add MCP servers from a configuration", save.summary(JSONObject().put("config", config)))
    }

    @Test
    fun `what the pages refuse, the model is told in their words`() {
        run(save, github)
        val id = store.servers().single().id
        assertEquals("Another server is called GitHub", refusal(save, github))
        assertEquals("Remote servers need https:// (http only to this phone)", refusal(save, JSONObject().put("name", "Web").put("url", "http://example.com/mcp")))
        assertEquals("Name the server", refusal(save, JSONObject().put("command", "uvx")))
        assertEquals("Give command or url, not both", refusal(save, JSONObject().put("name", "X").put("command", "uvx").put("url", "https://x.test")))
        assertEquals("Give config, or command or url", refusal(save, JSONObject().put("name", "X")))
        assertEquals("GitHub is a local server: remove it and add a remote one", refusal(save, JSONObject().put("id", id).put("url", "https://x.test")))
        assertEquals("There is no MCP server m-0: list_mcp_servers lists them", refusal(save, JSONObject().put("id", "m-0")))
        assertEquals("A configuration adds servers: to change one, give its id and what changes", refusal(save, JSONObject().put("id", id).put("config", "{}")))
        assertEquals("maybe is not a choice: off, asks_first, runs", refusal(save, JSONObject().put("id", id).put("tools", JSONObject().put("x", "maybe"))))
        assertEquals("This is not JSON an MCP configuration is written in", refusal(save, JSONObject().put("config", "not json")))
        assertEquals(1, store.servers().size)
    }

    @Test
    fun `a server is removed by its id, a workspace's only from its file`() {
        run(save, github)
        val id = store.servers().single().id
        assertEquals("Remove the MCP server GitHub", remove.summary(JSONObject().put("id", id)))
        assertEquals("Removed GitHub.", run(remove, JSONObject().put("id", id)))
        assertTrue(store.servers().isEmpty())
        assertEquals("There is no MCP server $id", refusal(remove, JSONObject().put("id", id)))
        File(workspace, ".mcp.json").writeText("""{"mcpServers": {"tool": {"command": "node", "args": ["server.js"]}}}""")
        assertEquals("tool comes from this workspace's .mcp.json: it is removed there", refusal(remove, JSONObject().put("id", "${workspace.path}#tool")))
    }

    @Test
    fun `a workspace's server keeps its file's name, command and address, and its trust stays the user's`() {
        File(workspace, ".mcp.json").writeText("""{"mcpServers": {"tool": {"command": "node", "args": ["server.js"]}}}""")
        val id = "${workspace.path}#tool"
        assertEquals(
            "tool comes from this workspace's .mcp.json: its name, command and address are the file's",
            refusal(save, JSONObject().put("id", id).put("command", "python")),
        )
        assertEquals("Changed tool ($id), on: list_mcp_servers says how it is, and its tools are offered from the next turn.", run(save, JSONObject().put("id", id).put("name", "tool").put("tools", JSONObject().put("run", "runs"))))
        settled(id)
        val listed = run(list, JSONObject())
        assertTrue(listed, listed.startsWith("$id · tool · on · Not trusted yet: Trust it to start it: it comes from this workspace's .mcp.json\nfrom this workspace's .mcp.json\nlocal: node server.js"))
        assertEquals(mapOf("run" to McpToolChoice.Runs), servers.listed(workspace.path).first { it.first.id == id }.first.tools)
    }

    @Test
    fun `changes ask every time, in every mode, and a reading mode lists only`() {
        assertTrue(save.asksAlways && remove.asksAlways)
        assertFalse(list.asksAlways)
        assertEquals(listOf(AssistantToolKind.Read, AssistantToolKind.Change, AssistantToolKind.Change), mcpServerTools(workspace.path, servers).map { it.kind })
    }

    @Test
    fun `a page's change refused is the user's notice`() = runBlocking {
        servers.start()
        val notice = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { stateHolder.effects.first { it is ICodeEffect.Notice } } }
        servers.handle(McpIntent.SaveServer(McpServer("", " ", "", McpTransport.Local("npx"))))
        val said = (notice.await() as ICodeEffect.Notice).notice
        assertEquals("Name the server", said.title)
    }
}
