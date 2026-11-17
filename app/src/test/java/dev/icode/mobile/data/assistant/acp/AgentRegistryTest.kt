package dev.icode.mobile.data.assistant.acp

import dev.icode.mobile.domain.CodingAgents
import dev.icode.mobile.domain.RuntimeTools
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pinned agent list (docs/assistant-and-mcp.md): the registry's own format, read by the one parser. */
class AgentRegistryTest {
    private val pinned = AgentRegistry.listed(AgentRegistry.parse(File("src/main/assets/${AgentRegistry.ASSET}").readText()))

    @Test fun everyListedAgentIsPinnedAtItsVersion() {
        assertEquals(CodingAgents.listed.map { it.id to it.version }, pinned.map { it.agent.id to it.agent.version })
        pinned.forEach { entry ->
            val listed = CodingAgents.byId(entry.agent.id)!!
            assertEquals(entry.agent.id, listed.npm, entry.distribution is AgentDistribution.Npm)
            assertEquals(listed.tool, entry.agent.tool)
            // Each is a runtime tool, installed and removed as the others are; an npm one needs Node.js.
            val tool = RuntimeTools.byId(entry.agent.tool)!!
            assertEquals(listed.npm, RuntimeTools.NodeJs.id in tool.requires)
            assertTrue(entry.agent.terms!!.startsWith("https://"))
        }
    }

    @Test fun binariesArePinnedByChecksumAndPackagesByExactVersion() {
        val claude = pinned.single { it.agent.id == "claude-acp" }.distribution as AgentDistribution.Binary
        assertTrue(claude.archive.endsWith("claude-agent-acp-linux-arm64.tar.gz"))
        assertEquals("851570c0df0281aef72e59135d1938921a0eb12155034927ea9101e2d3636924", claude.sha256)
        val opencode = pinned.single { it.agent.id == "opencode" }.distribution as AgentDistribution.Binary
        assertEquals(listOf("acp"), opencode.args)
        val codex = pinned.single { it.agent.id == "codex-acp" }.distribution as AgentDistribution.Npm
        assertEquals("@agentclientprotocol/codex-acp@2.7.0", codex.spec)
        val droid = pinned.single { it.agent.id == "factory-droid" }.distribution as AgentDistribution.Npm
        assertEquals(listOf("exec", "--output-format", "acp-daemon"), droid.args)
        assertEquals("true", droid.env["DROID_DISABLE_AUTO_UPDATE"])
        assertEquals("proprietary", pinned.single { it.agent.id == "claude-acp" }.agent.licence)
    }

    @Test fun anAgentIcodeCannotRunPinnedIsLeftOut() {
        val parsed = AgentRegistry.parse(
            """
            {"version": "1.0.0", "agents": [
              {"id": "mac-only", "name": "Mac", "version": "1.0.0", "distribution": {"binary": {"darwin-aarch64": {"archive": "https://x/a.zip", "cmd": "./a"}}}},
              {"id": "floating", "name": "Floating", "version": "1.0.0", "distribution": {"npx": {"package": "floating-agent"}}},
              {"id": "unchecked", "name": "Unchecked", "version": "1.0.0", "distribution": {"binary": {"linux-aarch64": {"archive": "https://x/a.tar.gz", "cmd": "./a"}}}},
              {"id": "scoped", "name": "Scoped", "version": "2.0.0", "distribution": {"npx": {"package": "@scope/agent@2.0.0", "args": ["--acp"]}}}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("scoped"), parsed.map { it.agent.id })
        val scoped = parsed.single().distribution as AgentDistribution.Npm
        assertEquals("@scope/agent", scoped.name)
        assertEquals("2.0.0", scoped.version)
        assertNull(AgentRegistry.listed(parsed).firstOrNull())
    }
}
