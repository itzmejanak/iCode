package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * The tab strip of the editor's documents and the terminal's sessions (layout.md §13.1): tabs
 * scrolling sideways, the selected one on the editor's background and brought into view, each with
 * its [mark] — drawn in the title's colour unless it has its own — its title and a close button.
 * A long press that moves reorders; one that does not goes to [onPress] (the tab's sheet).
 */
@Composable
fun <T> IcodeTabStrip(
    tabs: List<T>,
    keyOf: (T) -> String,
    title: (T) -> String,
    /** Spoken for the tab: its title, its state, and what a long press does. */
    description: (T) -> String,
    selected: String?,
    onSelect: (String) -> Unit,
    onReorder: (moving: String, target: String) -> Unit,
    onClose: (String) -> Unit,
    onPress: ((String) -> Unit)? = null,
    mark: @Composable (tab: T, color: Color) -> Unit = { _, _ -> },
    /** A tab whose close was tapped and is not done: the spinner in its ×'s place, and no second tap. */
    closing: (T) -> Boolean = { false },
) {
    val scrollState = rememberScrollState()
    val tabBounds = remember { mutableMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragStartCenterX by remember { mutableFloatStateOf(0f) }
    val keys = tabs.map(keyOf)
    Row(
        Modifier.fillMaxWidth().height(IcodeSizes.TerminalTabBar).background(IcodeColors.Surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(scrollState), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                val id = keyOf(tab)
                key(id) {
                    val bringIntoView = remember { BringIntoViewRequester() }
                    LaunchedEffect(selected, tabs.size) {
                        if (selected == id) bringIntoView.bringIntoView()
                    }
                    val titleColor = if (id == selected) IcodeColors.TextPrimary else IcodeColors.TextSecondary
                    Row(
                        modifier = Modifier
                            .zIndex(if (dragging == id) 1f else 0f)
                            .graphicsLayer { translationX = if (dragging == id) dragOffsetX else 0f }
                            .onGloballyPositioned { tabBounds[id] = it.boundsInWindow() }
                            .bringIntoViewRequester(bringIntoView)
                            .height(IcodeSizes.TerminalTabBar)
                            .background(if (id == selected) IcodeColors.BackgroundDeep else IcodeColors.Surface)
                            .clickable { onSelect(id) }
                            .padding(start = IcodeSpacing.Space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(IcodeSizes.TerminalTabBar)
                                .pointerInput(id, keys) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragging = id
                                            dragOffsetX = 0f
                                            dragStartCenterX = tabBounds[id]?.center?.x ?: 0f
                                        },
                                        onDragEnd = {
                                            if (onPress != null && abs(dragOffsetX) < viewConfiguration.touchSlop) {
                                                onPress(id)
                                            } else {
                                                val dropX = dragStartCenterX + dragOffsetX
                                                val target = keys.minByOrNull { other ->
                                                    val center = if (other == id) dragStartCenterX else tabBounds[other]?.center?.x ?: Float.POSITIVE_INFINITY
                                                    abs(center - dropX)
                                                }
                                                if (target != null && target != id) onReorder(id, target)
                                            }
                                            dragging = null
                                            dragOffsetX = 0f
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            dragOffsetX = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffsetX += amount.x
                                        },
                                    )
                                }
                                .semantics { contentDescription = description(tab) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                                mark(tab, titleColor)
                                Text(title(tab), style = IcodeType.Mono.copy(color = titleColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val closingTab = closing(tab)
                        Box(
                            modifier = Modifier
                                .width(IcodeSizes.TouchTarget)
                                .height(IcodeSizes.TerminalTabBar)
                                .clickable(enabled = !closingTab) { onClose(id) }
                                .semantics { contentDescription = "Close ${title(tab)}" }
                                .busySemantics(closingTab),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (rememberShownWhileBusy(closingTab)) IcodeSpinner()
                            else Icon(Icons.Outlined.Close, contentDescription = null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
                        }
                    }
                }
            }
        }
    }
}
