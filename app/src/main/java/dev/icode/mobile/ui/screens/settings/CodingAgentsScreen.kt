package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeActionSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeLoadingLine
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.AgentIntent
import dev.icode.mobile.domain.CodingAgent
import dev.icode.mobile.domain.CodingAgents
import dev.icode.mobile.domain.CodingAgentsState
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.SheetIcon
import dev.icode.mobile.ui.agentMark

/**
 * Settings › Assistant › Coding agents (docs/assistant-and-mcp.md): a row for each agent iCode offers —
 * its mark, name and state, Untested until verified on phones — with Install or Update and its work's
 * progress, and Update All when several have updates. A row opens the agent's sheet; Remove there asks
 * whether its sign-in and settings stay. Opening the page reads the registry again.
 */
@Composable
fun CodingAgentsScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val agents = LocalICodeSnapshot.current.agents
    LaunchedEffect(Unit) { gateway.dispatch(AgentIntent.Check) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by rememberSaveable { mutableStateOf(false) }
    val shown = agents.catalogue.firstOrNull { it.id == open }
    val close = {
        open = null
        removing = false
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(shown != null)) {
            IcodeTopBar("Coding Agents", subtitle = subtitle(agents), onBack = actions.back)
            IcodePageColumn(Modifier.weight(1f)) {
                when {
                    agents.catalogue.isNotEmpty() -> AgentList(agents) { open = it }
                    agents.checking -> IcodeLoadingLine("Reading the agents list")
                    else -> IcodeSection {
                        EmptyState(
                            "Couldn't read the agents list",
                            Modifier.padding(vertical = IcodeSpacing.Space6),
                            description = agents.checkFailure,
                            icon = Icons.Outlined.SmartToy,
                        )
                        PrimaryButton("Try Again", { gateway.dispatch(AgentIntent.Check) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        shown?.let { agent ->
            IcodeSheetOverlay(onClose = close) {
                val modifier = Modifier.align(Alignment.BottomCenter)
                if (removing) RemoveSheet(agent, modifier, close) else AgentSheet(agent, agents, modifier, onRemove = { removing = true }, onClose = close)
            }
        }
    }
}

private fun subtitle(agents: CodingAgentsState): String {
    if (agents.installed.isEmpty()) return "Agents that answer in your chats"
    val updates = agents.updates.size
    return "${agents.installed.size} installed" + when (updates) {
        0 -> ""
        1 -> " · 1 update"
        else -> " · $updates updates"
    }
}

@Composable
private fun AgentList(agents: CodingAgentsState, onOpen: (String) -> Unit) {
    val gateway = LocalICodeGateway.current
    agents.checkFailure?.let { Text("Couldn't check for updates: $it", style = IcodeType.Caption.copy(color = IcodeColors.Amber)) }
    if (agents.updates.size > 1) PrimaryButton("Update All", { gateway.dispatch(AgentIntent.UpdateAll) }, Modifier.fillMaxWidth())
    IcodeSection("Agents") { agents.catalogue.forEach { agent -> AgentRow(agent, agents) { onOpen(agent.id) } } }
}

@Composable
private fun AgentRow(agent: CodingAgent, agents: CodingAgentsState, onOpen: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val installed = agents.installed[agent.id]
    val work = agents.work[agent.id]
    IcodeListRow(
        card = true,
        onClick = onOpen,
        leading = { IconBadge(agentMark(agent.id), IcodeColors.TextPrimary) },
        content = {
            Column(Modifier.weight(1f)) {
                Text(agent.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                if (work == null) {
                    Text(CodingAgents.status(agent, installed, agents.sizes[agent.id]), style = IcodeType.Caption, maxLines = 1)
                    if (agent.id !in CodingAgents.VERIFIED) StatusLabel("Untested", IcodeColors.Amber)
                } else {
                    Text(listOfNotNull(work.progress.step, work.progress.item).joinToString(" · "), style = IcodeType.Caption, maxLines = 1)
                    ProgressTrack(work.progress.fraction, Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space1))
                }
            }
        },
        trailing = {
            when {
                work != null -> if (work.progress.cancellable) IcodeTextButton("Cancel", { gateway.dispatch(AgentIntent.Cancel(agent.id)) })
                agent.source == null -> Unit
                installed == null -> SecondaryButton("Install", { gateway.dispatch(AgentIntent.Install(agent.id)) }, compact = true)
                installed.version != agent.version -> PrimaryButton("Update", { gateway.dispatch(AgentIntent.Install(agent.id)) }, compact = true)
            }
        },
    )
}

/** An agent's sheet: Install or Update, its website and licence, and Remove. */
@Composable
private fun AgentSheet(agent: CodingAgent, agents: CodingAgentsState, modifier: Modifier, onRemove: () -> Unit, onClose: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val uri = LocalUriHandler.current
    val installed = agents.installed[agent.id]
    val idle = agent.id !in agents.work
    val install = { gateway.dispatch(AgentIntent.Install(agent.id)).also { onClose() } }
    IcodeActionSheet(
        title = agent.name,
        detail = agent.description,
        actions = listOfNotNull(
            IcodeMenuAction("Install ${agent.version}", Icons.Outlined.Download, install).takeIf { idle && installed == null && agent.source != null },
            IcodeMenuAction("Update to ${agent.version}", Icons.Outlined.Download, install).takeIf { idle && installed != null && installed.version != agent.version },
            agent.website?.let { site -> IcodeMenuAction("Website", Icons.Outlined.OpenInNew, { uri.openUri(site) }) },
            IcodeMenuAction("Licence", Icons.Outlined.Description, { uri.openUri(agent.licenseUrl) }, detail = agent.license.ifEmpty { null }),
            IcodeMenuAction("Remove", Icons.Outlined.Delete, onRemove, destructive = true, opensPage = true).takeIf { idle && installed != null },
        ),
        onClose = onClose,
        modifier = modifier,
        leading = { SheetIcon(agentMark(agent.id), IcodeColors.TextPrimary) },
    )
}

/** Remove, asked each time (owner's choice, 2026-11-18): its sign-in and settings kept for a reinstall, or everything gone. */
@Composable
private fun RemoveSheet(agent: CodingAgent, modifier: Modifier, onClose: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val remove = { everything: Boolean ->
        gateway.dispatch(AgentIntent.Remove(agent.id, everything))
        onClose()
    }
    IcodeActionSheet(
        title = "Remove ${agent.name}",
        actions = listOf(
            IcodeMenuAction("Keep sign-in and settings", Icons.Outlined.Delete, { remove(false) }, detail = "Installing it again finds them"),
            IcodeMenuAction(
                "Delete everything",
                Icons.Outlined.DeleteForever,
                { remove(true) },
                destructive = true,
                detail = "Also " + CodingAgents.OWN[agent.id].orEmpty().joinToString(", ") { "~/$it" },
            ),
        ),
        onClose = onClose,
        modifier = modifier,
        leading = { SheetIcon(agentMark(agent.id), IcodeColors.TextPrimary) },
    )
}
