package dev.icode.mobile.designsystem

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A sheet swiped down ([IcodeBottomSheet]): how far it is pulled, and the [modifier] that pulls it —
 * by a drag on the sheet itself, or by what its scrolling content leaves over at its top.
 */
internal class SheetSwipe(private val scope: CoroutineScope, private val close: () -> Unit, private val flingPx: Float) {
    private var pulled by mutableFloatStateOf(0f)
    private var height by mutableIntStateOf(0)

    private fun pull(delta: Float): Float {
        val before = pulled
        pulled = (pulled + delta).coerceAtLeast(0f)
        return pulled - before
    }

    /** Let go at [velocity] (px/s, down positive): away past a quarter of its height or flung down, else back. */
    fun settle(velocity: Float) {
        if (pulled == 0f && velocity <= flingPx) return
        scope.launch {
            if (pulled > height * CLOSE_SHARE || velocity > flingPx) {
                animate(pulled, height.toFloat(), animationSpec = tween(AWAY_MILLIS)) { value, _ -> pulled = value }
                close()
                // Kept open by its owner after all: it comes back.
                pulled = 0f
            } else {
                animate(pulled, 0f, animationSpec = spring()) { value, _ -> pulled = value }
            }
        }
    }

    private val content = object : NestedScrollConnection {
        // Pulled down, a drag back up lifts the sheet before the content scrolls.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0 && pulled > 0f) Offset(0f, pull(available.y)) else Offset.Zero

        // The content at its top: the rest of a drag down pulls the sheet. A fling's rest does not.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0 && source == NestedScrollSource.UserInput) Offset(0f, pull(available.y)) else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (pulled == 0f) return Velocity.Zero
            settle(available.y)
            return available
        }
    }

    val modifier: Modifier @Composable get() = Modifier
        .onSizeChanged { height = it.height }
        .offset { IntOffset(0, pulled.roundToInt()) }
        .nestedScroll(content)
        .draggable(rememberDraggableState { pull(it) }, Orientation.Vertical, onDragStopped = { settle(it) })

    private companion object {
        const val CLOSE_SHARE = 0.25f
        const val AWAY_MILLIS = 180
    }
}

@Composable
internal fun rememberSheetSwipe(onClose: () -> Unit): SheetSwipe {
    val close by rememberUpdatedState(onClose)
    val scope = rememberCoroutineScope()
    val flingPx = with(LocalDensity.current) { FLING.toPx() }
    return remember(scope, flingPx) { SheetSwipe(scope, { close() }, flingPx) }
}

/** A flick down faster than this closes the sheet however little it moved (Material's sheets: 125 dp/s). */
private val FLING = 125.dp
