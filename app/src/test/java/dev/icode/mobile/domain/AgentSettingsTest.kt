package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The agent chat's settings: chips, the asking start, warnings, progress and the / list's commands. */
class AgentSettingsTest {
    @Test fun `mode, model and thinking are chips in that order, and switches and other choices are not`() {
        assertTrue(AgentSetting("mode", "Mode", "mode", "default", listOf(AgentValue("default", "Manual"))).chip)
        assertTrue(AgentSetting("effort", "Thinking", "thought_level", "high", listOf(AgentValue("high", "High"))).chip)
        assertEquals(false, AgentSetting("auto_approve", "Auto-approve tools", null, "false").chip)
        assertEquals(false, AgentSetting("provider", "Provider", null, "anthropic", listOf(AgentValue("anthropic", "Anthropic"))).chip)
        assertEquals(listOf("mode", "model", "thought_level"), AgentSettings.CHIPS)
    }

    @Test fun `every checked agent starts asking, and those that act unasked say so`() {
        assertEquals(mapOf("mode" to "default"), AgentSettings.ASKING["claude-acp"])
        assertEquals(mapOf("mode" to "read-only"), AgentSettings.ASKING["codex-acp"])
        assertEquals(mapOf("mode" to "default"), AgentSettings.ASKING["gemini"])
        assertEquals(mapOf("auto_approve" to "false"), AgentSettings.ASKING["cline"])
        assertEquals("OpenCode's Build asks where no permission is set", mapOf("mode" to "build"), AgentSettings.ASKING["opencode"])
        assertNull("Unchecked agents start as they start", AgentSettings.ASKING["factory-droid"])

        assertEquals("Changes files and runs commands without asking you", AgentSettings.warning("claude-acp", "mode", "bypassPermissions"))
        assertEquals("Decides itself what is safe, and may act without asking you", AgentSettings.warning("codex-acp", "mode", "agent"))
        assertEquals("Changes files without asking you", AgentSettings.warning("gemini", "mode", "autoEdit"))
        assertEquals("Changes files and runs commands without asking you", AgentSettings.warning("cline", "auto_approve", "true"))
        assertNull(AgentSettings.warning("claude-acp", "mode", "default"))
        assertNull(AgentSettings.warning("cline", "auto_approve", "false"))
        assertNull("OpenCode's own modes all ask by default", AgentSettings.warning("opencode", "mode", "plan"))
        assertNull("No warning until an agent is checked", AgentSettings.warning("factory-droid", "mode", "auto"))
    }

    @Test fun `progress counts what is done of the whole list, and there is none without a list`() {
        val plan = listOf(AgentPlanStep("a", AgentPlanStatus.Done), AgentPlanStep("b", AgentPlanStatus.Done), AgentPlanStep("c", AgentPlanStatus.Working), AgentPlanStep("d", AgentPlanStatus.Pending), AgentPlanStep("e", AgentPlanStatus.Pending))
        assertEquals("2/5", AgentSettings.progress(plan))
        assertNull(AgentSettings.progress(emptyList()))
    }

    @Test fun `the slash list shows the commands begun, until a space ends the name`() {
        val commands = listOf(AgentCommand("review", "Review"), AgentCommand("release", "Release"), AgentCommand("init", "Init"))
        assertEquals(commands, AgentSettings.commandsFor("/", commands))
        assertEquals(listOf("review", "release"), AgentSettings.commandsFor("/re", commands).map { it.name })
        assertEquals(listOf("review"), AgentSettings.commandsFor("/REV", commands).map { it.name })
        assertTrue(AgentSettings.commandsFor("/review now", commands).isEmpty())
        assertTrue(AgentSettings.commandsFor("review", commands).isEmpty())
    }
}
