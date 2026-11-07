package dev.icode.mobile.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.domain.voice.SpeechWay
import dev.icode.mobile.domain.voice.SpokenReplies
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › Voice (docs/phone-agent-plan.md §5.8): the microphone; who hears it
 * (docs/speech-plan.md §5); when answers are read aloud, and who reads them (docs/tts-plan.md §5). Each
 * way is chosen in a sheet from the bottom of the screen, with the providers' page at its foot.
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
    var choosing by rememberSaveable { mutableStateOf<SpeechWay?>(null) }
    RefreshOnResume { gateway.dispatch(VoiceIntent.Refresh) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(choosing != null)) {
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
                    val listener = voice.chosen(SpeechWay.Listening)
                    SettingRow(
                        "Speech Recognition",
                        Modifier.fillMaxWidth(),
                        subtitle = "${listener.name} · ${SpeechRules.where(listener)}",
                        leadingIcon = Icons.Outlined.GraphicEq,
                        onClick = { choosing = SpeechWay.Listening },
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
                    val speaker = voice.chosen(SpeechWay.Speaking)
                    SettingRow(
                        "Voice",
                        Modifier.fillMaxWidth(),
                        subtitle = "${speaker.name} · ${SpeechRules.where(speaker, SpeechWay.Speaking)}",
                        leadingIcon = Icons.Outlined.RecordVoiceOver,
                        onClick = { choosing = SpeechWay.Speaking },
                    ) { Chevron() }
                    SettingRow(
                        "Play Sample",
                        Modifier.fillMaxWidth(),
                        subtitle = if (voice.speaking) "Speaking…" else "Hear the voice",
                        leadingIcon = Icons.Outlined.PlayArrow,
                        onClick = { gateway.dispatch(if (voice.speaking) VoiceIntent.Silence else VoiceIntent.Sample) },
                    )
                }
            }
        }
        choosing?.let { way ->
            IcodeSheetOverlay(onClose = { choosing = null }) {
                SpeechChooserSheet(
                    way,
                    voice,
                    Modifier.align(Alignment.BottomCenter),
                    onClose = { choosing = null },
                    onProviders = {
                        choosing = null
                        actions.navigate(Route.SpeechProviders)
                    },
                )
            }
        }
    }
}
