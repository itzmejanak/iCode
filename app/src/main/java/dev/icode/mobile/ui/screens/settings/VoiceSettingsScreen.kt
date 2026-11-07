package dev.icode.mobile.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.domain.voice.SpokenReplies
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › Voice (docs/phone-agent-plan.md §5.8): the microphone; what hears it — the
 * speech provider in use (docs/speech-plan.md §5); and when answers are read aloud, by Android's voice.
 */
@Composable
fun VoiceSettingsScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val voice = LocalICodeSnapshot.current.voice
    val context = LocalContext.current
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
                val listener = voice.listener
                SettingRow(
                    "Speech Recognition",
                    Modifier.fillMaxWidth(),
                    subtitle = "${listener.name} · ${SpeechRules.where(listener)}",
                    leadingIcon = Icons.Outlined.GraphicEq,
                    onClick = { actions.navigate(Route.SpeechProviders) },
                ) { Chevron() }
            }
            IcodeSection("Read Aloud") {
                IcodeSelect(
                    options = SpokenReplies.entries,
                    selected = voice.spoken,
                    optionLabel = { it.label },
                    optionDescription = { it.description },
                    onSelect = { gateway.dispatch(VoiceIntent.ChooseSpokenReplies(it)) },
                    label = "Answers",
                )
                SettingRow(
                    "Play Sample",
                    Modifier.fillMaxWidth(),
                    subtitle = if (voice.speaking) "Speaking…" else "Hear the voice",
                    leadingIcon = Icons.Outlined.PlayArrow,
                    onClick = { gateway.dispatch(if (voice.speaking) VoiceIntent.Silence else VoiceIntent.Sample) },
                )
                // Android's own voice reads aloud: iCode opens where it is kept.
                SettingRow(
                    "Voice and Language",
                    Modifier.fillMaxWidth(),
                    subtitle = "Android's text-to-speech",
                    leadingIcon = Icons.Outlined.RecordVoiceOver,
                    onClick = { context.openFirst(Intent(TTS_SETTINGS), Intent(Settings.ACTION_SETTINGS)) },
                ) { Chevron() }
            }
        }
    }
}

/** Settings' text-to-speech page: its action, which Android names nowhere public. */
private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
