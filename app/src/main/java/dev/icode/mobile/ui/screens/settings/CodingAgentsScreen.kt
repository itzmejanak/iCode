package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.AgentIntent
import dev.icode.mobile.domain.AgentRules
import dev.icode.mobile.domain.CodingAgent
import dev.icode.mobile.domain.RuntimeToolsState
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › Coding Agents (docs/assistant-and-mcp.md): the external agents a chat can be
 * answered by, each installed into the runtime as a runtime tool and opened on its own page.
 */
@Composable
fun CodingAgentsScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val snapshot = LocalICodeSnapshot.current
    val agents = snapshot.agents
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Coding Agents", subtitle = "Other agents in the Assistant's chats", onBack = actions.back)
        IcodePageColumn(Modifier.weight(1f)) {
            Text(
                "A coding agent answers a chat in iCode's own Assistant page, through the Agent Client Protocol. " +
                    "Install one, then choose it in the chat's model sheet: new chats go to it.",
                style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
            )
            IcodeSection("Agents") {
                agents.agents.forEach { agent ->
                    AgentRow(agent, snapshot.runtimeTools, chosen = agent.id == agents.chosen, running = AgentRules.statusLabel(agents.status[agent.id])) {
                        gateway.dispatch(AgentIntent.Edit(agent.id))
                        actions.navigate(Route.CodingAgent)
                    }
                }
            }
            AgentReach()
        }
    }
}

/** An agent's row: its name and version, whether it is installed and whether new chats go to it. */
@Composable
private fun AgentRow(agent: CodingAgent, tools: RuntimeToolsState, chosen: Boolean, running: String, onOpen: () -> Unit) {
    val tool = RuntimeTools.byId(agent.tool)
    val installed = tool != null && tools.isInstalled(tool)
    val installing = tool != null && tools.installing(tool) != null
    IcodeListRow(
        card = true,
        selected = chosen,
        onClick = onOpen,
        leading = { IconBadge(Icons.Outlined.SmartToy, if (installed) IcodeColors.Accent else IcodeColors.TextMuted) },
        content = {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro)) {
                Text(agent.name, style = IcodeType.Body)
                MonoLine(listOfNotNull(agent.version, agent.licence, if (installed) running else null, if (chosen) "New chats" else null).joinToString(" · "), color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall, maxLines = 1)
            }
        },
        trailing = {
            StatusLabel(AgentRules.installLabel(installed, installing), installColor(installed, installing))
            Chevron()
        },
    )
}

@Composable
internal fun installColor(installed: Boolean, installing: Boolean): Color = when {
    installing -> IcodeColors.Amber
    installed -> IcodeColors.Green
    else -> IcodeColors.TextSecondary
}

/** What an agent reaches, said where it is installed and chosen (docs/assistant-and-mcp.md, SECURITY.md). */
@Composable
internal fun AgentReach(modifier: Modifier = Modifier) {
    NoticeCard(IcodeTone.Warning, modifier.fillMaxWidth(), title = "What an agent can reach", icon = Icons.Outlined.WarningAmber) {
        Text(
            "An agent is another vendor's program. It runs in iCode's Linux runtime with all of it in reach: every workspace, " +
                "the runtime's home, and phone storage once All Files Access is on. Its own permission settings decide what it asks " +
                "before it acts; iCode's do not apply. What you send it goes to its vendor, under that vendor's terms.",
            style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
        )
    }
}
