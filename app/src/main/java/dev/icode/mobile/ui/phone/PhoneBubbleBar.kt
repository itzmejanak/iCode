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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFloatingPill
import dev.icode.mobile.designsystem.IcodeMenuAction
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
 * The bubble's bar (owner's design, 2026-11-03; redesigned 2026-11-04): the bubble expanded into one
 * floating pill where it sat, or, from a side, in the middle of the bottom by the keyboard — the mark
 * that collapses it back, the field, the microphone, Send — or Stop while the agent works. The field
 * grows with what is typed up to [BAR_LINES]. On the pill's tab, rising at its end as the owner drew
 * it: past [BAR_LINES] Expand, which makes the field an editor of [EDITOR_LINES], and ⋮, which opens
 * New chat and Open in iCode in the tab itself — no popup, which would be a touch outside the bar's
 * window and close it. Above, only when there is something to say: what the agent is doing, the
 * question it waits on with its answers, why the microphone heard nothing, or why the last turn
 * failed, with Retry. Anchored at its bottom, all of it grows upward.
 */
@Composable
internal fun PhoneBubbleBar(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    var editor by remember { mutableStateOf(false) }
    var lines by remember { mutableIntStateOf(1) }
    val overlay = view.overlay
    val draft = view.draft
    Column(
        Modifier.widthIn(max = overlayPanelWidth()),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        Notes(view, actions)
        IcodeFloatingPill(
            Modifier.fillMaxWidth(),
            tab = listOfNotNull(
                IcodeMenuAction(if (editor) "Collapse" else "Expand", if (editor) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull, { editor = !editor })
                    .takeIf { editor || lines > BAR_LINES },
            ),
            menu = listOf(
                IcodeMenuAction("New chat", Icons.Outlined.Add, { actions.dispatch(ICodeIntent.NewAssistantChat) }, enabled = !overlay.working),
                IcodeMenuAction("Open in iCode", Icons.AutoMirrored.Outlined.OpenInNew, actions.open),
            ),
        ) {
            val bottom = Modifier.align(Alignment.Bottom)
            BarMark(view, actions, bottom)
            BasicTextField(
                value = draft,
                onValueChange = view.onDraft,
                modifier = Modifier.weight(1f).padding(vertical = IcodeSpacing.Space2),
                textStyle = IcodeType.Body.copy(color = IcodeColors.TextPrimary),
                minLines = if (editor) EDITOR_LINES else 1,
                maxLines = if (editor) EDITOR_LINES else BAR_LINES,
                onTextLayout = { lines = it.lineCount },
                cursorBrush = SolidColor(IcodeColors.Accent),
                decorationBox = { field ->
                    Box {
                        if (draft.isEmpty()) Text("Ask iCode anything…", style = IcodeType.Body.copy(color = IcodeColors.TextMuted), maxLines = 1)
                        field()
                    }
                },
            )
            BarMic(view, actions, bottom)
            when {
                overlay.working && draft.isBlank() -> OverlayButton(Icons.Outlined.Stop, "Stop", IcodeColors.Error, bottom) { actions.dispatch(ICodeIntent.StopAssistant) }
                view.voice.speaking && draft.isBlank() -> OverlayButton(Icons.Outlined.StopCircle, "Stop reading aloud", IcodeColors.Accent, bottom) { actions.dispatch(VoiceIntent.Silence) }
                else -> OverlayButton(Icons.AutoMirrored.Outlined.Send, "Send", IcodeColors.Accent, bottom, enabled = draft.isNotBlank() && !overlay.working, onClick = actions.send)
            }
        }
    }
}

/**
 * The bar's leading mark: the bubble itself, shrunken into the pill as far in from its end as Send's
 * icon is from the other. A tap collapses the bar, or, muted, resumes listening; a drag frees the bubble.
 */
@Composable
private fun BarMark(view: PhoneBubbleView, actions: PhoneBubbleActions, modifier: Modifier) {
    Box(
        modifier.size(IcodeSizes.TouchTarget)
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
        Box(Modifier.size(IcodeSizes.IconXl)) {
            IcodeBrandMark(Modifier.fillMaxSize().clip(IcodeShapes.Pill))
            if (view.overlay.asking) Box(Modifier.align(Alignment.TopEnd)) { StatusDot(IcodeColors.Amber) }
        }
    }
}

/**
 * The bar's microphone, wearing the states the bubble shows — the agent's first, then its own: the
 * waveform while it listens or reads aloud, the microphone off when muted, the spinner thinking, and
 * the spinner round a hand acting.
 */
@Composable
private fun BarMic(view: PhoneBubbleView, actions: PhoneBubbleActions, modifier: Modifier) {
    IconButton(onClick = actions.listen, modifier = modifier) {
        Box(Modifier.size(IcodeSizes.IconXl), contentAlignment = Alignment.Center) {
            when (view.overlay.look) {
                Look.Idle -> Icon(Icons.Outlined.Mic, "Speak", tint = IcodeColors.TextSecondary, modifier = Modifier.fillMaxSize())
                Look.Muted -> Icon(Icons.Outlined.MicOff, "Speak", tint = IcodeColors.TextSecondary, modifier = Modifier.fillMaxSize())
                Look.Listening, Look.Speaking -> Waveform(IcodeColors.Accent)
                Look.Thinking -> Spinner(IcodeColors.Accent)
                Look.Acting -> {
                    Spinner(IcodeColors.Accent)
                    Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
                }
            }
        }
    }
}

/** What the bar has to say above it, in one card; nothing at all when there is nothing. */
@Composable
private fun Notes(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    val assistant = view.assistant
    val overlay = view.overlay
    val problem = view.voice.problem(VoiceFrom.Bubble)
    val failure = assistant.failure?.takeIf { !overlay.working }
    val step = overlay.step?.takeIf { overlay.working && !overlay.asking }
    if (problem == null && failure == null && step == null && !overlay.asking) return
    Column(
        Modifier.fillMaxWidth().background(IcodeColors.SurfaceElevated, IcodeShapes.Small).padding(IcodeSpacing.Space2),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        step?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (overlay.asking) Question(view, actions)
        if (problem != null) HeardLine(view.voice, VoiceFrom.Bubble)
        failure?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IcodeTextButton("Retry", { actions.dispatch(ICodeIntent.RetryAssistantMessage) })
            }
        }
    }
}

/**
 * The question the turn waits on, with its answers: an approval, or a pause. Shared words with the chat's
 * cards. An MCP server's question shows its first line: its form is answered in iCode.
 */
@Composable
private fun Question(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    val dispatch = actions.dispatch
    val assistant = view.assistant
    val approval = assistant.approval
    val pause = assistant.pause
    val asked = view.overlay.question?.takeIf { approval == null && pause == null }
    val bound = AssistantPlace.name(assistant.turn?.workspace ?: assistant.workspace.orEmpty())
    val title = approval?.let { AssistantPermissions.question(it, bound) } ?: pause?.kind?.question ?: asked?.let { "${it.server} asks" }
    Text(title.orEmpty(), style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
    Text(
        approval?.call?.summary ?: pause?.detail ?: asked?.message?.lineSequence()?.firstOrNull().orEmpty(),
        style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
        if (approval != null) {
            val answer = { it: AssistantAnswer -> dispatch(ICodeIntent.AnswerAssistant(approval.call.id, it)) }
            SecondaryButton("Deny", { answer(AssistantAnswer.Deny) }, compact = true)
            AssistantPermissions.allowIn(approval, bound)?.let { SecondaryButton(it, { answer(AssistantAnswer.AllowInChat) }, compact = true) }
            PrimaryButton(AssistantPermissions.allowOnce(approval), { answer(AssistantAnswer.Allow) }, compact = true)
        } else if (pause != null) {
            SecondaryButton("Stop", { dispatch(ICodeIntent.StopAssistant) }, compact = true)
            PrimaryButton(pause.kind.proceed, { dispatch(ICodeIntent.ContinueAssistant) }, compact = true)
        } else if (asked != null) {
            PrimaryButton("Open in iCode", actions.open, compact = true)
        }
    }
}

/** The lines the bar's field grows to before it scrolls; past them, expand opens the editor. */
private const val BAR_LINES = 4

/** The editor's lines, expanded: room to read over a long message before it goes. */
private const val EDITOR_LINES = 12
