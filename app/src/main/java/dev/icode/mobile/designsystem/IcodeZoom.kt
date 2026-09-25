package dev.icode.mobile.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified

/** How much a pinch has scaled the code text inside [PinchToScaleText]; 1 everywhere else. */
val LocalCodeTextScale = compositionLocalOf { 1f }

private const val MIN_CODE_SCALE = 0.6f
private const val MAX_CODE_SCALE = 2.5f

/**
 * Two fingers pinch the code text drawn inside [content] larger or smaller —
 * [MonoLine] reads [LocalCodeTextScale] — as the editor and the terminal zoom.
 * Only multi-touch is taken, and taken before the list sees it, so one finger
 * still scrolls and taps still reach buttons. The scale lasts while the screen
 * is open.
 */
@Composable
fun PinchToScaleText(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) scale = (scale * zoom).coerceIn(MIN_CODE_SCALE, MAX_CODE_SCALE)
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
        CompositionLocalProvider(LocalCodeTextScale provides scale, content = content)
    }
}

/** [this] with its size and line height multiplied by [factor]. */
fun TextStyle.scaledBy(factor: Float): TextStyle = if (factor == 1f) this else copy(
    fontSize = fontSize * factor,
    lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
)
