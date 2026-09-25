package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.OpenDocument
import kotlin.math.abs

/** The terminal's swipe/drag/close tab interaction, adapted to open documents. */
@Composable
internal fun DocumentTabStrip(
    documents: List<OpenDocument>,
    selectedLocation: String?,
    onSelect: (String) -> Unit,
    onReorder: (String, String) -> Unit,
    onClose: (String) -> Unit,
    onCloseOthers: () -> Unit,
    onCloseSaved: () -> Unit,
    onNewFile: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val tabBounds = remember { mutableMapOf<String, Rect>() }
    var draggingLocation by remember { mutableStateOf<String?>(null) }
    // A long press that never moves opens the tab's actions; one that moves
    // reorders. Both start from the same gesture, so which it was is only known
    // when the finger lifts.
    var menuLocation by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragStartCenterX by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier.fillMaxWidth().height(IcodeSizes.TerminalTabBar).background(IcodeColors.Surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(scrollState), verticalAlignment = Alignment.CenterVertically) {
            documents.forEach { document ->
                key(document.location) {
                    val bringIntoView = remember { BringIntoViewRequester() }
                    LaunchedEffect(selectedLocation, documents.size) {
                        if (selectedLocation == document.location) bringIntoView.bringIntoView()
                    }
                    Row(
                        modifier = Modifier
                            .zIndex(if (draggingLocation == document.location) 1f else 0f)
                            .graphicsLayer {
                                translationX = if (draggingLocation == document.location) dragOffsetX else 0f
                            }
                            .onGloballyPositioned { tabBounds[document.location] = it.boundsInWindow() }
                            .bringIntoViewRequester(bringIntoView)
                            .height(IcodeSizes.TerminalTabBar)
                            .background(if (document.location == selectedLocation) IcodeColors.BackgroundDeep else IcodeColors.Surface)
                            .clickable { onSelect(document.location) }
                            .padding(start = IcodeSpacing.Space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(IcodeSizes.TerminalTabBar)
                                .pointerInput(document.location, documents.map { it.location }) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingLocation = document.location
                                            dragOffsetX = 0f
                                            dragStartCenterX = tabBounds[document.location]?.center?.x ?: 0f
                                        },
                                        onDragEnd = {
                                            if (abs(dragOffsetX) < viewConfiguration.touchSlop) {
                                                menuLocation = document.location
                                            } else {
                                                val dropX = dragStartCenterX + dragOffsetX
                                                val target = documents.minByOrNull { tab ->
                                                    val center = if (tab.location == document.location) dragStartCenterX
                                                    else tabBounds[tab.location]?.center?.x ?: Float.POSITIVE_INFINITY
                                                    abs(center - dropX)
                                                }?.location
                                                if (target != null && target != document.location) {
                                                    onReorder(document.location, target)
                                                }
                                            }
                                            draggingLocation = null
                                            dragOffsetX = 0f
                                        },
                                        onDragCancel = {
                                            draggingLocation = null
                                            dragOffsetX = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffsetX += amount.x
                                        },
                                    )
                                }
                                .semantics {
                                    contentDescription = "${document.name}${if (document.dirty) ", unsaved" else ""}, long press for tab actions, or drag to reorder"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${if (document.dirty) "● " else ""}${document.name}",
                                style = IcodeType.Mono.copy(
                                    color = if (document.location == selectedLocation) IcodeColors.TextPrimary else IcodeColors.TextSecondary,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IcodeActionMenu(
                                expanded = menuLocation == document.location,
                                onDismiss = { menuLocation = null },
                                actions = tabActions(
                                    document = document,
                                    documents = documents,
                                    selectedLocation = selectedLocation,
                                    onClose = onClose,
                                    onCloseOthers = onCloseOthers,
                                    onCloseSaved = onCloseSaved,
                                    onNewFile = onNewFile,
                                ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(IcodeSizes.TouchTarget)
                                .height(IcodeSizes.TerminalTabBar)
                                .clickable { onClose(document.location) }
                                .semantics { contentDescription = "Close ${document.name}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("×", style = IcodeType.Title.copy(color = IcodeColors.TextSecondary))
                        }
                    }
                }
            }
        }
    }
}

/** The actions the tab overflow used to carry, now scoped to the pressed tab. */
private fun tabActions(
    document: OpenDocument,
    documents: List<OpenDocument>,
    selectedLocation: String?,
    onClose: (String) -> Unit,
    onCloseOthers: () -> Unit,
    onCloseSaved: () -> Unit,
    onNewFile: () -> Unit,
): List<IcodeMenuAction> = listOf(
    IcodeMenuAction(
        label = "New file",
        icon = Icons.Outlined.Add,
        onClick = onNewFile,
    ),
    IcodeMenuAction(
        label = "Close other tabs",
        icon = Icons.Outlined.Close,
        enabled = documents.size > 1 && selectedLocation != null,
        onClick = onCloseOthers,
    ),
    IcodeMenuAction(
        label = "Close saved tabs",
        icon = Icons.Outlined.Close,
        enabled = documents.any { !it.dirty },
        onClick = onCloseSaved,
    ),
    IcodeMenuAction(
        label = "Close ${document.name}",
        icon = Icons.Outlined.Close,
        destructive = true,
        onClick = { onClose(document.location) },
    ),
)
