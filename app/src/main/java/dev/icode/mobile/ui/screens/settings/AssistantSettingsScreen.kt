package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeDropdown
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.KeyChipRow
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/**
 * The assistant's provider (docs/assistant-koog-plan.md §3, §12): which one, where for a server of
 * the user's own, the model — Koog's catalogue a tap away, any ID typed, with its limits for one Koog
 * does not know — and the API key, masked and never shown again once saved. Save checks the form;
 * the first message checks the connection. Mode, effort and permissions are the chat page's.
 */
@Composable
fun AssistantSettingsScreen(actions: NavigationActions) {
    val gateway = LocalWorkbenchGateway.current
    val state = LocalWorkbenchSnapshot.current.assistant
    val saved = state.settings
    var kind by rememberSaveable(saved) { mutableStateOf(saved?.kind ?: AssistantProviderKind.entries.first()) }
    var baseUrl by rememberSaveable(saved, kind) {
        mutableStateOf(saved?.takeIf { it.kind == kind }?.baseUrl ?: kind.defaultBaseUrl.orEmpty())
    }
    var model by rememberSaveable(saved, kind) {
        mutableStateOf(saved?.takeIf { it.kind == kind }?.model ?: state.catalogue[kind]?.firstOrNull()?.id.orEmpty())
    }
    // The limits saved for this provider's model, exactly as saved.
    var contextWindow by rememberSaveable(saved, kind) { mutableStateOf(saved?.takeIf { it.kind == kind }?.contextWindow?.toString().orEmpty()) }
    var maxOutput by rememberSaveable(saved, kind) { mutableStateOf(saved?.takeIf { it.kind == kind }?.maxOutput?.toString().orEmpty()) }
    // Never saveable: saved state leaves the process in a Bundle, and a key must not.
    var key by remember(kind) { mutableStateOf("") }
    // A key belongs to the provider it was made for: another provider's is not offered.
    val keySaved = saved?.hasKey == true && saved.kind == kind
    var replacingKey by remember(saved, kind) { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val catalogue = state.catalogue[kind].orEmpty()
    // A model Koog knows comes with its limits; one it does not know gets the user's.
    val known = catalogue.firstOrNull { it.id == model.trim() }
    val limits = if (known == null) contextWindow to maxOutput else "" to ""
    val problem = AssistantRules.settingsProblem(kind, baseUrl, model, key.isNotBlank(), keySaved, limits.first, limits.second)

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Assistant", subtitle = settingsSubtitle(Route.AssistantSettings), onBack = actions.back)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4),
        ) {
            Text(
                "What the assistant reads — files, search results, command output — is sent to this provider.",
                style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
            )
            IcodeDropdown(
                label = "Provider",
                options = AssistantProviderKind.entries,
                selected = kind,
                optionLabel = { it.label },
                onSelect = { kind = it },
                optionDescription = { option ->
                    when {
                        option == AssistantProviderKind.Ollama -> "A model server; on this phone by default"
                        option == AssistantProviderKind.OpenAICompatible -> "DeepSeek, Groq, LM Studio and other servers"
                        else -> "Needs an API key"
                    }
                },
            )
            if (kind.asksBaseUrl) {
                LabeledTextField(
                    "Server Address", baseUrl, { baseUrl = it },
                    placeholder = kind.defaultBaseUrl ?: "https://api.example.com/v1",
                    literal = true,
                )
            }
            LabeledTextField("Model", model, { model = it }, placeholder = "The model's ID", literal = true)
            // Koog's catalogue for this provider, narrowed to what is typed: a tap fills the field.
            val typed = model.trim()
            val suggested = catalogue.map { it.id }
            val shown = suggested.filter { typed.isEmpty() || it.contains(typed, ignoreCase = true) }.ifEmpty { suggested }.take(MAX_SUGGESTIONS)
            if (shown.isNotEmpty()) {
                KeyChipRow(
                    keys = shown,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    activeKeys = setOf(typed),
                    onKey = { model = it },
                )
            }
            if (keySaved && !replacingKey) {
                SectionLabel("API Key")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                    StatusLabel("Saved for ${kind.label}", IcodeColors.Green, Modifier.weight(1f))
                    SecondaryButton("Replace", { replacingKey = true })
                }
            } else {
                LabeledTextField(
                    "API Key", key, { key = it },
                    placeholder = if (kind.needsKey) "Paste the key" else "Optional",
                    secret = true,
                )
            }
            if (known != null) {
                Text(
                    listOfNotNull(
                        known.contextWindow?.let { "${AssistantRules.tokenCount(it)} tokens of context" },
                        known.maxOutput?.let { "up to ${AssistantRules.tokenCount(it)} in a reply" },
                    ).joinToString(", ").ifEmpty { "Koog knows this model" },
                    style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
                )
            } else if (typed.isNotEmpty()) {
                // The meter, compaction and Anthropic's reply limit need these for a model Koog does not know.
                LabeledTextField("Context Window", contextWindow, { contextWindow = it }, placeholder = "Tokens it reads, as 128k — optional", literal = true)
                LabeledTextField("Output Limit", maxOutput, { maxOutput = it }, placeholder = "Tokens in one reply, as 8k — optional", literal = true)
            }
            problem?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
            PrimaryButton(
                "Save",
                {
                    gateway.dispatch(WorkbenchIntent.SaveAssistantSettings(kind, baseUrl, model, key.takeIf { it.isNotBlank() }, limits.first, limits.second))
                    actions.back()
                },
                Modifier.fillMaxWidth(),
                enabled = problem == null,
            )
            if (saved != null) {
                DangerButton("Remove Assistant Settings", { removing = true }, Modifier.fillMaxWidth())
            }
        }
    }
    if (removing) {
        IcodeConfirmDialog(
            title = "Remove the assistant's settings?",
            message = "The provider, the model and the API key are forgotten on this phone. Chats stay.",
            confirmLabel = "Remove",
            onConfirm = {
                gateway.dispatch(WorkbenchIntent.RemoveAssistantSettings)
                removing = false
            },
            onDismiss = { removing = false },
        )
    }
}

private const val MAX_SUGGESTIONS = 12
