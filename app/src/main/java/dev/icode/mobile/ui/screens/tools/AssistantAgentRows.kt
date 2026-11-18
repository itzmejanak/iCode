package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpinner
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.AgentChatIntent
import dev.icode.mobile.domain.AgentFound
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.CodingAgents
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.agentMark

/**
 * The model sheet's Agents tab (docs/assistant-and-mcp.md): each agent iCode offers, under its mark — an
 * installed one answers the chat once chosen; one not installed opens Coding Agents, where it is.
 */
@Composable
internal fun AgentRows(state: AssistantState, onClose: () -> Unit, onAgents: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val agents = LocalICodeSnapshot.current.agents
    agents.catalogue.forEach { agent ->
        val installed = agent.id in agents.installed
        val chosen = state.answering == AssistantAgent(agent.id)
        IcodeListRow(
            title = agent.name,
            titleColor = if (chosen) IcodeColors.Accent else IcodeColors.TextPrimary,
            subtitle = if (installed) agent.description else "Not installed: tap to install",
            subtitleStyle = IcodeType.Caption,
            selected = chosen,
            leading = { Icon(agentMark(agent.id), contentDescription = null, tint = IcodeColors.TextPrimary, modifier = Modifier.size(IcodeSizes.IconMd)) },
            onClick = {
                if (installed) gateway.dispatch(AgentChatIntent.Choose(agent.id)) else onAgents()
                onClose()
            },
            trailing = {
                when {
                    chosen -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
                    agent.id !in CodingAgents.VERIFIED -> StatusLabel("Untested", IcodeColors.Amber)
                }
            },
        )
    }
    if (agents.catalogue.isEmpty()) {
        IcodeListRow(
            title = "Coding Agents",
            subtitle = "Install Claude, Codex, Gemini CLI and more",
            subtitleStyle = IcodeType.Caption,
            onClick = {
                onAgents()
                onClose()
            },
        )
    }
}

/**
 * An agent's conversation no chat here has, among the chats by date (docs/assistant-and-mcp.md): under the
 * agent's mark; a tap takes it up as a chat with all that was said, [opening] while it loads.
 */
@Composable
internal fun FoundAgentChatRow(found: AgentFound, opening: Boolean, enabled: Boolean, onTakeUp: () -> Unit) {
    val name = LocalICodeSnapshot.current.agents.catalogue.firstOrNull { it.id == found.agent }?.name ?: found.agent
    IcodeListRow(
        title = found.title,
        subtitle = if (opening) "Opening…" else listOfNotNull(name, found.updatedAt.takeIf { it > 0 }?.let(RelativeTime::since)).joinToString(" · "),
        subtitleStyle = IcodeType.Caption,
        leading = { Icon(agentMark(found.agent), contentDescription = name, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconMd)) },
        onClick = onTakeUp.takeIf { enabled },
        trailing = { if (opening) IcodeSpinner(color = IcodeColors.Accent) },
    )
}
