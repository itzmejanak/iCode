package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Each agent's own program in a terminal, as its published release has it. */
class AgentProgramsTest {
    @Test fun `an adapter's program lies beside it and takes up the chat's session by its ID`() {
        val claude = listOf("/root/.local/share/icode/agents/claude-acp/0.108.0/claude-agent-acp")
        assertEquals(listOf("/root/.local/share/icode/agents/claude-acp/0.108.0/claude", "--resume", "s-1"), AgentPrograms.command("claude-acp", claude, "s-1"))
        assertEquals(listOf("/root/.local/share/icode/agents/claude-acp/0.108.0/claude"), AgentPrograms.command("claude-acp", claude, null))
        val codex = listOf("/root/.local/share/icode/agents/codex-acp/2.7.0/node_modules/.bin/codex-acp")
        assertEquals(listOf("/root/.local/share/icode/agents/codex-acp/2.7.0/node_modules/.bin/codex", "resume", "t-1"), AgentPrograms.command("codex-acp", codex, "t-1"))
    }

    @Test fun `an agent that is its own program opens without its ACP arguments, afresh`() {
        assertEquals(listOf("/a/node_modules/.bin/gemini"), AgentPrograms.command("gemini", listOf("/a/node_modules/.bin/gemini", "--acp"), "s-1"))
        assertEquals(listOf("/a/node_modules/cline/bin/cline"), AgentPrograms.command("cline", listOf("/a/node_modules/cline/bin/cline", "--acp"), null))
        assertEquals(listOf("/a/opencode"), AgentPrograms.command("opencode", listOf("/a/opencode", "acp"), null))
        assertEquals(listOf("/a/node_modules/.bin/droid"), AgentPrograms.command("factory-droid", listOf("/a/node_modules/.bin/droid", "exec", "--output-format", "acp-daemon"), null))
        assertEquals(false, AgentPrograms.resumes("gemini"))
    }

    @Test fun `an agent iCode does not know has no program to open`() {
        assertNull(AgentPrograms.command("someone-else", listOf("/a/agent"), null))
    }
}
