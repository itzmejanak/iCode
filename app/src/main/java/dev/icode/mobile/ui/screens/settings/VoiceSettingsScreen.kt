package dev.icode.mobile.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.data.voice.Voice
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.voice.SpokenReplies
import dev.icode.mobile.navigation.NavigationActions

/**
 * Settings › Assistant › Voice (docs/phone-agent-plan.md §5.8): the microphone, Android's speech
 * recognition and text-to-speech, and when answers are read aloud.
 */
@Composable
fun VoiceSettingsScreen(actions: NavigationActions) {
    val context = LocalContext.current
    remember { Voice.load(context) }
    val replies by Voice.replies.collectAsState()
    var microphone by remember { mutableStateOf(Voice.hasMicrophone(context)) }
    var tried by remember { mutableStateOf<String?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { microphone = it }
    RefreshOnResume { microphone = Voice.hasMicrophone(context) }
    val recognition = remember { Voice.canListen(context) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Voice", subtitle = if (microphone) "Microphone allowed" else "Microphone not allowed", onBack = actions.back)
        IcodePageColumn(Modifier.weight(1f)) {
            IcodeSection("Listening") {
                Text(
                    "The microphone in the chat and in the bubble listens with Android's speech recognition — on this phone " +
                        "where it offers that, else the recognition service you chose in Android's settings. What is heard is " +
                        "put in the message for you to send.",
                    style = IcodeType.Body.copy(color = IcodeColors.TextSecondary),
                )
                IcodeListRow(
                    card = true,
                    title = "Microphone",
                    subtitle = if (microphone) "Allowed · used only while listening" else "Not allowed",
                    trailing = { StatusLabel(if (microphone) "On" else "Off", if (microphone) IcodeColors.Green else IcodeColors.TextMuted) },
                )
                if (!microphone) PrimaryButton("Allow Microphone", { ask.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.fillMaxWidth())
                if (!recognition) {
                    Text("This phone has no speech recognition service; typing works as before.", style = IcodeType.Caption.copy(color = IcodeColors.Amber))
                } else if (microphone) {
                    SecondaryButton(
                        "Try Listening",
                        { Voice.listen(context, onHeard = { tried = "Heard: $it" }, onProblem = { tried = it }) },
                        Modifier.fillMaxWidth(),
                    )
                    tried?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary)) }
                }
            }
            IcodeSection("Spoken replies") {
                SpokenReplies.entries.forEach { option ->
                    IcodeListRow(
                        card = true,
                        selected = option == replies,
                        title = option.label,
                        subtitle = option.description,
                        subtitleStyle = IcodeType.Caption,
                        onClick = { Voice.setReplies(context, option) },
                        trailing = {
                            if (option == replies) Icon(Icons.Outlined.Check, contentDescription = "Chosen", tint = IcodeColors.Accent)
                        },
                    )
                }
                Text(
                    "Read with Android's text-to-speech engine and voice. Only the answer's words are read — no code or links. " +
                        "A new message or Stop silences it.",
                    style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
                )
                SecondaryButton("Hear a Sample", { Voice.speak(context, "This is how iCode reads its answers.") }, Modifier.fillMaxWidth())
            }
        }
    }
}
