package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IcodeWatermark
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import dev.icode.mobile.designsystem.LocalCodeTextScale
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PinchToScaleText
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.scaledBy
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.ui.LocalWorkbenchGateway

/**
 * The chat: what was said and done, pinched larger or smaller with two fingers as the editor is;
 * then what the turn is doing and the question it waits on. A chat with nothing in it yet is the
 * home screen's: the logo, and what to ask.
 */
@Composable
internal fun AssistantChatView(state: AssistantState, modifier: Modifier, onSuggestion: (String) -> Unit) {
    val gateway = LocalWorkbenchGateway.current
    val entries = remember(state) { AssistantRules.entries(state) }
    Column(modifier) {
        if (entries.isEmpty() && state.failure == null) {
            AssistantWelcome(state, Modifier.weight(1f), onSuggestion)
        } else {
            val list = rememberLazyListState()
            // The newest stays in view as it comes.
            LaunchedEffect(entries.size, state.failure, state.turn?.step) {
                if (list.layoutInfo.totalItemsCount > 0) list.animateScrollToItem(list.layoutInfo.totalItemsCount - 1)
            }
            PinchToScaleText(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = list,
                    contentPadding = PaddingValues(IcodeSpacing.Space3),
                    verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                        // The answer a turn ends on; one still being worked on may yet go on to a step.
                        val ends = entries.getOrNull(index + 1) !is AssistantEntry.Step && !(index == entries.lastIndex && state.runningHere)
                        AssistantEntryRow(entry, copyable = ends)
                    }
                    state.failure?.let { failure ->
                        item(key = "failure") {
                            NoticeCard(IcodeTone.Error, Modifier.fillMaxWidth(), title = "The assistant stopped", icon = Icons.Outlined.ErrorOutline) {
                                Text(failure, style = IcodeType.Caption.scaledBy(LocalCodeTextScale.current))
                                // A message no reply came to: sent again, its files with it, once the cause is fixed.
                                if (!state.running && state.messages.lastOrNull() is AssistantMessage.User) {
                                    SecondaryButton("Try Again", { gateway.dispatch(WorkbenchIntent.RetryAssistantMessage) })
                                }
                            }
                        }
                    }
                }
            }
        }
        state.turn?.let { turn ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space1),
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
            AssistantApprovalCard(approval, Modifier.padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space2)) { answer ->
                gateway.dispatch(WorkbenchIntent.AnswerAssistant(approval.call.id, answer))
            }
        }
    }
}

/** An empty chat: the logo over its letterpress, as on the home screen, and a few things to ask. */
@Composable
private fun AssistantWelcome(state: AssistantState, modifier: Modifier, onSuggestion: (String) -> Unit) {
    val workspace = AssistantRules.workspaceName(state.workspace.orEmpty())
    val asking = state.settings.mode == AssistantMode.Ask
    Box(modifier.fillMaxWidth()) {
        IcodeWatermark(Modifier.align(Alignment.Center))
        Column(
            Modifier.align(Alignment.Center).widthIn(max = IcodeSizes.ContentMaxWidth).padding(IcodeSpacing.Space4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            EmptyState(
                title = if (asking) "Ask about $workspace" else "Build with the assistant",
                description = if (asking) {
                    "It reads this workspace to answer, and changes nothing."
                } else {
                    "It reads and changes $workspace's files and runs commands in its terminal${if (state.settings.askBeforeChanges) ", asking first" else ""}."
                },
                mark = { IcodeBrandMark(Modifier.size(IcodeSizes.MarkLg)) },
            )
            (if (asking) ASK_SUGGESTIONS else AGENT_SUGGESTIONS).forEach { suggestion ->
                IcodeListRow(title = suggestion, card = true, onClick = { onSuggestion(suggestion) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val AGENT_SUGGESTIONS = listOf(
    "Explain how this project is organized",
    "Review my uncommitted changes",
    "Add a README for this project",
)

private val ASK_SUGGESTIONS = listOf(
    "Explain how this project is organized",
    "Where does the app start?",
    "What do my uncommitted changes do?",
)
