package dev.icode.mobile.ui.phone

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFloatingPill
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.phone.PhoneOverlay
import dev.icode.mobile.domain.phone.PhoneOverlay.Look
import dev.icode.mobile.domain.voice.VoiceState
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.sin

/** What the bubble does, as its window and the backend answer it. */
internal class PhoneBubbleActions(
    /** The bubble tapped: what that does follows its state (§5.7). */
    val tap: () -> Unit,
    val drag: (dx: Float, dy: Float) -> Unit,
    val dragEnd: () -> Unit,
    val dispatch: (ICodeIntent) -> Unit,
    /** The bar's microphone: the bar gives way to the bubble, listening. */
    val listen: () -> Unit,
    /** The bar's Send: what was typed goes to the chat iCode shows. */
    val send: () -> Unit,
    /** The whole chat, in iCode. */
    val open: () -> Unit,
)

/** What the bubble's window shows now. */
internal class PhoneBubbleView(
    val overlay: PhoneOverlay,
    val assistant: AssistantState,
    val voice: VoiceState,
    /** The bar is open beside the bubble. */
    val bar: Boolean,
    val dockRight: Boolean,
    val tucked: Boolean,
    val draft: String,
    val onDraft: (String) -> Unit,
)

/**
 * The bubble over other apps (docs/phone-agent-plan.md §5.7, owner's design 2026-11-03): one small,
 * movable bubble that shows the agent's state, the bar beside it — towards the middle — when text can be
 * typed, and while it listens the words heard so far. Nothing else: no header, no cards of actions.
 */
@Composable
internal fun PhoneBubble(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    val words = view.voice.heard?.text?.takeIf { view.overlay.look == Look.Listening && it.isNotBlank() }
    val side: (@Composable RowScope.() -> Unit)? = when {
        view.bar -> { { PhoneBubbleBar(view, actions) } }
        words != null -> { { Caption(words) } }
        else -> null
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        if (view.dockRight) side?.invoke(this)
        Mark(view, actions)
        if (!view.dockRight) side?.invoke(this)
    }
}

@Composable
private fun Mark(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    val look = view.overlay.look
    val scale by animateFloatAsState(if (look == Look.Listening) LISTENING_SCALE else 1f, label = "listening")
    val fade by animateFloatAsState(if (view.tucked) TUCKED_ALPHA else 1f, label = "tucked")
    Box(
        Modifier.size(IcodeSizes.Bubble)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = fade
            }
            .semantics { contentDescription = describe(look) + if (view.overlay.asking) ", a question waits" else "" }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = actions.dragEnd, onDragCancel = actions.dragEnd) { change, amount ->
                    change.consume()
                    actions.drag(amount.x, amount.y)
                }
            }
            .clickable { actions.tap() },
        contentAlignment = Alignment.Center,
    ) {
        if (look == Look.Idle) {
            IcodeBrandMark(Modifier.fillMaxSize().clip(IcodeShapes.Pill))
        } else {
            Box(Modifier.fillMaxSize().clip(IcodeShapes.Pill).background(IcodeColors.SurfaceElevated))
        }
        when (look) {
            Look.Idle -> Unit
            Look.Listening -> {
                Ring(IcodeColors.Accent)
                Waveform(IcodeColors.Accent)
            }
            Look.Muted -> {
                Ring(IcodeColors.Outline)
                Icon(Icons.Outlined.MicOff, contentDescription = null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconLg))
            }
            Look.Thinking -> {
                Spinner()
                IcodeBrandMark(Modifier.padding(IcodeSizes.BubbleRing + IcodeSpacing.Space1).fillMaxSize().clip(IcodeShapes.Pill))
            }
            Look.Acting -> {
                Spinner()
                Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconLg))
            }
            Look.Speaking -> Waveform(IcodeColors.Accent)
        }
        if (view.overlay.asking) Box(Modifier.align(Alignment.TopEnd)) { StatusDot(IcodeColors.Amber) }
    }
}

@Composable
private fun Ring(color: Color) {
    Box(Modifier.fillMaxSize().border(IcodeSizes.BubbleRing, color, IcodeShapes.Pill))
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(Modifier.fillMaxSize(), color = IcodeColors.Accent, strokeWidth = IcodeSizes.BubbleRing)
}

/** Bars rising and falling while it listens or speaks; still where the system's animations are off. */
@Composable
private fun Waveform(color: Color) {
    val moving = remember { ValueAnimator.areAnimatorsEnabled() }
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(WAVE_MILLIS, easing = LinearEasing)),
        label = "phase",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro), verticalAlignment = Alignment.CenterVertically) {
        BARS.forEachIndexed { index, height ->
            val level = if (moving) height * (WAVE_FLOOR + (1 - WAVE_FLOOR) * sin(phase + index * WAVE_STEP).absoluteValue) else height
            Box(Modifier.width(IcodeSizes.WaveformBar).height(IcodeSizes.Waveform * level).background(color, IcodeShapes.Pill))
        }
    }
}

/** The words heard so far, beside the bubble while it listens: their end, where they grow. */
@Composable
private fun Caption(words: String) {
    IcodeFloatingPill(Modifier.widthIn(max = IcodeSizes.BubblePanel)) {
        Text(
            if (words.length > CAPTION_CHARS) "…" + words.takeLast(CAPTION_CHARS) else words,
            style = IcodeType.Body.copy(color = IcodeColors.TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = IcodeSpacing.Space3),
        )
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

private fun describe(look: Look): String = when (look) {
    Look.Idle -> "iCode assistant"
    Look.Listening -> "Listening, tap to mute"
    Look.Muted -> "Microphone muted, tap to resume"
    Look.Thinking -> "Assistant thinking"
    Look.Acting -> "Assistant acting on the phone"
    Look.Speaking -> "Reading aloud, tap to stop"
}

private const val LISTENING_SCALE = 1.1f
private const val TUCKED_ALPHA = 0.55f
private const val WAVE_MILLIS = 1_100
private const val WAVE_FLOOR = 0.35f
private const val WAVE_STEP = 1.3f
private const val CAPTION_CHARS = 48

/** The waveform's bars, as a share of its height: low at the ends, high in the middle. */
private val BARS = listOf(0.45f, 0.75f, 1f, 0.75f, 0.45f)
