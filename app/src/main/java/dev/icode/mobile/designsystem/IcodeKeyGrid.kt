package dev.icode.mobile.designsystem

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Keys in equal cells (the editor's and the terminal's keys rows, as their settings arrange them): as
 * many columns of [IcodeSizes.TouchTarget]-wide cells as fit, the cells stretched to fill the width,
 * so every key is the same size and the columns line up.
 *
 * With [onOrder], a key held is lifted and follows the finger, the others sliding out of its way, and
 * [onOrder] gets the new order when it is let go; a screen reader moves one with Move earlier and Move
 * later. [cell] draws each key with the modifier it is given; `held` is true for the lifted one, whose
 * tap is not a tap.
 */
@Composable
fun <T : Any> IcodeKeyGrid(
    keys: List<T>,
    onOrder: ((List<T>) -> Unit)?,
    modifier: Modifier = Modifier,
    cell: @Composable (key: T, modifier: Modifier, held: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reorder by rememberUpdatedState(onOrder)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val layout = with(density) {
            KeyGridLayout.fit(constraints.maxWidth, IcodeSizes.TouchTarget.roundToPx(), IcodeSizes.TouchTarget.roundToPx(), IcodeSpacing.Space1.roundToPx())
        }
        // The order drawn: [keys], or, while a key is held, where the drag has moved it to.
        val order = remember(keys) { mutableStateOf(keys) }
        var held by remember { mutableStateOf<T?>(null) }
        // The held key's top-left in the grid, following the finger.
        var lifted by remember { mutableStateOf(Offset.Zero) }
        val size = with(density) { Modifier.size(layout.cellWidth.toDp(), layout.cellHeight.toDp()) }
        Box(Modifier.fillMaxWidth().height(with(density) { layout.height(keys.size).toDp() })) {
            // Composed in a fixed order and placed by [order], so a held key stays the same node as it moves.
            keys.forEach { key ->
                key(key) {
                    val at = order.value.indexOf(key)
                    val isHeld = key == held
                    val place by animateIntOffsetAsState(IntOffset(layout.x(at), layout.y(at)), label = "key")
                    val arrange = if (onOrder == null) Modifier else Modifier
                        .pointerInput(key, layout, order) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val from = order.value.indexOf(key)
                                    lifted = Offset(layout.x(from).toFloat(), layout.y(from).toFloat())
                                    held = key
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    lifted += amount
                                    val from = order.value.indexOf(key)
                                    val to = layout.indexAt(lifted.x + layout.cellWidth / 2f, lifted.y + layout.cellHeight / 2f, keys.size)
                                    if (to != from) order.value = order.value.moved(from, to)
                                },
                                onDragEnd = {
                                    held = null
                                    if (order.value != keys) reorder?.invoke(order.value)
                                },
                                onDragCancel = {
                                    held = null
                                    order.value = keys
                                },
                            )
                        }
                        .semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction("Move earlier") { reorder?.invoke(keys.moved(at, at - 1)); true }.takeIf { at > 0 },
                                CustomAccessibilityAction("Move later") { reorder?.invoke(keys.moved(at, at + 1)); true }.takeIf { at < keys.lastIndex },
                            )
                        }
                    cell(
                        key,
                        Modifier
                            .offset { if (isHeld) lifted.round() else place }
                            .zIndex(if (isHeld) 1f else 0f)
                            .graphicsLayer { if (isHeld) { scaleX = LIFT; scaleY = LIFT } }
                            .then(size)
                            .then(arrange),
                        isHeld,
                    )
                }
            }
        }
    }
}

/**
 * Where [IcodeKeyGrid] puts its cells, in pixels: [columns] across, each [cellWidth] by [cellHeight],
 * [gap] apart.
 */
internal data class KeyGridLayout(val columns: Int, val cellWidth: Int, val cellHeight: Int, val gap: Int) {
    fun x(index: Int): Int = index % columns * (cellWidth + gap)
    fun y(index: Int): Int = index / columns * (cellHeight + gap)

    /** How tall [count] keys stand. */
    fun height(count: Int): Int = if (count == 0) 0 else (count + columns - 1) / columns * (cellHeight + gap) - gap

    /** The place, among [count], of the cell nearest a key whose middle is at ([x], [y]). */
    fun indexAt(x: Float, y: Float, count: Int): Int {
        val column = ((x - cellWidth / 2f) / (cellWidth + gap)).roundToInt().coerceIn(0, columns - 1)
        val row = ((y - cellHeight / 2f) / (cellHeight + gap)).roundToInt().coerceAtLeast(0)
        return (row * columns + column).coerceIn(0, count - 1)
    }

    companion object {
        /** As many columns of cells at least [minCell] wide as fit [width], each stretched so they fill it. */
        fun fit(width: Int, minCell: Int, cellHeight: Int, gap: Int): KeyGridLayout {
            val columns = max(1, (width + gap) / (minCell + gap))
            return KeyGridLayout(columns, (width - gap * (columns - 1)) / columns, cellHeight, gap)
        }
    }
}

/** This list with the item at [from] put at [to], those between shifting a place. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

/** How much a held key grows, lifted off the grid. */
private const val LIFT = 1.12f
