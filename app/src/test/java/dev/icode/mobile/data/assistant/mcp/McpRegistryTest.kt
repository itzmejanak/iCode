package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.McpValue
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** docs/assistant-and-mcp.md: `server.json` as the registry's documentation publishes it, as iCode would add it. */
class McpRegistryTest {
    @Test
    fun `a PyPI package runs by uvx, its values to fill`() {
        val entry = McpRegistry.entry(
            JSONObject(
                """{"name": "io.github.example/weather-mcp", "description": "Weather", "version": "0.5.0",
                "packages": [{"registryType": "pypi", "registryBaseUrl": "https://pypi.org", "identifier": "weather-mcp-server", "version": "0.5.0",
                  "runtimeHint": "uvx", "transport": {"type": "stdio"},
                  "environmentVariables": [
                    {"name": "WEATHER_API_KEY", "description": "API key for weather service", "isRequired": true, "isSecret": true},
                    {"name": "WEATHER_UNITS", "description": "Temperature units (celsius, fahrenheit)", "default": "celsius"}
                  ]}]}""",
            ),
        )!!
        assertEquals("weather-mcp", entry.server.name)
        val local = entry.server.transport as McpTransport.Local
        assertEquals("uvx", local.command)
        assertEquals(listOf("weather-mcp-server==0.5.0"), local.args)
        assertEquals(
            listOf(
                McpValue("WEATHER_API_KEY", null, secret = true, hint = "API key for weather service"),
                McpValue("WEATHER_UNITS", "celsius", secret = false),
            ),
            local.env,
        )
    }

    @Test
    fun `a remote comes first, and an npm package runs by npx`() {
        val detail = """{"name": "io.modelcontextprotocol.anonymous/hybrid-mcp", "title": "Hybrid", "description": "Both", "version": "1.5.0",
            "packages": [{"registryType": "npm", "identifier": "@example/hybrid-mcp-server", "version": "1.5.0", "runtimeHint": "npx", "transport": {"type": "stdio"},
              "packageArguments": [{"type": "named", "name": "--mode", "description": "Operation mode", "default": "local"}]}],
            "remotes": [{"type": "streamable-http", "url": "https://mcp.anonymous.modelcontextprotocol.io/http",
              "headers": [{"name": "X-API-Key", "description": "API key for authentication", "isRequired": true, "isSecret": true},
                          {"name": "X-Region", "description": "Service region", "default": "us-east-1"}]}]}"""
        val entry = McpRegistry.entry(JSONObject(detail))!!
        assertEquals("Hybrid", entry.server.name)
        val remote = entry.server.transport as McpTransport.Remote
        assertEquals("https://mcp.anonymous.modelcontextprotocol.io/http", remote.url)
        assertEquals(listOf("X-API-Key", "X-Region"), remote.headers.map { it.name })
        assertEquals("us-east-1", remote.headers[1].value)
        val local = McpRegistry.entry(JSONObject(detail).apply { remove("remotes") })!!.server.transport as McpTransport.Local
        assertEquals("npx", local.command)
        assertEquals(listOf("-y", "@example/hybrid-mcp-server@1.5.0", "--mode=local"), local.args)
    }

    @Test
    fun `a template is filled from its variables, and one the user must give stays for them`() {
        val entry = McpRegistry.entry(
            JSONObject(
                """{"name": "a/b", "description": "", "version": "1",
                "remotes": [{"type": "sse", "url": "https://{tenant}.example.com/{region}/sse",
                  "variables": {"tenant": {"isRequired": true}, "region": {"default": "eu"}}}],
                "packages": []}""",
            ),
        )!!
        assertEquals("https://{tenant}.example.com/eu/sse", (entry.server.transport as McpTransport.Remote).url)
    }

    @Test
    fun `a server iCode cannot run is not offered`() {
        assertNull(
            McpRegistry.entry(
                JSONObject("""{"name": "io.github.example/fs", "description": "", "version": "1.0.2", "packages": [{"registryType": "oci", "identifier": "docker.io/mcp/filesystem:1.0.2", "transport": {"type": "stdio"}}]}"""),
            ),
        )
    }

    @Test
    fun `a listing keeps what is active`() {
        val listing = JSONObject(
            """{"servers": [
                {"server": {"name": "a/one", "description": "", "version": "1", "remotes": [{"type": "streamable-http", "url": "https://one.example/mcp"}]},
                 "_meta": {"io.modelcontextprotocol.registry/official": {"status": "active", "isLatest": true}}},
                {"server": {"name": "a/two", "description": "", "version": "1", "remotes": [{"type": "streamable-http", "url": "https://two.example/mcp"}]},
                 "_meta": {"io.modelcontextprotocol.registry/official": {"status": "deprecated", "isLatest": true}}}
            ], "metadata": {"count": 2}}""",
        )
        assertEquals(listOf("a/one"), McpRegistry.entries(listing).map { it.name })
    }
}
