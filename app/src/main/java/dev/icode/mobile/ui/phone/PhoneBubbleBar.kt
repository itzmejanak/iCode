package dev.icode.mobile.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFloatingPill
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.phone.PhoneOverlay.Look
import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.ui.HeardLine

/**
 * The bubble's bar (owner's design, 2026-11-03; redesigned bottom-anchored, 2026-11-04): the bubble
 * expanded into one compact floating pill above where it sat — the mark that collapses it back, the
 * field, the microphone, Send — or Stop while the agent works. Above it, only when there is something
 * to say: what the agent is doing, the question it waits on with its answers, why the microphone heard
 * nothing, or why the last turn failed, with Retry. ⋮ holds New chat and Open in iCode.
 */
@Composable
internal fun PhoneBubbleBar(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    var more by remember { mutableStateOf(false) }
    val overlay = view.overlay
    val draft = view.draft
    Column(
        Modifier.widthIn(max = overlayPanelWidth()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        IcodeFloatingPill(Modifier.fillMaxWidth()) {
            BarMark(view, actions)
            BasicTextField(
                value = draft,
                onValueChange = view.onDraft,
                modifier = Modifier.weight(1f).padding(start = IcodeSpacing.Space1),
                singleLine = true,
                textStyle = IcodeType.Body.copy(color = IcodeColors.TextPrimary),
                cursorBrush = SolidColor(IcodeColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank() && !overlay.working) actions.send() }),
                decorationBox = { field ->
                    Box {
                        if (draft.isEmpty()) Text("Ask iCode anything…", style = IcodeType.Body.copy(color = IcodeColors.TextMuted), maxLines = 1)
                        field()
                    }
                },
            )
            BarMic(view, actions)
            when {
                overlay.working && draft.isBlank() -> OverlayButton(Icons.Outlined.Stop, "Stop", IcodeColors.Error) { actions.dispatch(ICodeIntent.StopAssistant) }
                view.voice.speaking && draft.isBlank() -> OverlayButton(Icons.Outlined.StopCircle, "Stop reading aloud", IcodeColors.Accent) { actions.dispatch(VoiceIntent.Silence) }
                else -> OverlayButton(Icons.AutoMirrored.Outlined.Send, "Send", IcodeColors.Accent, enabled = draft.isNotBlank() && !overlay.working, onClick = actions.send)
            }
            OverlayButton(Icons.Outlined.MoreVert, "More", IcodeColors.TextSecondary) { more = !more }
        }
        Notes(view, actions, more)
    }
}

/**
 * The bar's leading mark: the bubble itself, shrunken into the pill — tiny, since the microphone now
 * carries the states. A tap collapses the bar, or, muted, resumes listening; a drag frees the bubble.
 */
@Composable
private fun BarMark(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    Box(
        Modifier.padding(start = IcodeSpacing.Space1).size(IcodeSizes.BadgeSm)
            .semantics { contentDescription = "iCode assistant" + if (view.overlay.asking) ", a question waits" else "" }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = actions.dragEnd, onDragCancel = actions.dragEnd) { change, amount ->
                    change.consume()
                    actions.drag(amount.x, amount.y)
                }
            }
            .clickable { actions.tap() },
        contentAlignment = Alignment.Center,
    ) {
        IcodeBrandMark(Modifier.size(IcodeSizes.IconMd).clip(IcodeShapes.Pill))
        if (view.overlay.asking) Box(Modifier.align(Alignment.TopEnd)) { StatusDot(IcodeColors.Amber) }
    }
}

/**
 * The bar's microphone, wearing the states the bubble shows — the agent's first, then its own: the
 * blue ring while it listens, the microphone off when muted, the blue spinner thinking, the orange
 * one acting, and the orange waveform while it reads aloud.
 */
@Composable
private fun BarMic(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    val look = view.overlay.look
    IconButton(onClick = actions.listen) {
        Box(Modifier.size(IcodeSizes.IconLg), contentAlignment = Alignment.Center) {
            when (look) {
                Look.Idle -> Icon(Icons.Outlined.Mic, "Speak", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconLg))
                Look.Listening -> {
                    Ring(IcodeColors.Accent)
                    Waveform(IcodeColors.Accent)
                }
                Look.Muted -> Icon(Icons.Outlined.MicOff, "Speak", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconLg))
                Look.Thinking -> Spinner(IcodeColors.Accent)
                Look.Acting -> {
                    Spinner(IcodeColors.Accent)
                    Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
                }
                Look.Speaking -> Waveform(IcodeColors.Accent)
            }
        }
    }
}

/** What the bar has to say above it, in one card; nothing at all when there is nothing. */
@Composable
private fun Notes(view: PhoneBubbleView, actions: PhoneBubbleActions, more: Boolean) {
    val assistant = view.assistant
    val overlay = view.overlay
    val problem = view.voice.problem(VoiceFrom.Bubble)
    val failure = assistant.failure?.takeIf { !overlay.working }
    val step = overlay.step?.takeIf { overlay.working && !overlay.asking }
    if (!more && problem == null && failure == null && step == null && !overlay.asking) return
    Column(
        Modifier.fillMaxWidth().background(IcodeColors.SurfaceElevated, IcodeShapes.Small).padding(IcodeSpacing.Space2),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        step?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (overlay.asking) Question(view, actions.dispatch)
        if (problem != null) HeardLine(view.voice, VoiceFrom.Bubble)
        failure?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IcodeTextButton("Retry", { actions.dispatch(ICodeIntent.RetryAssistantMessage) })
            }
        }
        if (more) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IcodeTextButton("New chat", { actions.dispatch(ICodeIntent.NewAssistantChat) }, enabled = !overlay.working)
                IcodeTextButton("Open in iCode", actions.open)
            }
        }
    }
}

/** The question the turn waits on, with its answers: an approval, or a pause. Shared words with the chat's cards. */
@Composable
private fun Question(view: PhoneBubbleView, dispatch: (ICodeIntent) -> Unit) {
    val assistant = view.assistant
    val approval = assistant.approval
    val pause = assistant.pause
    val bound = AssistantPlace.name(assistant.turn?.workspace ?: assistant.workspace.orEmpty())
    Text(approval?.let { AssistantPermissions.question(it, bound) } ?: pause?.kind?.question.orEmpty(), style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
    Text(approval?.call?.summary ?: pause?.detail.orEmpty(), style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
        if (approval != null) {
            val answer = { it: AssistantAnswer -> dispatch(ICodeIntent.AnswerAssistant(approval.call.id, it)) }
            SecondaryButton("Deny", { answer(AssistantAnswer.Deny) }, compact = true)
            AssistantPermissions.allowIn(approval, bound)?.let { SecondaryButton(it, { answer(AssistantAnswer.AllowInChat) }, compact = true) }
            PrimaryButton(AssistantPermissions.allowOnce(approval), { answer(AssistantAnswer.Allow) }, compact = true)
        } else if (pause != null) {
            SecondaryButton("Stop", { dispatch(ICodeIntent.StopAssistant) }, compact = true)
            PrimaryButton(pause.kind.proceed, { dispatch(ICodeIntent.ContinueAssistant) }, compact = true)
        }
    }
}
