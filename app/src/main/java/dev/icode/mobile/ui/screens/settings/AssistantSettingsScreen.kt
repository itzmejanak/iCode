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
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * The assistant's providers (docs/assistant-koog-plan.md §13 P1), as Language Services lists its
 * servers: each with its models and whether the chat uses it; a tap opens its page, + adds one.
 * The chat's model, mode, effort and permissions are chosen on the chat page.
 */
@Composable
fun AssistantSettingsScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val settings = LocalICodeSnapshot.current.assistant.settings
    fun open(id: String) {
        gateway.dispatch(ICodeIntent.EditAssistantProvider(id))
        actions.navigate(Route.AssistantProvider)
    }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Assistant",
            subtitle = settingsSubtitle(Route.AssistantSettings),
            onBack = actions.back,
            actions = {
                if (settings.providers.isNotEmpty()) {
                    IconButton(onClick = { open("") }) {
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
                    PrimaryButton("Add Provider", { open("") }, Modifier.fillMaxWidth())
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
            Text(
                "What the assistant reads — files, search results, command output, what you attach — is sent to the provider of " +
                    "the chat's model. API keys are kept encrypted on this phone and never shown again.",
                style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
            )
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
