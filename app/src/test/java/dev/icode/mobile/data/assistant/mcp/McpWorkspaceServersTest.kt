package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.InMemoryPreferences
import dev.icode.mobile.domain.McpTransport
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** docs/assistant-and-mcp.md: a workspace's `.mcp.json`, trusted as it is, its variables filled. */
class McpWorkspaceServersTest {
    private val workspace = Files.createTempDirectory("workspace").toFile()
    private val store = McpServerStore(InMemoryPreferences(), { "sealed:$it" }, { it.removePrefix("sealed:") })
    private var variables = mapOf("GITHUB_TOKEN" to "from-login", "HOME" to "/root")
    private val servers = McpWorkspaceServers(store, { "/workspace/${File(it).name}" }) { variables }

    private fun write(json: String) = File(workspace, ".mcp.json").writeText(json)

    @After
    fun clean() {
        workspace.deleteRecursively()
    }

    @Test
    fun `its servers are named for the workspace and keyed apart from the user's`() {
        write("""{"mcpServers": {"github": {"command": "npx", "args": ["-y", "@github/mcp"], "env": {"TOKEN": "${'$'}{GITHUB_TOKEN}"}}}}""")
        val server = servers.of(workspace.path, listOf("github")).single()
        assertEquals("${workspace.path}#github", server.id)
        assertEquals("github_2", server.key)
        assertEquals(workspace.path, server.workspace)
        // Its values are never in what is shown.
        assertNull((server.transport as McpTransport.Local).env.single().value)
    }

    @Test
    fun `none without a file, or with one that is not a configuration`() {
        assertTrue(servers.of(workspace.path, emptyList()).isEmpty())
        write("not json")
        assertTrue(servers.of(workspace.path, emptyList()).isEmpty())
    }

    @Test
    fun `it is trusted as its file says it, and a change asks again`() {
        write("""{"mcpServers": {"tool": {"command": "node", "args": ["server.js"]}}}""")
        val server = servers.of(workspace.path, emptyList()).single()
        assertFalse(servers.trusted(server))
        store.trust(server.id, servers.fingerprint(server))
        assertTrue(servers.trusted(server))
        write("""{"mcpServers": {"tool": {"command": "node", "args": ["other.js"]}}}""")
        assertFalse(servers.trusted(servers.of(workspace.path, emptyList()).single()))
        // A changed value is a changed configuration too.
        write("""{"mcpServers": {"tool": {"command": "node", "args": ["server.js"], "env": {"NODE_OPTIONS": "--require ./x.js"}}}}""")
        assertFalse(servers.trusted(servers.of(workspace.path, emptyList()).single()))
    }

    @Test
    fun `it starts with its variables filled, the user's own value first, in the workspace`() = runBlocking {
        write("""{"mcpServers": {"github": {"command": "npx", "args": ["${'$'}{HOME}/bin/gh"], "env": {"TOKEN": "${'$'}{GITHUB_TOKEN}", "PLAIN": "1", "UNSET": "${'$'}{NOPE}"}}}}""")
        val server = servers.of(workspace.path, emptyList()).single()
        val (ready, values) = servers.resolve(server, store.reveal(server))
        val local = ready.transport as McpTransport.Local
        assertEquals(listOf("/root/bin/gh"), local.args)
        assertEquals("/workspace/${workspace.name}", local.folder)
        assertEquals(mapOf("TOKEN" to "from-login", "PLAIN" to "1", "UNSET" to null), values.associate { it.name to it.value })
        // A value the user kept in iCode comes before the login environment's.
        store.save(server.copy(transport = local.copy(env = local.env.map { if (it.name == "TOKEN") it.copy(value = "kept") else it })))
        val (_, again) = servers.resolve(server, store.reveal(server))
        assertEquals("kept", again.single { it.name == "TOKEN" }.value)
    }

    @Test
    fun `a command naming a variable not set does not start`() = runBlocking {
        write("""{"mcpServers": {"x": {"command": "${'$'}{NOPE}/run"}}}""")
        val server = servers.of(workspace.path, emptyList()).single()
        try {
            servers.resolve(server, store.reveal(server))
            fail("Its command cannot be known")
        } catch (refused: IllegalArgumentException) {
            assertEquals("x names a variable your login environment does not set", refused.message)
        }
    }
}
