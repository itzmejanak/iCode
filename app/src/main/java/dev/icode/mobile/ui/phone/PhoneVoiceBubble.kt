package dev.icode.mobile.ui.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFloatingPill
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType

/**
 * The voice bubble (§5.7, redesigned 2026-11-04): the bar gives way to it while the microphone listens.
 * The words heard so far live inside it — Gemini's live line — under a microphone that mutes and a Stop
 * that throws the hearing away. The words themselves stay the bubble: tapped, they mute; dragged, they
 * go where the finger goes. Muting never stops what the agent does. Its waveform leads as far in from
 * one end as Stop's icon sits from the other.
 */
@Composable
internal fun PhoneVoiceBubble(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    val words = view.voice.heard?.text.orEmpty()
    IcodeFloatingPill(Modifier.widthIn(max = overlayPanelWidth())) {
        Row(
            Modifier
                .weight(1f, fill = words.isNotBlank())
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = actions.dragEnd, onDragCancel = actions.dragEnd) { change, amount ->
                        change.consume()
                        actions.drag(amount.x, amount.y)
                    }
                }
                .clickable { actions.mute() }
                .padding(start = IcodeSpacing.IconButtonInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (words.isBlank()) {
                // Nothing heard yet: the listening waveform alone, big enough to be the bubble's whole face.
                Waveform(IcodeColors.Accent, IcodeSizes.WaveformVoice, IcodeSizes.WaveformBarVoice, VOICE_BARS)
            } else {
                Waveform(IcodeColors.Accent)
                Text(
                    words,
                    style = IcodeType.Body.copy(color = IcodeColors.TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = IcodeSpacing.Space2),
                )
            }
        }
        OverlayButton(Icons.Outlined.MicOff, "Mute, type instead", IcodeColors.TextSecondary, onClick = actions.mute)
        OverlayButton(Icons.Outlined.Stop, "Stop listening", IcodeColors.Error, onClick = actions.cancel)
    }
}

/** The voice bubble's alone waveform: wider, so the listening face fills the pill. */
private val VOICE_BARS = listOf(0.3f, 0.55f, 0.8f, 1f, 0.75f, 1f, 0.65f, 0.45f, 0.3f)
