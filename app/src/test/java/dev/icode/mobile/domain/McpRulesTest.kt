package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/mcp-plan.md §4.1, §6.1: a pasted configuration in each of its shapes, the checks, the names. */
class McpRulesTest {
    @Test
    fun `an mcpServers map gives each server by its name`() {
        val imported = McpRules.import(
            """{"mcpServers": {
                "everything": {"command": "npx", "args": ["-y", "@modelcontextprotocol/server-everything"]},
                "github": {"url": "https://api.githubcopilot.com/mcp/", "headers": {"Authorization": "Bearer ${'$'}{GITHUB_TOKEN}"}}
            }}""",
        )
        val everything = imported.servers.single { it.name == "everything" }
        val github = imported.servers.single { it.name == "github" }
        assertEquals(McpTransport.Local("npx", listOf("-y", "@modelcontextprotocol/server-everything")), everything.transport)
        val remote = github.transport as McpTransport.Remote
        assertEquals("https://api.githubcopilot.com/mcp/", remote.url)
        // A value that names a variable waits for the user's.
        assertEquals(McpValue("Authorization", null, secret = true, hint = "Bearer \${GITHUB_TOKEN}"), remote.headers.single())
        assertTrue(imported.ignored.isEmpty())
    }

    @Test
    fun `a servers map, a map of names, and one server are read too`() {
        assertEquals("a", McpRules.import("""{"servers": {"a": {"command": "uvx", "args": ["mcp-server-time"]}}}""").servers.single().name)
        assertEquals(setOf("a", "b"), McpRules.import("""{"a": {"command": "x"}, "b": {"serverUrl": "https://b.example"}}""").servers.map { it.name }.toSet())
        val single = McpRules.import("""{"httpUrl": "https://one.example/mcp", "disabled": true}""", single = "One").servers.single()
        assertEquals("One", single.name)
        assertFalse(single.enabled)
    }

    @Test
    fun `fields iCode does not read are named once, and nothing else is kept`() {
        val imported = McpRules.import("""{"mcpServers": {"a": {"command": "x", "type": "stdio", "timeout": 5, "alwaysAllow": []}, "b": {"command": "y", "timeout": 1}}}""")
        assertEquals(listOf("alwaysAllow", "timeout"), imported.ignored)
        assertEquals(McpTransport.Local("x"), imported.servers.single { it.name == "a" }.transport)
    }

    @Test
    fun `what is not a configuration is said so`() {
        listOf("not json", "{}", """{"mcpServers": {}}""").forEach { json ->
            try {
                McpRules.import(json)
                throw AssertionError("$json is no configuration")
            } catch (refused: IllegalArgumentException) {
                assertTrue(refused.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun `a server is checked before it is saved`() {
        val local = McpServer("", "Time", "", McpTransport.Local("uvx", listOf("mcp-server-time")))
        assertNull(McpRules.problem(local, emptyList()))
        assertEquals("Name the server", McpRules.problem(local.copy(name = " "), emptyList()))
        assertEquals("Another server is called Time", McpRules.problem(local, listOf(local.copy(id = "m-1", name = "time"))))
        assertEquals("Enter the command that starts it", McpRules.problem(local.copy(transport = McpTransport.Local(" ")), emptyList()))
        assertEquals("A tool's time is 5 to 1800 seconds", McpRules.problem(local.copy(toolTimeoutSeconds = 2), emptyList()))
        val token = McpValue("TOKEN")
        assertEquals("Enter a value for TOKEN", McpRules.problem(local.copy(transport = McpTransport.Local("x", env = listOf(token))), emptyList()))
        // A secret already saved needs no value again.
        assertNull(McpRules.problem(local.copy(transport = McpTransport.Local("x", env = listOf(token.copy(saved = true)))), emptyList()))
        assertEquals("TOKEN is named twice", McpRules.problem(local.copy(transport = McpTransport.Local("x", env = listOf(token.copy(value = "1"), token.copy(value = "2")))), emptyList()))
        val remote = McpServer("", "Remote", "", McpTransport.Remote("https://mcp.example.com/mcp", listOf(McpValue("Bad Header", "x"))))
        assertEquals("Bad Header is not a header name", McpRules.problem(remote, emptyList()))
    }

    @Test
    fun `a remote address is https, or http to this phone`() {
        assertNull(McpRules.remoteProblem("https://mcp.example.com/mcp"))
        assertNull(McpRules.remoteProblem("http://localhost:3001/mcp"))
        assertNull(McpRules.remoteProblem("http://127.0.0.1:3001/mcp"))
        assertEquals("Remote servers need https:// (http only to this phone)", McpRules.remoteProblem("http://mcp.example.com/mcp"))
        assertEquals("Enter an https:// address", McpRules.remoteProblem("mcp.example.com"))
    }

    @Test
    fun `a server's key is its name, short, lower case and its own`() {
        assertEquals("github", McpRules.key("GitHub", emptyList()))
        assertEquals("my_server", McpRules.key("My Server!", emptyList()))
        assertEquals("s2fa", McpRules.key("2FA", emptyList()))
        assertEquals("a_very_long_serv", McpRules.key("A very long server name", emptyList()))
        assertEquals("github_2", McpRules.key("GitHub", listOf("github")))
        assertEquals("github_3", McpRules.key("GitHub", listOf("github", "github_2")))
        assertEquals("server", McpRules.key("!!!", emptyList()))
    }

    @Test
    fun `a tool's name for the model is its server's key and its own, at most 64`() {
        assertEquals("github__create_issue", McpRules.toolName("github", "create_issue", emptySet()))
        assertEquals("github__a_b_c", McpRules.toolName("github", "a.b/c", emptySet()))
        val long = McpRules.toolName("github", "x".repeat(80), emptySet())
        assertEquals(McpRules.NAME_LIMIT, long.length)
        val clash = McpRules.toolName("github", "create_issue", setOf("github__create_issue"))
        assertTrue(clash.startsWith("github__create_issue_") && clash != "github__create_issue")
        // The same name is named the same each time.
        assertEquals(long, McpRules.toolName("github", "x".repeat(80), emptySet()))
    }

    @Test
    fun `a value's variables are filled, or it waits`() {
        assertEquals("Bearer abc", McpRules.expand("Bearer \${TOKEN}", mapOf("TOKEN" to "abc")))
        assertEquals("http://localhost:8080", McpRules.expand("\${HOST:-http://localhost:8080}", emptyMap()))
        assertEquals("plain", McpRules.expand("plain", emptyMap()))
        assertNull(McpRules.expand("\${MISSING}", emptyMap()))
    }

    @Test
    fun `a pasted value is never written out`() {
        assertEquals("ImportServers(12 characters)", McpIntent.ImportServers("{\"k\":\"key1\"}").toString())
        assertEquals("McpValue(TOKEN, secret, saved=false)", McpValue("TOKEN", "abc").toString())
        assertFalse(McpIntent.SignedIn("dev.icode.mobile:/mcp-sign-in?code=secret").toString().contains("secret"))
    }

    @Test
    fun `the tools on are counted across the servers that are on`() {
        val tools = listOf(McpToolInfo("a", null, ""), McpToolInfo("b", null, ""), McpToolInfo("c", null, "", problem = "unreadable"))
        val on = McpServer("1", "On", "on", McpTransport.Local("x"), tools = mapOf("b" to McpToolChoice.Off))
        val off = McpServer("2", "Off", "off", McpTransport.Local("y"), enabled = false)
        val state = McpState(listOf(on, off), mapOf("1" to McpServerState(McpStatus.Ready, tools = tools), "2" to McpServerState(McpStatus.Ready, tools = tools)))
        assertEquals(1, state.toolsOn)
    }

    @Test
    fun `a URL question shows its host before anything opens`() {
        assertEquals("auth.example.com", McpQuestion(1, "S", "Sign in", url = "https://auth.example.com/start?x=1").host)
        assertEquals("example.com:8443", McpQuestion(1, "S", "Go", url = "https://example.com:8443?y").host)
    }
}
