package dev.icode.mobile.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.voice.ModelState
import dev.icode.mobile.domain.voice.SpokenReplies
import dev.icode.mobile.domain.voice.VoiceCatalog
import dev.icode.mobile.domain.voice.VoiceEngine
import dev.icode.mobile.domain.voice.VoiceModel
import dev.icode.mobile.domain.voice.VoiceSettings
import dev.icode.mobile.domain.voice.VoiceState
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › Voice (docs/phone-agent-plan.md §5.8): the microphone; who listens and who
 * speaks — Android, or an open-source model on this phone, downloaded once its size is shown; when
 * answers are read aloud; and the models on this phone, each with what it takes.
 */
@Composable
fun VoiceSettingsScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val voice = LocalICodeSnapshot.current.voice
    val settings = voice.settings
    val context = LocalContext.current
    val choose = { next: VoiceSettings -> gateway.dispatch(VoiceIntent.Choose(next)) }
    // Asked once here; refused, the next tap opens App info, where Android keeps it.
    var refused by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refused = !granted
        gateway.dispatch(VoiceIntent.Refresh)
    }
    RefreshOnResume { gateway.dispatch(VoiceIntent.Refresh) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Voice", subtitle = "Microphone and spoken answers", onBack = actions.back)
        IcodePageColumn(Modifier.weight(1f)) {
            IcodeSection("Microphone") {
                SettingRow(
                    "Microphone",
                    Modifier.fillMaxWidth(),
                    subtitle = when {
                        voice.microphone -> "Used only while listening"
                        refused -> "Allow it in App info › Permissions"
                        else -> "To speak to the assistant"
                    },
                    leadingIcon = Icons.Outlined.Mic,
                    onClick = { if (refused) context.openAppInfo() else ask.launch(Manifest.permission.RECORD_AUDIO) }.takeUnless { voice.microphone },
                ) { AccessState(if (voice.microphone) "ALLOWED" else "NOT ALLOWED", allowed = voice.microphone) }
            }
            IcodeSection("Listening") {
                EngineSelect(settings.listening, android = "Your phone's speech recognition", onPhone = "Offline: what you say stays here") {
                    choose(settings.copy(listening = it))
                }
                if (settings.listening == VoiceEngine.OnPhone) {
                    val model = VoiceCatalog.listener(settings.listenModel)
                    IcodeSelect(
                        options = VoiceCatalog.LISTENERS,
                        selected = model,
                        optionLabel = { it.name },
                        optionDescription = { it.detail },
                        onSelect = { choose(settings.copy(listenModel = it.id)) },
                        label = "Model",
                    )
                    VoiceModelRow(model, voice, gateway::dispatch, needs = listOf(VoiceCatalog.VAD))
                }
            }
            IcodeSection("Speaking") {
                EngineSelect(settings.speaking, android = "Your phone's text-to-speech voice", onPhone = "Natural voices, offline") {
                    choose(settings.copy(speaking = it))
                }
                if (settings.speaking == VoiceEngine.OnPhone) {
                    val chosen = VoiceCatalog.voice(settings.voice)
                    IcodeSelect(
                        options = VoiceCatalog.languages,
                        selected = chosen.language,
                        optionLabel = { it },
                        onSelect = { language -> choose(settings.copy(voice = VoiceCatalog.voicesIn(language).first().id)) },
                        label = "Language",
                    )
                    val voices = VoiceCatalog.voicesIn(chosen.language)
                    if (voices.size > 1) {
                        IcodeSelect(options = voices, selected = chosen, optionLabel = { it.name }, onSelect = { choose(settings.copy(voice = it.id)) }, label = "Voice")
                    }
                    VoiceModelRow(chosen.model, voice, gateway::dispatch)
                }
                SettingRow(
                    "Play Sample",
                    Modifier.fillMaxWidth(),
                    subtitle = if (voice.speaking) "Speaking…" else "Hear the voice",
                    leadingIcon = Icons.Outlined.PlayArrow,
                    onClick = { gateway.dispatch(if (voice.speaking) VoiceIntent.Silence else VoiceIntent.Sample) },
                )
            }
            IcodeSection("Spoken Replies") {
                IcodeSelect(
                    options = SpokenReplies.entries,
                    selected = settings.spoken,
                    optionLabel = { it.label },
                    optionDescription = { it.description },
                    onSelect = { choose(settings.copy(spoken = it)) },
                    label = "Read Answers Aloud",
                )
            }
            // The rest of what was downloaded: what each takes, and Remove.
            val chosen = settings.listenModels + settings.speakModels
            val others = VoiceCatalog.models.filter { it !in chosen && voice.state(it) !is ModelState.Absent }
            if (others.isNotEmpty()) {
                IcodeSection("On This Phone") { others.forEach { VoiceModelRow(it, voice, gateway::dispatch) } }
            }
        }
    }
}

/** Android's engine, or a model on this phone. */
@Composable
private fun EngineSelect(engine: VoiceEngine, android: String, onPhone: String, onSelect: (VoiceEngine) -> Unit) {
    IcodeSelect(
        options = VoiceEngine.entries,
        selected = engine,
        optionLabel = { it.label },
        optionDescription = { if (it == VoiceEngine.Android) android else onPhone },
        onSelect = onSelect,
        label = "Engine",
    )
}

/**
 * [model] on this phone: its download's size first, then how far it has come, then what it takes, with
 * Download, Cancel or Remove. [needs] download with it, and stay when it is removed.
 */
@Composable
private fun VoiceModelRow(model: VoiceModel, voice: VoiceState, dispatch: (ICodeIntent) -> Unit, needs: List<VoiceModel> = emptyList()) {
    val all = listOf(model) + needs
    val missing = all.filter { voice.state(it) !is ModelState.Ready }
    val states = all.map(voice::state)
    val failure = states.filterIsInstance<ModelState.Failed>().firstOrNull()
    val busy = states.any { it is ModelState.Downloading || it == ModelState.Unpacking }
    val total = all.sumOf { it.bytes }
    val received = all.sumOf { part ->
        when (val state = voice.state(part)) {
            is ModelState.Ready -> part.bytes
            is ModelState.Downloading -> (state.fraction * part.bytes).toLong()
            else -> 0L
        }
    }
    SettingRow(
        model.name,
        Modifier.fillMaxWidth(),
        subtitle = when {
            states.any { it == ModelState.Unpacking } -> "Unpacking…"
            busy -> "Downloading · ${received * PERCENT / total.coerceAtLeast(1)}% of ${ByteSize.format(total)}"
            failure != null -> failure.reason
            missing.isEmpty() -> "On this phone · ${ByteSize.format(states.sumOf { (it as ModelState.Ready).bytes })}"
            else -> "${model.detail} · ${ByteSize.format(missing.sumOf { it.bytes })}"
        },
        leadingIcon = when {
            failure != null -> Icons.Outlined.ErrorOutline
            missing.isEmpty() -> Icons.Outlined.CheckCircle
            else -> Icons.Outlined.CloudDownload
        },
        leadingTint = when {
            failure != null -> IcodeColors.Error
            missing.isEmpty() -> IcodeColors.Green
            else -> IcodeColors.Accent
        },
    ) {
        when {
            busy -> IcodeTextButton("Cancel", { all.filter { voice.state(it) !is ModelState.Ready }.forEach { dispatch(VoiceIntent.Remove(it.id)) } })
            missing.isEmpty() -> IcodeTextButton("Remove", { dispatch(VoiceIntent.Remove(model.id)) })
            else -> IcodeTextButton(if (failure != null) "Retry" else "Download", { missing.forEach { dispatch(VoiceIntent.Download(it.id)) } })
        }
    }
}

private const val PERCENT = 100
