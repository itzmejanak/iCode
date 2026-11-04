package dev.icode.mobile.ui.phone

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeColors
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
    /** The bar's microphone: the bar gives way to the voice bubble, listening. */
    val listen: () -> Unit,
    /** The bar's Send: what was typed goes to the chat iCode shows. */
    val send: () -> Unit,
    /** The whole chat, in iCode. */
    val open: () -> Unit,
    /** The voice bubble's microphone: listening stops, the words wait in the bar; the agent runs on. */
    val mute: () -> Unit,
    /** The voice bubble's Stop: the voice session ends and nothing heard is kept. */
    val cancel: () -> Unit,
)

/** What the bubble's window shows now. */
internal class PhoneBubbleView(
    val overlay: PhoneOverlay,
    val assistant: AssistantState,
    val voice: VoiceState,
    /** The bar is open: the bubble expanded into it. */
    val bar: Boolean,
    /** The voice bubble is up: the bar gave way to it. */
    val voiceUp: Boolean,
    val tucked: Boolean,
    val draft: String,
    val onDraft: (String) -> Unit,
)

/**
 * The bubble over other apps (docs/phone-agent-plan.md §5.7; redesigned 2026-11-04, after Gemini):
 * one small bubble anchored at the bottom of the screen, which expands into a compact bar to type in,
 * and while the microphone listens becomes a voice bubble — the words heard so far under a waveform,
 * with a microphone to mute and a Stop to throw the hearing away. Nothing else: no header, no cards
 * of actions, no dashboard.
 */
@Composable
internal fun PhoneBubble(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    val face = if (view.voiceUp) Face.Voice else if (view.bar) Face.Bar else Face.Bubble
    AnimatedContent(
        targetState = face,
        transitionSpec = {
            (fadeIn(tween(MORPH_MILLIS)) + scaleIn(initialScale = MORPH_SCALE, animationSpec = tween(MORPH_MILLIS))) togetherWith
                (fadeOut(tween(MORPH_MILLIS / 2)) + scaleOut(targetScale = MORPH_SCALE, animationSpec = tween(MORPH_MILLIS / 2)))
        },
        label = "face",
    ) { target ->
        when (target) {
            Face.Bubble -> Mark(view, actions)
            Face.Bar -> PhoneBubbleBar(view, actions)
            Face.Voice -> PhoneVoiceBubble(view, actions)
        }
    }
}

/** The window's three faces: the bubble, the bar it expands into, and the voice bubble. */
private enum class Face { Bubble, Bar, Voice }

/** How wide the bar and the voice bubble may grow: the panel, or the screen less its margins. */
@Composable
internal fun overlayPanelWidth(): Dp =
    minOf(IcodeSizes.BubblePanel, (LocalConfiguration.current.screenWidthDp.dp - IcodeSpacing.Space4 * 2).coerceAtLeast(IcodeSizes.Bubble))

/**
 * The bubble itself (§5.7): the iCode mark at its smallest, growing a ring, a spinner or a waveform as
 * the agent and the microphone get to work — pressed, it gives a little; dragged, it goes free.
 */
@Composable
private fun Mark(view: PhoneBubbleView, actions: PhoneBubbleActions) {
    val look = view.overlay.look
    val scale by animateFloatAsState(if (look == Look.Listening) LISTENING_SCALE else 1f, label = "listening")
    val fade by animateFloatAsState(if (view.tucked) TUCKED_ALPHA else 1f, label = "tucked")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, label = "pressed")
    Box(
        Modifier.size(IcodeSizes.Bubble)
            .graphicsLayer {
                scaleX = scale * press
                scaleY = scale * press
                alpha = fade
            }
            .semantics { contentDescription = describe(look) + if (view.overlay.asking) ", a question waits" else "" }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = actions.dragEnd, onDragCancel = actions.dragEnd) { change, amount ->
                    change.consume()
                    actions.drag(amount.x, amount.y)
                }
            }
            .clickable(interactionSource = source, indication = null) { actions.tap() },
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
                Spinner(IcodeColors.Accent)
                IcodeBrandMark(Modifier.padding(IcodeSizes.BubbleRing + IcodeSpacing.Space1).fillMaxSize().clip(IcodeShapes.Pill))
            }
            Look.Acting -> {
                Spinner(IcodeColors.Accent)
                Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
            }
            Look.Speaking -> Waveform(IcodeColors.Accent)
        }
        if (view.overlay.asking) Box(Modifier.align(Alignment.TopEnd)) { StatusDot(IcodeColors.Amber) }
    }
}

@Composable
internal fun Ring(color: Color) {
    Box(Modifier.fillMaxSize().border(IcodeSizes.BubbleRing, color, IcodeShapes.Pill))
}

@Composable
internal fun Spinner(color: Color) {
    CircularProgressIndicator(Modifier.fillMaxSize(), color = color, strokeWidth = IcodeSizes.BubbleRing)
}

/** Bars rising and falling while it listens or speaks; still where the system's animations are off. */
@Composable
internal fun Waveform(
    color: Color,
    height: Dp = IcodeSizes.Waveform,
    barWidth: Dp = IcodeSizes.WaveformBar,
    bars: List<Float> = BARS,
) {
    val moving = remember { ValueAnimator.areAnimatorsEnabled() }
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(WAVE_MILLIS, easing = LinearEasing)),
        label = "phase",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro), verticalAlignment = Alignment.CenterVertically) {
        bars.forEachIndexed { index, level ->
            val share = if (moving) level * (WAVE_FLOOR + (1 - WAVE_FLOOR) * sin(phase + index * WAVE_STEP).absoluteValue) else level
            Box(Modifier.width(barWidth).height(height * share).background(color, IcodeShapes.Pill))
        }
    }
}

/**
 * An icon button in the bar or the voice bubble, at the touch target a thumb needs: its icon
 * [IcodeSpacing.IconButtonInset] in from its edges, as what leads a pill is from the other end.
 */
@Composable
internal fun OverlayButton(icon: ImageVector, description: String, tint: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(icon, contentDescription = description, tint = if (enabled) tint else IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconXl))
    }
}

/** The target dragging shows at the top of the screen: dropped on it, the bubble is put away with the glow (§5.7). */
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

internal fun describe(look: Look): String = when (look) {
    Look.Idle -> "iCode assistant"
    Look.Listening -> "Listening, tap to mute"
    Look.Muted -> "Microphone muted, tap to resume"
    Look.Thinking -> "Assistant thinking"
    Look.Acting -> "Assistant acting on the phone"
    Look.Speaking -> "Reading aloud, tap to stop"
}

private const val LISTENING_SCALE = 1.1f
private const val PRESSED_SCALE = 0.94f
private const val TUCKED_ALPHA = 0.55f
private const val MORPH_MILLIS = 220
private const val MORPH_SCALE = 0.88f
private const val WAVE_MILLIS = 1_100
private const val WAVE_FLOOR = 0.35f
private const val WAVE_STEP = 1.3f

/** The waveform's bars, as a share of its height: low at the ends, high in the middle. */
private val BARS = listOf(0.45f, 0.75f, 1f, 0.75f, 0.45f)
