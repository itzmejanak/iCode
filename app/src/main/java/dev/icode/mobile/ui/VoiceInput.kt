package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.domain.voice.VoiceState

// The microphone, as the chat's composer and the bubble show it (docs/phone-agent-plan.md §5.8).

/**
 * Tapped, it listens; tapped again, the user is done. [askMicrophone] runs instead where Android has not
 * granted the microphone and the screen can ask for it; elsewhere the hearing says what to do.
 */
@Composable
internal fun MicButton(voice: VoiceState, from: VoiceFrom, dispatch: (ICodeIntent) -> Unit, size: Dp = IcodeSizes.IconMd, askMicrophone: (() -> Unit)? = null) {
    val listening = voice.listening(from)
    IconButton(onClick = {
        when {
            listening -> dispatch(VoiceIntent.FinishListening)
            !voice.microphone && askMicrophone != null -> askMicrophone()
            else -> dispatch(VoiceIntent.Listen(from))
        }
    }) {
        Icon(
            if (listening) Icons.Outlined.StopCircle else Icons.Outlined.Mic,
            contentDescription = if (listening) "Done talking" else "Speak",
            tint = if (listening) IcodeColors.Accent else IcodeColors.TextSecondary,
            modifier = Modifier.size(size),
        )
    }
}

/** While the microphone listens for [from], the words heard so far; after, why nothing was heard. */
@Composable
internal fun HeardLine(voice: VoiceState, from: VoiceFrom, modifier: Modifier = Modifier) {
    val listening = voice.listening(from)
    val line = if (listening) voice.heard?.text?.ifBlank { null } ?: "Listening…" else voice.problem(from) ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        if (listening) Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
        Text(
            line,
            style = IcodeType.Caption.copy(color = if (listening) IcodeColors.TextSecondary else IcodeColors.Amber),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Once per hearing for [from], its words once the user stopped talking: they are sent. One heard before this screen is not. */
@Composable
internal fun OnHeard(voice: VoiceState, from: VoiceFrom, send: (String) -> Unit) {
    val heard = voice.heard?.takeIf { it.from == from }
    var handled by remember { mutableLongStateOf(heard?.id ?: 0L) }
    LaunchedEffect(heard?.id, heard?.final) {
        val words = heard?.sent ?: return@LaunchedEffect
        if (heard.id == handled) return@LaunchedEffect
        handled = heard.id
        send(words)
    }
}
