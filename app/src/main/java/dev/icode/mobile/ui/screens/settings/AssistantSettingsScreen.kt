package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.AssistantProvider
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant: its pages — Providers, Coding Agents, MCP Servers, Voice, Phone Control — as Settings lists its categories
 * (owner's request, 2026-11-03). The chat's model, mode, effort and permissions are chosen on the chat page.
 */
@Composable
fun AssistantSettingsScreen(actions: NavigationActions) {
    val snapshot = LocalICodeSnapshot.current
    val settings = snapshot.assistant.settings
    val model = settings.model
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Assistant", subtitle = settingsSubtitle(Route.AssistantSettings), onBack = actions.back)
        IcodePageColumn(Modifier.weight(1f)) {
            IcodeSection {
                SettingRow(
                    "Providers",
                    Modifier.fillMaxWidth(),
                    subtitle = when {
                        settings.providers.isEmpty() -> "Add a model provider"
                        model != null -> "${providerCount(settings.providers.size)} · ${model.id}"
                        else -> providerCount(settings.providers.size)
                    },
                    leadingIcon = Icons.Outlined.Hub,
                    onClick = { actions.navigate(Route.AssistantProviders) },
                ) {
                    if (settings.providers.isEmpty()) AccessState("NONE", allowed = false)
                    Chevron()
                }
                SettingRow(
                    "Coding Agents",
                    Modifier.fillMaxWidth(),
                    subtitle = snapshot.agents.agents.count { agent -> RuntimeTools.byId(agent.tool)?.let(snapshot.runtimeTools::isInstalled) == true }.let { count ->
                        if (count == 0) "Claude Code, Codex, Gemini CLI and more" else "$count installed" + (snapshot.agents.chosenAgent?.let { " · ${it.name} for new chats" } ?: "")
                    },
                    leadingIcon = Icons.Outlined.SmartToy,
                    onClick = { actions.navigate(Route.CodingAgents) },
                ) { Chevron() }
                SettingRow(
                    "MCP Servers",
                    Modifier.fillMaxWidth(),
                    subtitle = snapshot.mcp.servers.size.let { count ->
                        if (count == 0) "Tools from other servers" else "${if (count == 1) "1 server" else "$count servers"} · ${snapshot.mcp.toolsOn} tools"
                    },
                    leadingIcon = Icons.Outlined.Extension,
                    onClick = { actions.navigate(Route.McpServers) },
                ) { Chevron() }
                SettingRow(
                    "Voice",
                    Modifier.fillMaxWidth(),
                    subtitle = "Answers read aloud: ${snapshot.voice.spoken.label.lowercase()}",
                    leadingIcon = Icons.Outlined.Mic,
                    onClick = { actions.navigate(Route.VoiceSettings) },
                ) { Chevron() }
                SettingRow(
                    "Phone Control",
                    Modifier.fillMaxWidth(),
                    subtitle = "Read and act in other apps",
                    leadingIcon = Icons.Outlined.PhoneAndroid,
                    onClick = { actions.navigate(Route.PhoneControl) },
                ) {
                    AccessState(if (snapshot.phone.service) "ON" else "OFF", allowed = snapshot.phone.service)
                    Chevron()
                }
            }
        }
    }
}

private fun providerCount(count: Int) = if (count == 1) "1 provider" else "$count providers"

/**
 * Settings › Assistant › Providers (docs/assistant-and-mcp.md), as Language Services lists its
 * servers: each with its models and whether the chat uses it; a tap opens its page, + opens the sheet of
 * provider kinds.
 */
@Composable
fun AssistantProvidersScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val settings = LocalICodeSnapshot.current.assistant.settings
    var picking by rememberSaveable { mutableStateOf(false) }
    fun open(id: String, kind: AssistantProviderKind? = null) {
        picking = false
        gateway.dispatch(ICodeIntent.EditAssistantProvider(id, kind))
        actions.navigate(Route.AssistantProvider)
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(picking)) {
            IcodeTopBar(
                "Providers",
                subtitle = "Models and API keys",
                onBack = actions.back,
                actions = {
                    if (settings.providers.isNotEmpty()) {
                        IconButton(onClick = { picking = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add provider", tint = IcodeColors.TextPrimary)
                        }
                    }
                },
            )
            IcodePageColumn(Modifier.weight(1f)) {
                if (settings.providers.isEmpty()) {
                    IcodeSection {
                        EmptyState(
                            title = "Add a model provider",
                            description = "OpenAI, Anthropic, Gemini, Ollama and more",
                            mark = { IcodeBrandMark(Modifier.size(IcodeSizes.MarkLg)) },
                            modifier = Modifier.padding(vertical = IcodeSpacing.Space6),
                        )
                        PrimaryButton("Add Provider", { picking = true }, Modifier.fillMaxWidth())
                    }
                } else {
                    IcodeSection("Providers") {
                        settings.providers.forEach { provider ->
                            ProviderRow(provider, inUse = provider.id == settings.choice?.provider) { open(provider.id) }
                        }
                    }
                }
            }
        }
        if (picking) {
            IcodeSheetOverlay(onClose = { picking = false }) {
                ProviderKindSheet(selected = null, modifier = Modifier.align(Alignment.BottomCenter), onClose = { picking = false }) { open("", it) }
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: AssistantProvider, inUse: Boolean, onClick: () -> Unit) {
    val count = provider.models.size
    IcodeListRow(
        card = true,
        selected = inUse,
        onClick = onClick,
        leading = {
            IconBadge(
                text = provider.name.filter(Char::isLetterOrDigit).take(2).uppercase().ifEmpty { "AI" },
                tint = if (inUse) IcodeColors.Accent else IcodeColors.TextSecondary,
                size = IcodeSizes.BadgeSm,
            )
        },
        content = {
            Column(Modifier.weight(1f)) {
                Text(provider.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                MonoLine(
                    listOfNotNull(
                        provider.kind.label.takeIf { it != provider.name },
                        if (count == 1) "1 model" else "$count models",
                    ).joinToString(" · "),
                    style = IcodeType.MonoSmall,
                )
            }
        },
        trailing = {
            when {
                provider.kind.needsKey && !provider.hasKey -> StatusLabel("No key", IcodeColors.Amber)
                inUse -> StatusLabel("In use", IcodeColors.Green)
            }
            Chevron()
        },
    )
}
