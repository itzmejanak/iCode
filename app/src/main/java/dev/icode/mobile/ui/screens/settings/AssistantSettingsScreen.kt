package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.AssistantProvider
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.data.voice.Voice
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.behindSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant: one row per page — Providers, Voice, Phone control — as Settings lists its
 * categories (owner's request, 2026-11-03). The chat's model, mode, effort and permissions are chosen on
 * the chat page.
 */
@Composable
fun AssistantSettingsScreen(actions: NavigationActions) {
    val settings = LocalICodeSnapshot.current.assistant.settings
    val phone = LocalICodeSnapshot.current.phone
    val context = LocalContext.current
    remember { Voice.load(context) }
    val replies by Voice.replies.collectAsState()
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Assistant", subtitle = settingsSubtitle(Route.AssistantSettings), onBack = actions.back)
        IcodePageColumn(Modifier.weight(1f)) {
            IcodeSection {
                HubRow(
                    "Providers",
                    when {
                        settings.providers.isEmpty() -> "None yet · add OpenAI, Anthropic, Gemini, Ollama…"
                        settings.model != null && settings.provider != null -> "${settings.providers.size} · the chat uses ${settings.model!!.id} from ${settings.provider!!.name}"
                        else -> "${settings.providers.size} · models and API keys"
                    },
                    status = if (settings.providers.isEmpty()) "Add" to IcodeColors.Amber else null,
                ) { actions.navigate(Route.AssistantProviders) }
                HubRow("Voice", "Speak to it · answers read aloud: ${replies.label}") { actions.navigate(Route.VoiceSettings) }
                HubRow(
                    "Phone control",
                    if (phone.service) "On" else "Off · read and act in other apps from a phone chat",
                    status = if (phone.service) "On" to IcodeColors.Green else null,
                ) { actions.navigate(Route.PhoneControl) }
            }
            Text(
                "What the assistant reads — files, search results, command output, what you attach — is sent to the provider of " +
                    "the chat's model. API keys are kept encrypted on this phone and never shown again.",
                style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
            )
        }
    }
}

@Composable
private fun HubRow(title: String, subtitle: String, status: Pair<String, androidx.compose.ui.graphics.Color>? = null, onClick: () -> Unit) {
    IcodeListRow(
        card = true,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            status?.let { (label, tint) -> StatusLabel(label, tint) }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
        },
    )
}

/**
 * Settings › Assistant › Providers (docs/assistant-koog-plan.md §13 P1), as Language Services lists its
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
                            description = "OpenAI, Anthropic, Google Gemini, OpenRouter, Ollama, or any OpenAI-compatible server — " +
                                "DeepSeek, Groq, LM Studio. Add several, each with the models you use: the chat lists them all.",
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
                        settings.provider?.let { provider ->
                            settings.model?.let { model ->
                                Text(
                                    "The chat uses ${model.id} from ${provider.name}. Choose another model on the chat page.",
                                    style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
                                )
                            }
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
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
        },
    )
}
