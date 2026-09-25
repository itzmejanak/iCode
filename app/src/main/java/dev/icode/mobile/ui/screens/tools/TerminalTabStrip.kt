package dev.icode.mobile.ui.screens.tools

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.TerminalSessionState
import dev.icode.mobile.domain.TerminalStatus
import kotlin.math.abs

/** Full-width, swipeable tab strip with long-press drag reordering on each label. */
@Composable
internal fun TerminalTabStrip(
    sessions: List<TerminalSessionState>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onReorder: (String, String) -> Unit,
    onClose: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val tabBounds = remember { mutableMapOf<String, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragStartCenterX by remember { mutableFloatStateOf(0f) }
    Row(
        modifier = Modifier.fillMaxWidth().height(IcodeSizes.TerminalTabBar).background(IcodeColors.Surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sessions.forEach { session ->
                key(session.id) {
                    val bringIntoView = remember { BringIntoViewRequester() }
                    LaunchedEffect(selectedId, sessions.size) {
                        if (selectedId == session.id) bringIntoView.bringIntoView()
                    }
                    Row(
                        modifier = Modifier
                            .zIndex(if (draggingId == session.id) 1f else 0f)
                            .graphicsLayer {
                                translationX = if (draggingId == session.id) dragOffsetX else 0f
                            }
                            .onGloballyPositioned { tabBounds[session.id] = it.boundsInWindow() }
                            .bringIntoViewRequester(bringIntoView)
                            .height(IcodeSizes.TerminalTabBar)
                            .background(
                                if (session.id == selectedId) IcodeColors.BackgroundDeep else IcodeColors.Surface,
                            )
                            .clickable { onSelect(session.id) }
                            .padding(start = IcodeSpacing.Space3, end = IcodeSpacing.Space1),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(IcodeSizes.TerminalTabBar)
                                .pointerInput(session.id, sessions.map { it.id }) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = session.id
                                            dragOffsetX = 0f
                                            dragStartCenterX = tabBounds[session.id]?.center?.x ?: 0f
                                        },
                                        onDragEnd = {
                                            val dropX = dragStartCenterX + dragOffsetX
                                            val targetId = sessions.minByOrNull { tab ->
                                                val center = if (tab.id == session.id) {
                                                    dragStartCenterX
                                                } else {
                                                    tabBounds[tab.id]?.center?.x ?: Float.POSITIVE_INFINITY
                                                }
                                                abs(center - dropX)
                                            }?.id
                                            if (targetId != null && targetId != session.id) onReorder(session.id, targetId)
                                            draggingId = null
                                            dragOffsetX = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffsetX = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffsetX += amount.x
                                        },
                                    )
                                }
                                .semantics { contentDescription = "${session.title}, long press and drag to reorder" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${statusMark(session)} ${session.title}",
                                style = IcodeType.Mono.copy(color = statusColor(session)),
                                maxLines = 1,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(IcodeSizes.TouchTarget)
                                .height(IcodeSizes.TerminalTabBar)
                                .clickable { onClose(session.id) }
                                .semantics { contentDescription = "Close ${session.title}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("\u00D7", style = IcodeType.Title.copy(color = IcodeColors.TextSecondary))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColor(session: TerminalSessionState) = when (session.status) {
    TerminalStatus.Running -> IcodeColors.Green
    TerminalStatus.Exited, TerminalStatus.Failed -> IcodeColors.Error
}

/** Status is carried by a glyph as well as colour, never colour alone. */
private fun statusMark(session: TerminalSessionState): String = when (session.status) {
    TerminalStatus.Running -> "●"
    TerminalStatus.Exited, TerminalStatus.Failed -> "✖"
}
