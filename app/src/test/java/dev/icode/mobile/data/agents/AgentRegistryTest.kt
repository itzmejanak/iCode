package dev.icode.mobile.data.agents

import dev.icode.mobile.domain.AgentSource
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The ACP registry's list, read in its own format (FORMAT.md, github.com/agentclientprotocol/registry). */
class AgentRegistryTest {
    @get:Rule val temp = TemporaryFolder()

    private val sha = "969d747d0d662663e2aab56667be5819e2716890c2188e32672ac7ab4e47873f"

    /** As the registry publishes it, its entries trimmed: the offered agents out of order, one not offered, one missing. */
    private val list = """
        {"version": "1.0.0", "agents": [
          {"id": "factory-droid", "name": "Factory Droid", "version": "1.13.1", "description": "Factory Droid - AI coding agent",
           "website": "https://factory.ai/product/cli", "authors": ["Factory AI"], "license": "proprietary",
           "license_url": "https://factory.ai/legal/terms",
           "distribution": {"npx": {"package": "droid@1.13.1", "args": ["exec", "--output-format", "acp-daemon"],
             "env": {"DROID_DISABLE_AUTO_UPDATE": "true"}}}},
          {"id": "goose", "name": "goose", "version": "1.0.0", "description": "Not offered", "license_url": "https://example.com",
           "distribution": {"npx": {"package": "goose@1.0.0"}}},
          {"id": "claude-acp", "name": "Claude Agent", "version": "0.108.0", "description": "ACP wrapper for Anthropic's Claude",
           "repository": "https://github.com/agentclientprotocol/claude-agent-acp", "license": "proprietary",
           "license_url": "https://github.com/agentclientprotocol/claude-agent-acp/blob/main/LICENSE",
           "distribution": {
             "binary": {
               "darwin-aarch64": {"archive": "https://example.com/mac.zip", "cmd": "./claude-agent-acp", "sha256": "$sha"},
               "linux-aarch64": {"archive": "https://github.com/agentclientprotocol/claude-agent-acp/releases/download/v0.108.0/claude-agent-acp-linux-arm64.tar.gz",
                                 "cmd": "./claude-agent-acp", "sha256": "${sha.uppercase()}"}},
             "npx": {"package": "@agentclientprotocol/claude-agent-acp@0.108.0"}}},
          {"id": "codex-acp", "name": "Codex", "version": "2.7.0", "description": "ACP adapter for OpenAI's coding assistant",
           "repository": "https://github.com/agentclientprotocol/codex-acp", "license": "Apache-2.0",
           "license_url": "https://github.com/agentclientprotocol/codex-acp/blob/main/LICENSE",
           "distribution": {"npx": {"package": "@agentclientprotocol/codex-acp@2.7.0"}}},
          {"id": "opencode", "name": "OpenCode", "version": "2.3.0", "description": "The open source coding agent", "website": "https://opencode.ai",
           "license": "MIT", "license_url": "https://github.com/anomalyco/opencode/blob/dev/LICENSE",
           "distribution": {"binary": {"linux-aarch64": {"archive": "https://example.com/opencode-linux-arm64.tar.gz", "cmd": "./opencode", "args": ["acp"]}}}},
          {"id": "gemini", "name": "Gemini CLI", "version": "0.68.0", "description": "Google's official CLI for Gemini", "website": "https://geminicli.com",
           "license": "Apache-2.0", "license_url": "https://github.com/google-gemini/gemini-cli/blob/main/LICENSE",
           "distribution": {"npx": {"package": "@google/gemini-cli", "args": ["--acp"]}}}
        ]}
    """.trimIndent()

    @Test fun `the offered agents are read in the page's order, the others and the missing left out`() {
        assertEquals(listOf("claude-acp", "codex-acp", "gemini", "opencode", "factory-droid"), AgentRegistry.parse(list).map { it.id })
    }

    @Test fun `the phone's verified build comes first, its digest as lowercase hex`() {
        val claude = AgentRegistry.parse(list).first { it.id == "claude-acp" }
        assertEquals(
            AgentSource.Binary(
                "https://github.com/agentclientprotocol/claude-agent-acp/releases/download/v0.108.0/claude-agent-acp-linux-arm64.tar.gz",
                sha, "./claude-agent-acp", emptyList(), emptyMap(),
            ),
            claude.source,
        )
        assertEquals("https://github.com/agentclientprotocol/claude-agent-acp", claude.website)
    }

    @Test fun `an npm package installs at its exact version, scoped or not, with its arguments and variables`() {
        val agents = AgentRegistry.parse(list).associateBy { it.id }
        assertEquals(AgentSource.Npm("@agentclientprotocol/codex-acp", "2.7.0", emptyList(), emptyMap()), agents.getValue("codex-acp").source)
        assertEquals(
            AgentSource.Npm("droid", "1.13.1", listOf("exec", "--output-format", "acp-daemon"), mapOf("DROID_DISABLE_AUTO_UPDATE" to "true")),
            agents.getValue("factory-droid").source,
        )
    }

    @Test fun `a build without its digest, or a package without its version, is never installed`() {
        val agents = AgentRegistry.parse(list).associateBy { it.id }
        assertNull(agents.getValue("opencode").source)
        assertNull(agents.getValue("gemini").source)
    }

    @Test fun `the list read is kept, and kept when the registry cannot be reached`() {
        val kept = File(temp.root, "agents/registry.json")
        var answer: () -> String = { list }
        val registry = AgentRegistry(kept, download = { answer() }, measure = { 48_000_000L })
        assertEquals(emptyList<Any>(), registry.kept())
        assertEquals(5, registry.read().size)
        answer = { throw IOException("No network") }
        assertThrows(IOException::class.java) { registry.read() }
        assertEquals(5, registry.kept().size)
        assertEquals(48_000_000L, registry.size(registry.kept().first { it.id == "claude-acp" }))
        assertNull("an npm package's download is not known before npm installs it", registry.size(registry.kept().first { it.id == "codex-acp" }))
    }
}
