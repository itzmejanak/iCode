package dev.icode.mobile.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.phone.PhoneOverlay

/** What the bubble does, as its window and the backend answer it. */
internal class PhoneBubbleActions(
    val expand: (Boolean) -> Unit,
    val drag: (dx: Float, dy: Float) -> Unit,
    val dragEnd: () -> Unit,
    val dispatch: (ICodeIntent) -> Unit,
    /** The whole chat, in iCode. */
    val open: () -> Unit,
    /** The microphone: words heard go to [onHeard]; null while voice is not set up. */
    val listen: ((onHeard: (String) -> Unit) -> Unit)?,
)

/**
 * The bubble (docs/phone-agent-plan.md §5.7): the iCode mark at the screen's edge, a ring round it while
 * the agent works and a dot when a question waits; tapped, a small panel with what the agent is doing,
 * the question and its answers, a field, the microphone, Send and Stop, and the chat in iCode.
 */
@Composable
internal fun PhoneBubble(overlay: PhoneOverlay, assistant: AssistantState, expanded: Boolean, actions: PhoneBubbleActions) {
    if (expanded) Panel(overlay, assistant, actions) else Mark(overlay, actions)
}

@Composable
private fun Mark(overlay: PhoneOverlay, actions: PhoneBubbleActions) {
    Box(
        Modifier.size(IcodeSizes.Bubble)
            .semantics { contentDescription = if (overlay.asking) "iCode assistant, a question waits" else "iCode assistant" }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = actions.dragEnd, onDragCancel = actions.dragEnd) { change, amount ->
                    change.consume()
                    actions.drag(amount.x, amount.y)
                }
            }
            .clickable { actions.expand(true) },
        contentAlignment = Alignment.Center,
    ) {
        if (overlay.working) CircularProgressIndicator(Modifier.fillMaxSize(), color = IcodeColors.Accent, strokeWidth = IcodeSizes.BubbleRing)
        IcodeBrandMark(Modifier.padding(IcodeSizes.BubbleRing + IcodeSpacing.Micro).fillMaxSize())
        if (overlay.asking) Box(Modifier.align(Alignment.TopEnd)) { StatusDot(IcodeColors.Amber) }
    }
}

@Composable
private fun Panel(overlay: PhoneOverlay, assistant: AssistantState, actions: PhoneBubbleActions) {
    var draft by remember { mutableStateOf("") }
    val send = {
        val text = draft.trim()
        if (text.isNotEmpty()) {
            // The bubble talks in the phone's chats, whatever iCode's page shows.
            actions.dispatch(ICodeIntent.ShowAssistantPlace(AssistantPlace.PHONE))
            actions.dispatch(ICodeIntent.SendAssistantMessage(text))
            draft = ""
        }
    }
    IcodeCard(Modifier.widthIn(max = IcodeSizes.BubblePanel).fillMaxWidth(), elevated = true, padding = PaddingValues(IcodeSpacing.Space3)) {
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                IcodeBrandMark(Modifier.size(IcodeSizes.BadgeSm))
                Column(Modifier.weight(1f)) {
                    Text("Assistant", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        overlay.step ?: "Ready",
                        style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { actions.expand(false) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconMd))
                }
            }
            Question(assistant, actions.dispatch)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                IcodeTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = "Ask the assistant…", textStyle = IcodeType.Body, singleLine = false)
                actions.listen?.let { listen ->
                    IconButton(onClick = { listen { heard -> draft = listOf(draft, heard).filter { it.isNotBlank() }.joinToString(" ") } }) {
                        Icon(Icons.Outlined.Mic, contentDescription = "Speak", tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconLg))
                    }
                }
                if (overlay.working && draft.isBlank()) {
                    IconButton(onClick = { actions.dispatch(ICodeIntent.StopAssistant) }) {
                        Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = IcodeColors.Error, modifier = Modifier.size(IcodeSizes.IconLg))
                    }
                } else {
                    IconButton(onClick = send, enabled = draft.isNotBlank() && !overlay.working) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconLg))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IcodeTextButton("New chat", { actions.dispatch(ICodeIntent.ShowAssistantPlace(AssistantPlace.PHONE)); actions.dispatch(ICodeIntent.NewAssistantChat) }, enabled = !overlay.working)
                IcodeTextButton("Open in iCode", actions.open)
            }
        }
    }
}

/** The question the turn waits on, with its answers: an approval, or a pause. Shared words with the chat's cards. */
@Composable
private fun Question(assistant: AssistantState, dispatch: (ICodeIntent) -> Unit) {
    val approval = assistant.approval
    val pause = assistant.pause
    if (approval == null && pause == null) return
    Column(
        Modifier.fillMaxWidth().background(IcodeColors.SurfaceElevated, IcodeShapes.Small).padding(IcodeSpacing.Space2),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        val bound = AssistantPlace.bound(assistant.turn?.workspace ?: AssistantPlace.PHONE)
        Text(approval?.let { AssistantPermissions.question(it, bound) } ?: pause!!.kind.question, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
        Text(approval?.call?.summary ?: pause!!.detail, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
}

/** The target dragging shows at the bottom: dropped on it, the bubble is put away with the glow (§5.7). */
@Composable
internal fun PhoneBubbleTarget(working: Boolean, over: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        Box(
            Modifier.size(IcodeSizes.Bubble).background(if (over) IcodeColors.Error else IcodeColors.SurfaceElevated, IcodeShapes.Pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = null, tint = if (over) IcodeColors.OnAccent else IcodeColors.TextPrimary, modifier = Modifier.size(IcodeSizes.IconLg))
        }
        Text(if (working) "Stop and hide" else "Hide", style = IcodeType.Caption.copy(color = IcodeColors.TextPrimary))
    }
}
