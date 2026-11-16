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
import androidx.compose.material.icons.outlined.RecordVoiceOver
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
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.SpeechIntent
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.domain.voice.SpeechWay
import dev.icode.mobile.domain.voice.VoiceRules
import dev.icode.mobile.domain.voice.Voices
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * One speech provider (docs/voice-and-phone-control.md): its account — name, key (masked,
 * never shown again once saved), its server's address where it has one, its language — then a part for
 * each way it serves. Listening: its model, where its audio goes, Test, Use for Listening. Speaking: its
 * voice, model and speed where it has them, where the answer goes, Hear It, Use for Speaking. Saved, the
 * page stays.
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
    val speaking = kind.speaking
    var name by rememberSaveable(editing.id) { mutableStateOf(saved?.name ?: kind.label) }
    var address by rememberSaveable(editing.id) { mutableStateOf(saved?.address.orEmpty()) }
    var model by rememberSaveable(editing.id) { mutableStateOf(saved?.modelUsed.orEmpty()) }
    var language by rememberSaveable(editing.id) { mutableStateOf(saved?.language.orEmpty()) }
    var voiceName by rememberSaveable(editing.id) { mutableStateOf(saved?.voice.orEmpty()) }
    var voiceModel by rememberSaveable(editing.id) { mutableStateOf(saved?.voiceModelUsed.orEmpty()) }
    var speed by rememberSaveable(editing.id) { mutableStateOf(saved?.speed) }
    var region by rememberSaveable(editing.id) { mutableStateOf(saved?.region.orEmpty()) }
    // An OpenAI provider may take its key from the assistant's OpenAI provider: linked, never copied (docs/voice-and-phone-control.md).
    var keyFrom by rememberSaveable(editing.id) { mutableStateOf(saved?.keyFrom) }
    val assistantKeys = LocalICodeSnapshot.current.assistant.settings.providers
        .filter { kind == SpeechProviderKind.OpenAI && it.kind == AssistantProviderKind.OpenAI && it.hasKey }
    // Never saveable: saved state leaves the process in a Bundle, and a key must not.
    var key by remember(editing.id) { mutableStateOf("") }
    var replacingKey by remember(editing.id) { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val draft = SpeechProvider(
        editing.id, kind, name, address, model.takeIf { it != kind.models.firstOrNull() }, language,
        keyFrom = keyFrom,
        voice = voiceName.ifBlank { null },
        voiceModel = voiceModel.takeIf { it != speaking?.models?.firstOrNull() }?.ifBlank { null },
        speed = speed?.takeIf { it != 1f },
        region = region.ifBlank { null },
    )
    // What Back would lose: anything typed, on a new provider or a saved one.
    val typed = key.isNotBlank() || name != (saved?.name ?: kind.label) || address != saved?.address.orEmpty() ||
        model != (saved?.modelUsed ?: kind.models.firstOrNull()).orEmpty() || language != saved?.language.orEmpty() || keyFrom != saved?.keyFrom ||
        voiceName != saved?.voice.orEmpty() || voiceModel != (saved?.voiceModelUsed ?: speaking?.models?.firstOrNull()).orEmpty() || speed != saved?.speed ||
        region != saved?.region.orEmpty()
    val changed = saved == null || typed
    val problem = SpeechRules.problem(draft, voice.providers, keyGiven = key.isNotBlank(), keySaved = saved?.hasKey == true)
    BackHandler(enabled = typed) { leaving = true }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(saved?.name ?: "Add ${kind.label}", subtitle = kind.label, onBack = { if (typed) leaving = true else actions.back() })
        IcodePageColumn(Modifier.weight(1f)) {
            if (android) {
                Text(
                    "Android's own recognizer and voice: on this phone where Android offers them, else the phone's services.",
                    style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
                )
            } else {
                LabeledTextField("Name", name, { name = it }, placeholder = kind.label)
                if (assistantKeys.isNotEmpty()) {
                    IcodeSelect(
                        options = listOf<String?>(null) + assistantKeys.map { it.id },
                        selected = keyFrom,
                        optionLabel = { id -> assistantKeys.firstOrNull { it.id == id }?.let { "The assistant's ${it.name}" } ?: "A key of its own" },
                        onSelect = { keyFrom = it },
                        label = "Key From",
                    )
                }
                if (keyFrom != null) {
                    Unit
                } else if (saved?.hasKey == true && !replacingKey) {
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
            }
            LabeledTextField("Language", language, { language = it }, placeholder = "Automatic: the phone's", literal = true)
            if (kind.hears) {
                IcodeSection("Listening") {
                    if (kind.models.isNotEmpty()) {
                        IcodeSelect(kind.models, model.ifEmpty { kind.models.first() }, { it }, { model = it }, label = "Model")
                    } else if (kind.asksAddress) {
                        LabeledTextField("Model", model, { model = it }, placeholder = "The server's model", literal = true)
                    }
                    WhereItGoes(if (android) "Audio stays with Android." else "While it listens, audio goes to ${SpeechRules.where(draft).ifEmpty { "its server" }}.")
                    if (saved != null) Test(voice, saved, enabled = !changed)
                    if (android) {
                        AndroidSettings("Voice Input", "Android's speech recognition", Icons.Outlined.GraphicEq, Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    }
                    if (saved != null) InUse(voice, saved, SpeechWay.Listening, enabled = !changed)
                }
            }
            if (speaking != null) {
                IcodeSection("Speaking") {
                    val choices = saved?.let { voice.choices[it.id] }
                    if (saved != null && speaking.voices == Voices.Listed) {
                        // Asked when the page opens, and again once it is saved: a key, a region or a language changes them.
                        LaunchedEffect(saved) { gateway.dispatch(SpeechIntent.Choices(saved.id)) }
                    }
                    speaking.regions?.let { regions -> RegionField(regions, region) { region = it } }
                    when (val voices = speaking.voices) {
                        Voices.Android -> AndroidSettings("Voice and Language", "Android's text-to-speech", Icons.Outlined.RecordVoiceOver, Intent(TTS_SETTINGS))
                        Voices.Listed -> Listed("Voice", saved, choices, choices?.voices, voiceName) { voiceName = it }
                        is Voices.Typed -> LabeledTextField("Voice", voiceName, { voiceName = it }, placeholder = "${voices.example}, or the server's own", literal = true)
                        is Voices.Fixed -> FixedVoice(draft, voices, voiceName) { voiceName = it }
                    }
                    when {
                        speaking.models.isNotEmpty() -> IcodeSelect(speaking.models, voiceModel.ifEmpty { speaking.models.first() }, { it }, { voiceModel = it }, label = "Model")
                        speaking.listsModels -> choices?.takeIf { it.problem == null }?.let { ListedSelect("Model", it.models, voiceModel) { picked -> voiceModel = picked } }
                        kind.asksAddress -> LabeledTextField("Model", voiceModel, { voiceModel = it }, placeholder = "The server's model", literal = true)
                    }
                    if (speaking.speeds.isNotEmpty()) {
                        IcodeSelect(speaking.speeds, speed ?: 1f, VoiceRules::speedLabel, { speed = it }, label = "Speed")
                    }
                    speaking.note?.let { WhereItGoes(it) }
                    WhereItGoes(
                        if (android) "Android's text-to-speech reads the answers." else "While it reads aloud, the answer goes to ${SpeechRules.where(draft, SpeechWay.Speaking).ifEmpty { "its server" }}.",
                    )
                    if (saved != null) HearIt(voice, saved, enabled = !changed)
                    if (saved != null) InUse(voice, saved, SpeechWay.Speaking, enabled = !changed)
                }
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
        val givenBack = listOfNotNull(
            "Android's recognizer hears".takeIf { voice.inUse == saved.id },
            "Android's voice reads aloud".takeIf { voice.speaker == saved.id },
        )
        IcodeConfirmSheet(
            title = "Remove ${saved.name}?",
            message = "Its API key is forgotten on this phone." + if (givenBack.isEmpty()) "" else " ${givenBack.joinToString(" and ")} from now on.",
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
