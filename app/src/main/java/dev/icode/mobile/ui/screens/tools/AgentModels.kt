package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.AgentIntent
import dev.icode.mobile.domain.AgentsState
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.RuntimeToolsState
import dev.icode.mobile.ui.LocalICodeGateway

/**
 * The coding agents installed, in the chat's model sheet beside the providers (docs/assistant-and-mcp.md):
 * one chosen takes new chats; a chat answered by something else gives way to a new one. [current] is
 * the agent answering the shown chat, or the one a new chat would go to.
 */
@Composable
internal fun AgentModelRows(agents: AgentsState, tools: RuntimeToolsState, current: String?, onClose: () -> Unit, onManage: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val installed = agents.agents.filter { agent -> RuntimeTools.byId(agent.tool)?.let(tools::isInstalled) == true }
    SectionLabel("Coding Agents")
    installed.forEach { agent ->
        val chosen = agent.id == current
        IcodeListRow(
            title = agent.name,
            titleColor = if (chosen) IcodeColors.Accent else IcodeColors.TextPrimary,
            subtitle = "${agent.version} · ${agent.licence}",
            subtitleStyle = IcodeType.Caption,
            selected = chosen,
            onClick = {
                gateway.dispatch(AgentIntent.Choose(agent.id))
                onClose()
            },
            trailing = { if (chosen) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd)) },
        )
    }
    if (installed.isEmpty()) Hint("No coding agent installed yet: Claude Code, Codex, Gemini CLI and more")
    SecondaryButton(
        "Manage Coding Agents",
        {
            onClose()
            onManage()
        },
        Modifier.fillMaxWidth(),
        compact = true,
    )
}
