package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.model.SessionUpdate
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.domain.AgentPlanStep
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules

/**
 * A conversation [agent] replays as it loads a session, as a chat keeps it (docs/assistant-and-mcp.md):
 * the user's words as messages, and the agent's replies and steps as [AcpReplies] keeps a turn's. What
 * the session cost is left out: a replay says only what it costs in all. [relative] gives a path the
 * agent names relative to the chat's place, or null outside it.
 */
internal class AcpReplay(private val agent: String, relative: (String) -> String?) : AssistantEvents {
    private var messages = emptyList<AssistantMessage>()
    private val replies = AcpReplies(this, relative)
    private val user = StringBuilder()

    @Synchronized fun on(update: SessionUpdate) {
        when (update) {
            is SessionUpdate.UserMessageChunk -> {
                // The user speaks: the agent's reply before it has ended.
                if (user.isEmpty()) replies.end()
                user.append(AcpReplies.textOf(update.content))
            }
            is SessionUpdate.UsageUpdate -> Unit
            else -> {
                said()
                replies.on(update)
            }
        }
    }

    /** The conversation, once the agent has replayed all of it. */
    @Synchronized fun messages(): List<AssistantMessage> {
        replies.end()
        said()
        return messages
    }

    private fun said() {
        if (user.isEmpty()) return
        messages = messages + AssistantMessage.User(user.toString())
        user.clear()
    }

    override fun writing(reasoning: String, text: String) = Unit

    override fun replied(reply: AssistantMessage.Reply) {
        messages = messages + reply.copy(by = AssistantAgent(agent))
    }

    override fun calling(call: AssistantCall) = Unit

    override fun answered(result: AssistantResult) {
        messages = AssistantRules.answered(messages, result)
    }

    override fun used(tokens: Long) = Unit

    override fun planned(steps: List<AgentPlanStep>) = Unit
}
