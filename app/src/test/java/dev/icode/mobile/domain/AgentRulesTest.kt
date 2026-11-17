package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How the page shows what a coding agent offers (docs/assistant-and-mcp.md). */
class AgentRulesTest {
    private val model = AgentOption.Select("model", "Model", category = "model", current = "s", choices = listOf(AgentOption.Select.Choice("s", "Sonnet")))
    private val mode = AgentOption.Select("mode", "Mode", category = "mode", current = "ask", choices = listOf(AgentOption.Select.Choice("ask", "Ask")))
    private val fast = AgentOption.Toggle("fast", "Fast mode", on = false)
    private val commands = listOf(AgentCommand("review", "Review"), AgentCommand("compact", "Compact"), AgentCommand("pr-review", "Review a PR"))

    @Test fun theMenuOpensOnOneSlashWord() {
        assertEquals("", AgentRules.slashQuery("/"))
        assertEquals("rev", AgentRules.slashQuery("/rev"))
        assertNull(AgentRules.slashQuery("/review this"))
        assertNull(AgentRules.slashQuery("review"))
    }

    @Test fun commandsStartingWithTheWordComeFirstThenSettingsNotShownAsChips() {
        val menu = AgentRules.menu("rev", commands, listOf(model, fast))
        assertEquals(listOf("review", "pr-review"), menu.filterIsInstance<AgentMenuItem.Command>().map { it.command.name })
        assertEquals(emptyList<AgentOption>(), menu.filterIsInstance<AgentMenuItem.Option>().map { it.option })
        assertEquals(listOf(AgentMenuItem.Option(fast)), AgentRules.menu("fast", commands, listOf(model, fast)))
    }

    @Test fun chipsAreTheModeModelAndEffortInThatOrder() {
        assertEquals(listOf(mode, model), AgentRules.chips(listOf(fast, model, mode)))
        assertEquals("Claude Agent · Sonnet", AgentRules.label(CodingAgent("c", "Claude Agent", "1", "", "MIT", null, null, "agent-c", false), listOf(model)))
    }

    @Test fun eachKindOfToolCallIsAReadAChangeOrACommand() {
        assertEquals(AssistantToolKind.Change, AgentRules.toolOf("edit").second)
        assertEquals(AssistantToolKind.Change, AgentRules.toolOf("delete").second)
        assertEquals(AssistantToolKind.Command, AgentRules.toolOf("execute").second)
        assertEquals(AssistantToolName.AgentTool to AssistantToolKind.Read, AgentRules.toolOf("something-new"))
    }

    @Test fun thePlanSaysHowFarItIs() {
        val plan = listOf(
            AgentPlanItem("Read", AgentPlanItem.Status.Completed),
            AgentPlanItem("Edit", AgentPlanItem.Status.InProgress),
            AgentPlanItem("Test", AgentPlanItem.Status.Pending),
        )
        assertEquals("1 of 3 done", AgentRules.planProgress(plan))
        assertEquals("Edit", AgentRules.planStep(plan)?.text)
    }

    @Test fun anAgentsKeysAreTheVariablesItsKeySignInNames() {
        val status = AgentStatus(signIn = listOf(AgentSignIn("key", "API key", kind = AgentSignIn.Kind.Keys, variables = listOf("OPENAI_API_KEY")), AgentSignIn("login", "Log in", kind = AgentSignIn.Kind.Agent)))
        assertEquals(listOf("OPENAI_API_KEY"), AgentRules.keyNames(status))
        assertEquals("key", AgentRules.keysMethod(status))
    }
}
