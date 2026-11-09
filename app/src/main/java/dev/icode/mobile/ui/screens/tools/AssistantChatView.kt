package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import dev.icode.mobile.designsystem.JumpToLatestButton
import dev.icode.mobile.designsystem.LocalCodeTextScale
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PinchToScaleText
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.scaledBy
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import kotlinx.coroutines.launch

/**
 * The chat: what was said and done — [rows], each run of steps one — pinched larger or smaller with
 * two fingers as the editor is; then what the turn is doing and the question it waits on. A row with
 * more behind it [onOpen]s it in a sheet, at a step for the question's details; a long press on one
 * that offers actions [onHold]s it, for its actions' sheet. A chat with nothing
 * in it yet is the home screen's: the logo, and what to ask.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssistantChatView(
    state: AssistantState,
    rows: List<AssistantEntry>,
    modifier: Modifier,
    onSuggestion: (String) -> Unit,
    onOpen: (row: String, step: String?) -> Unit,
    onHold: (row: String) -> Unit,
) {
    val gateway = LocalICodeGateway.current
    Column(modifier) {
        if (rows.isEmpty() && state.failure == null) {
            AssistantWelcome(state, Modifier.weight(1f), onSuggestion)
        } else {
            val count = rows.size + if (state.failure != null) 1 else 0
            // Each chat opens laid out at its end: never scrolled down through all it holds, drawing
            // every reply on the way, as it was.
            val list = remember(state.workspace, state.session) { LazyListState((count - 1).coerceAtLeast(0)) }
            // At the end, the list follows what comes; scrolled up to read, it stays, and ↓ brings it back.
            var following by remember(list) { mutableStateOf(true) }
            // The list's own glide to the end is not the user scrolling away from it.
            var gliding by remember(list) { mutableStateOf(false) }
            LaunchedEffect(list) {
                snapshotFlow { list.isScrollInProgress to list.canScrollForward }.collect { (moving, more) ->
                    when {
                        gliding -> Unit
                        moving -> following = !more
                        // At rest at the end: whatever brought it there, what comes next is followed.
                        !more -> following = true
                    }
                }
            }
            // A run of steps grows in its row: each step it takes is followed as a new row is.
            val steps = (rows.lastOrNull() as? AssistantEntry.Steps)?.steps?.size
            LaunchedEffect(list, rows.size, steps, state.failure, state.turn?.step) {
                if (following) list.scrollToEnd()
            }
            // What the user just sent is shown, wherever they were, and what answers it followed.
            val sent = rows.lastOrNull { it is AssistantEntry.Said }?.key
            LaunchedEffect(list, sent) {
                following = true
                list.scrollToEnd()
            }
            val written = state.turn?.live?.let { it.reasoning.length + it.text.length } ?: 0
            LaunchedEffect(written) {
                if (written > 0 && following) list.scrollToEnd()
            }
            // The keyboard's arrival shrinks the viewport; scroll to the end once, on each time it
            // appears, so the latest stays visible. Its insets animate — only the on-going state
            // is watched, or the list would be re-jumped to the end at every frame of the rise.
            val isImeVisible = WindowInsets.isImeVisible
            LaunchedEffect(isImeVisible) {
                if (isImeVisible && following) list.scrollToEnd()
            }
            val scope = rememberCoroutineScope()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PinchToScaleText(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = list,
                        // The page's gutter; its entries a conversation's, not a page's groups.
                        contentPadding = PaddingValues(IcodeSpacing.Gutter),
                        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
                    ) {
                        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                            // The answer a turn ends on; one still being worked on may yet go on to a step.
                            val ends = rows.getOrNull(index + 1) !is AssistantEntry.Steps && !(index == rows.lastIndex && state.runningHere)
                            AssistantEntryRow(
                                row,
                                copyable = ends,
                                streaming = index == rows.lastIndex && state.runningHere,
                                onOpen = { onOpen(row.key, null) },
                                onHold = { onHold(row.key) },
                            )
                        }
                        state.failure?.let { failure ->
                            item(key = "failure") {
                                val rateLimited = failure.contains("limiting requests", ignoreCase = true) || failure.contains("rate limit", ignoreCase = true)
                                val tone = if (rateLimited) IcodeTone.Warning else IcodeTone.Error
                                val title = if (rateLimited) "Rate limited — retried" else "The assistant stopped"
                                val icon = Icons.Outlined.ErrorOutline
                                NoticeCard(tone, Modifier.fillMaxWidth(), title = title, icon = icon) {
                                    Text(failure, style = IcodeType.Caption.scaledBy(LocalCodeTextScale.current))
                                    if (!state.running && state.messages.lastOrNull() is AssistantMessage.User) {
                                        SecondaryButton("Try Again", { gateway.dispatch(ICodeIntent.RetryAssistantMessage) })
                                    }
                                }
                            }
                        }
                    }
                }
                JumpToLatestButton(
                    visible = !following,
                    contentDescription = "Jump to the latest",
                    onClick = {
                        following = true
                        scope.launch {
                            gliding = true
                            try {
                                list.glideToEnd()
                            } finally {
                                gliding = false
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(IcodeSpacing.Space3),
                )
            }
        }
        state.turn?.let { turn ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Gutter, vertical = IcodeSpacing.Space1),
                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
            ) {
                if (state.runningHere) IndeterminateProgressTrack(Modifier.fillMaxWidth())
                Text(
                    if (state.runningHere) turn.step else "Working in another chat of ${turn.workspaceName}: ${turn.step}",
                    style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
                    maxLines = 1,
                )
            }
        }
        state.approval?.takeIf { state.runningHere }?.let { approval ->
            // Its details are its step's page, in the sheet of the run it is in.
            val asked = rows.firstNotNullOfOrNull { row ->
                (row as? AssistantEntry.Steps)?.steps?.firstOrNull { it.call.id == approval.call.id }?.let { row.key to it.key }
            }
            // Its gap to the composer is the composer's own top padding: one, not two.
            AssistantApprovalCard(
                approval,
                AssistantPlace.name(state.workspace.orEmpty()),
                Modifier.padding(start = IcodeSpacing.Space3, end = IcodeSpacing.Space3, top = IcodeSpacing.Space2),
                onDetails = asked?.let { (row, step) -> { onOpen(row, step) } },
            )
        }
        state.pause?.takeIf { state.runningHere }?.let { pause ->
            AssistantPauseCard(pause, Modifier.padding(start = IcodeSpacing.Space3, end = IcodeSpacing.Space3, top = IcodeSpacing.Space2))
        }
        state.clarification?.takeIf { state.runningHere }?.let { clarification ->
            AssistantQuestionsCard(clarification, Modifier.padding(start = IcodeSpacing.Space3, end = IcodeSpacing.Space3, top = IcodeSpacing.Space2))
        }
        LocalICodeSnapshot.current.mcp.question?.takeIf { state.runningHere }?.let { question ->
            McpQuestionCard(question, Modifier.padding(start = IcodeSpacing.Space3, end = IcodeSpacing.Space3, top = IcodeSpacing.Space2))
        }
    }
}

/**
 * To the end, smoothly: a glide when the last item is in view, a jump from further up, where a glide
 * would draw everything between.
 */
private suspend fun LazyListState.glideToEnd() {
    val last = layoutInfo.visibleItemsInfo.lastOrNull()
    if (last == null || last.index != layoutInfo.totalItemsCount - 1) return scrollToEnd()
    val below = last.offset + last.size - (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding)
    if (below > 0) animateScrollBy(below.toFloat())
}

/** To the end of the last item: a long one shows its end, not its start. Each step measures the list anew. */
private suspend fun LazyListState.scrollToEnd() {
    val count = layoutInfo.totalItemsCount
    if (count == 0) return
    scrollToItem(count - 1)
    val last = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val below = last.offset + last.size - (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding)
    if (below > 0) scrollBy(below.toFloat())
}
