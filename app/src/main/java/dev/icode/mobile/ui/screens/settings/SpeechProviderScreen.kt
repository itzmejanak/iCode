package dev.icode.mobile.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.SpeechIntent
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.domain.voice.VoiceState
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * One speech provider (docs/speech-plan.md §5): its name, its key — masked, never shown again once
 * saved — its server's address where it has one, its model and language, where its audio goes; Test
 * hears the user once with it, and Use for Listening makes it the one that hears. Saved, the page stays.
 */
@Composable
fun SpeechProviderScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val voice = LocalICodeSnapshot.current.voice
    val editing = voice.editing
    if (editing == null) {
        // Opened again after the process was gone: there is nothing to show.
        LaunchedEffect(Unit) { actions.back() }
        return
    }
    val saved = voice.providers.firstOrNull { it.id == editing.id }
    val kind = editing.kind
    val android = kind == SpeechProviderKind.Android
    var name by rememberSaveable(editing.id) { mutableStateOf(saved?.name ?: kind.label) }
    var address by rememberSaveable(editing.id) { mutableStateOf(saved?.address.orEmpty()) }
    var model by rememberSaveable(editing.id) { mutableStateOf(saved?.modelUsed.orEmpty()) }
    var language by rememberSaveable(editing.id) { mutableStateOf(saved?.language.orEmpty()) }
    // Never saveable: saved state leaves the process in a Bundle, and a key must not.
    var key by remember(editing.id) { mutableStateOf("") }
    var replacingKey by remember(editing.id) { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val draft = SpeechProvider(editing.id, kind, name, address, model.takeIf { it != kind.models.firstOrNull() }, language)
    // What Back would lose: anything typed, on a new provider or a saved one.
    val typed = key.isNotBlank() || name != (saved?.name ?: kind.label) || address != saved?.address.orEmpty() ||
        model != (saved?.modelUsed ?: kind.models.firstOrNull()).orEmpty() || language != saved?.language.orEmpty()
    val changed = saved == null || typed
    val problem = SpeechRules.problem(draft, voice.providers, keyGiven = key.isNotBlank(), keySaved = saved?.hasKey == true)
    BackHandler(enabled = typed) { leaving = true }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(saved?.name ?: "Add ${kind.label}", subtitle = kind.label, onBack = { if (typed) leaving = true else actions.back() })
        IcodePageColumn(Modifier.weight(1f)) {
            if (android) {
                Text(
                    "Android's own recognizer: on this phone where Android offers it, else the phone's recognition service.",
                    style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
                )
            } else {
                LabeledTextField("Name", name, { name = it }, placeholder = kind.label)
                if (saved?.hasKey == true && !replacingKey) {
                    IcodeSection("API Key") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                            StatusLabel("Saved, never shown again", IcodeColors.Green, Modifier.weight(1f))
                            SecondaryButton("Replace", { replacingKey = true })
                        }
                    }
                } else {
                    LabeledTextField("API Key", key, { key = it }, placeholder = if (kind.needsKey) "Paste the key" else "Optional", secret = true)
                }
                if (kind.asksAddress) LabeledTextField("Server Address", address, { address = it }, placeholder = "https://… or http://127.0.0.1:…", literal = true)
                if (kind.models.isNotEmpty()) {
                    IcodeSelect(kind.models, model.ifEmpty { kind.models.first() }, { it }, { model = it }, label = "Model")
                } else if (kind.asksAddress) {
                    LabeledTextField("Model", model, { model = it }, placeholder = "The server's model", literal = true)
                }
            }
            LabeledTextField("Language", language, { language = it }, placeholder = "Automatic: the phone's", literal = true)
            Text(
                if (android) "Audio stays with Android." else "While it listens, audio goes to ${SpeechRules.where(draft).ifEmpty { "its server" }}.",
                style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
            )
            if (saved != null) Test(voice, saved, enabled = !changed)
            if (android) {
                val context = LocalContext.current
                SettingRow(
                    "Android's Settings",
                    Modifier.fillMaxWidth(),
                    subtitle = "Its voice input and languages",
                    leadingIcon = Icons.Outlined.GraphicEq,
                    onClick = { context.openFirst(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), Intent(Settings.ACTION_SETTINGS)) },
                ) { Chevron() }
            }
            IcodeSection {
                problem?.takeIf { changed }?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
                if (changed) {
                    PrimaryButton(
                        if (saved == null) "Add Provider" else "Save",
                        { gateway.dispatch(SpeechIntent.Save(draft, key.takeIf { it.isNotBlank() })) },
                        Modifier.fillMaxWidth(),
                        enabled = problem == null,
                    )
                }
                if (saved != null && voice.inUse != saved.id) {
                    SecondaryButton("Use for Listening", { gateway.dispatch(SpeechIntent.Use(saved.id)) }, Modifier.fillMaxWidth(), enabled = !changed)
                } else if (saved != null) {
                    StatusLabel("In use: it hears the microphone", IcodeColors.Green)
                }
                if (saved != null && !android) DangerButton("Remove Provider", { removing = true }, Modifier.fillMaxWidth())
            }
        }
    }
    if (leaving) {
        IcodeConfirmSheet(
            title = "Leave without saving?",
            message = if (saved == null) "This provider is not added yet." else "The changes to ${saved.name} are not saved.",
            confirmLabel = "Discard",
            onConfirm = {
                leaving = false
                actions.back()
            },
            onDismiss = { leaving = false },
        )
    }
    if (removing && saved != null) {
        IcodeConfirmSheet(
            title = "Remove ${saved.name}?",
            message = "Its API key is forgotten on this phone." + if (voice.inUse == saved.id) " Android's recognizer hears from now on." else "",
            confirmLabel = "Remove",
            onConfirm = {
                gateway.dispatch(SpeechIntent.Remove(saved.id))
                removing = false
                actions.back()
            },
            onDismiss = { removing = false },
        )
    }
}

/**
 * Test: hears the user once with [provider], as saved, and shows what it heard — or why not — here
 * alone: it is sent nowhere.
 */
@Composable
private fun Test(voice: VoiceState, provider: SpeechProvider, enabled: Boolean) {
    val gateway = LocalICodeGateway.current
    val heard = voice.heard?.takeIf { it.from == VoiceFrom.Test }
    val listening = voice.listening(VoiceFrom.Test)
    val failure = voice.failures[provider.id]
    IcodeSection("Test") {
        when {
            listening -> Text(heard?.text?.ifBlank { null } ?: "Listening…", style = IcodeType.Body)
            heard?.problem != null -> Text(heard.problem, style = IcodeType.Caption.copy(color = IcodeColors.Error))
            heard != null -> Text(heard.text, style = IcodeType.Body)
            failure != null -> Text("Last time: $failure", style = IcodeType.Caption.copy(color = IcodeColors.Error))
            else -> Text("Say something: what it hears shows here, and is sent nowhere.", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
        }
        if (listening) {
            Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                PrimaryButton("Done", { gateway.dispatch(VoiceIntent.FinishListening) }, Modifier.weight(1f))
                SecondaryButton("Stop", { gateway.dispatch(VoiceIntent.CancelListening) }, Modifier.weight(1f))
            }
        } else {
            SecondaryButton("Test", { gateway.dispatch(SpeechIntent.Test(provider.id)) }, Modifier.fillMaxWidth(), enabled = enabled && voice.microphone)
            if (!voice.microphone) Text("Allow the microphone on the Voice page first.", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
        }
    }
}
