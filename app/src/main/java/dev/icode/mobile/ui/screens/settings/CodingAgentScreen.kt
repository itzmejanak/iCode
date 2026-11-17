package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.AgentIntent
import dev.icode.mobile.domain.AgentRules
import dev.icode.mobile.domain.AgentSignIn
import dev.icode.mobile.domain.AgentStatus
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.McpValue
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.RuntimeToolsState
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * One coding agent's page (docs/assistant-and-mcp.md): what it is and its licence; installing, updating
 * and removing it, as a runtime tool; new chats going to it; its process, its sign-in and keys; its
 * own CLI in a terminal tab; and the end of its log.
 */
@Composable
fun CodingAgentScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val snapshot = LocalICodeSnapshot.current
    val agents = snapshot.agents
    // The agent opened from the list stays this page's, through Runtime Tools and the terminal and back.
    val agent = agents.agent(agents.editing) ?: return Box(Modifier.fillMaxSize())
    val tools = snapshot.runtimeTools
    val tool = RuntimeTools.byId(agent.tool)
    val installed = tool != null && tools.isInstalled(tool)
    val work = tool?.let(tools::installing)
    val failure = tools.work?.takeIf { tool != null && it.includes(tool) }?.failure
    val status = agents.status[agent.id]
    var removing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(removing)) {
            IcodeTopBar(agent.name, subtitle = "${agent.version} · ${agent.licence}", onBack = actions.back)
            IcodePageColumn(Modifier.weight(1f)) {
                IcodeSection("About") {
                    Text(agent.description, style = IcodeType.Body)
                    MonoLine(if (agent.npm) "npm package · runs on Node.js" else "ARM64 Linux binary", color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall)
                    agent.terms?.let { terms -> SecondaryButton("Licence and Terms", { gateway.dispatch(ICodeIntent.OpenExternalUrl(terms)) }, compact = true) }
                }
                IcodeSection("Install") {
                    StatusLabel(AgentRules.installLabel(installed, work != null), installColor(installed, work != null))
                    work?.let {
                        ProgressTrack(it.fraction, Modifier.fillMaxWidth())
                        Text(it.step, style = IcodeType.Caption.copy(color = IcodeColors.Accent))
                    }
                    failure?.let { MonoLine(it, color = IcodeColors.Error, style = IcodeType.MonoSmall, maxLines = 6) }
                    if (agent.npm) Text("Installs with npm, and adds Node.js when it is not installed.", style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
                    Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                        if (!installed) PrimaryButton("Install", { gateway.dispatch(ICodeIntent.ApplyRuntimeTools(installedTools(tools) + agent.tool)) }, busy = work != null, compact = true)
                        else DangerButton("Remove", { removing = true })
                        SecondaryButton("Runtime Tools", { actions.navigate(Route.RuntimeTools) }, compact = true)
                    }
                }
                SettingRow("Use for New Chats", Modifier.fillMaxWidth(), subtitle = "New chats in the Assistant go to ${agent.name}") {
                    IcodeToggle(agents.chosen == agent.id) { on -> gateway.dispatch(AgentIntent.Choose(if (on) agent.id else null)) }
                }
                if (installed) {
                    IcodeSection("Process") {
                        StatusLabel(AgentRules.statusLabel(status), if (status?.state == AgentStatus.State.Failed) IcodeColors.Error else IcodeColors.TextSecondary)
                        status?.identity?.let { MonoLine(it, color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall) }
                        status?.failure?.let { MonoLine(it, color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall, maxLines = 12) }
                        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                            SecondaryButton("Open in Terminal", {
                                gateway.dispatch(AgentIntent.OpenInTerminal(agent.id))
                                actions.navigate(Route.TerminalExpanded)
                            }, compact = true)
                            if (status?.state == AgentStatus.State.Ready) SecondaryButton("Stop", { gateway.dispatch(AgentIntent.Stop(agent.id)) }, compact = true)
                        }
                    }
                    SignIn(agent.id, status, agents.keys[agent.id].orEmpty()) { actions.navigate(Route.TerminalExpanded) }
                    IcodeSection("Log") {
                        val lines = agents.log?.takeIf { it.agent == agent.id }?.lines.orEmpty()
                        CodeSurface(Modifier.fillMaxWidth()) {
                            MonoLine(lines.takeLast(LOG_LINES).joinToString("\n").ifEmpty { "Nothing written yet" }, style = IcodeType.MonoSmall, maxLines = LOG_LINES)
                        }
                        SecondaryButton("Read Again", { gateway.dispatch(AgentIntent.ReadLog(agent.id)) }, compact = true)
                    }
                }
                AgentReach()
            }
        }
        if (removing) {
            IcodeConfirmSheet(
                title = "Remove ${agent.name}?",
                confirmLabel = "Remove",
                onConfirm = {
                    gateway.dispatch(ICodeIntent.ApplyRuntimeTools(installedTools(tools) - agent.tool))
                    removing = false
                },
                onDismiss = { removing = false },
                destructive = true,
            ) {
                Text("Its install goes. Its sign-in and its own sessions stay in the runtime's home, where it keeps them.", style = IcodeType.Body)
            }
        }
    }
}

/** How the agent signs in, as it said when it last started: a button each, its keys entered here. */
@Composable
private fun SignIn(agent: String, status: AgentStatus?, keys: List<McpValue>, onTerminal: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val methods = status?.signIn.orEmpty()
    if (methods.isEmpty()) return
    IcodeSection("Sign In") {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
            methods.filter { it.kind != AgentSignIn.Kind.Keys }.forEach { method ->
                SecondaryButton(method.name, {
                    gateway.dispatch(AgentIntent.SignIn(method.id, agent))
                    if (method.kind == AgentSignIn.Kind.Terminal) onTerminal()
                }, compact = true)
            }
            if (methods.any { it.kind == AgentSignIn.Kind.Terminal }) SecondaryButton("Signed In", { gateway.dispatch(AgentIntent.SignedIn(agent)) }, compact = true)
        }
        if (keys.isNotEmpty()) {
            // Never saveable: a key is a secret.
            var typed by remember(keys) { mutableStateOf(keys) }
            McpValuesEditor("Keys", typed, fixed = true) { typed = it }
            methods.firstOrNull { it.kind == AgentSignIn.Kind.Keys }?.link?.let { link ->
                SecondaryButton("Get a Key", { gateway.dispatch(ICodeIntent.OpenExternalUrl(link)) }, compact = true)
            }
            PrimaryButton("Save Keys", { gateway.dispatch(AgentIntent.SaveKeys(agent, typed)) }, compact = true)
        }
    }
}

/** Every runtime tool installed now, by ID: what Apply keeps while one agent comes or goes. */
private fun installedTools(tools: RuntimeToolsState): Set<String> = RuntimeTools.all.filter(tools::isInstalled).map { it.id }.toSet()

private const val LOG_LINES = 40
