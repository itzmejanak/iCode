package dev.icode.mobile.domain

/** What the user does with the coding agents (docs/assistant-and-mcp.md): the assistant's intents. */
sealed interface AgentIntent : ICodeIntent.AssistantIntent {
    /** New chats go to [agent]; null for the built-in assistant. A chat shown with another engine's messages gives way to a new one. */
    data class Choose(val agent: String?) : AgentIntent

    /** [agent]'s settings page opened; null when it closed. */
    data class Edit(val agent: String?) : AgentIntent

    /** The shown chat's [option] set to [value], one of its choices. */
    data class SetOption(val option: String, val value: String) : AgentIntent

    /** The shown chat's [option] turned on or off. */
    data class SetToggle(val option: String, val on: Boolean) : AgentIntent

    /** [agent] — the shown chat's when null — signs in with [method]. */
    data class SignIn(val method: String, val agent: String? = null) : AgentIntent

    /** A sign-in done in the terminal tab: [agent] — the shown chat's when null — starts again, signed in. */
    data class SignedIn(val agent: String? = null) : AgentIntent

    /** The link the agent asked to open has been opened, or set aside. */
    data object LinkTaken : AgentIntent

    /** [agent]'s keys saved, encrypted; a value of null keeps the one saved. */
    data class SaveKeys(val agent: String, val values: List<McpValue>) : AgentIntent {
        override fun toString() = "SaveKeys($agent, ${values.map { it.name }})"
    }

    /** The agent's own CLI, in a terminal tab: [agent]'s from its settings, else the shown chat's, resuming it where it can. */
    data class OpenInTerminal(val agent: String? = null) : AgentIntent

    /** The answer to the question [AgentsState.question] asks. */
    data class Answer(val answer: McpAnswer) : AgentIntent {
        override fun toString() = "Answer(${answer.id}, ${answer.action})"
    }

    /** The approval of [callId] answered with the agent's own [choice]. */
    data class AnswerApproval(val callId: String, val choice: String) : AgentIntent

    /** [agent]'s log read again. */
    data class ReadLog(val agent: String) : AgentIntent

    /** [agent]'s process stopped; it starts again when it is next needed. */
    data class Stop(val agent: String) : AgentIntent
}
