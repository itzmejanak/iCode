package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeOverflowMenu
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/**
 * The assistant (docs/assistant-koog-plan.md §3): the open workspace's chat — what was said, each
 * step taken, the question it waits on — and what to ask next. Without a provider it explains
 * itself and offers its settings; nothing else in iCode asks for one.
 */
@Composable
fun AssistantScreen(actions: NavigationActions) {
    val gateway = LocalWorkbenchGateway.current
    val snapshot = LocalWorkbenchSnapshot.current
    val state = snapshot.assistant
    // The open workspace's chat, again when the workspace changes under the page.
    LaunchedEffect(snapshot.activeWorkspaceLocation) { gateway.dispatch(WorkbenchIntent.OpenAssistant) }
    var confirmingNewChat by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Assistant",
            subtitle = listOfNotNull(state.workspace?.let(AssistantRules::workspaceName), state.settings?.model).joinToString(" · ").ifEmpty { null },
            onBack = actions.back,
            actions = {
                IcodeOverflowMenu(
                    contentDescription = "More options for the assistant",
                    actions = listOf(
                        IcodeMenuAction("New Chat", Icons.Outlined.AddComment, { confirmingNewChat = true },
                            enabled = state.messages.isNotEmpty() && !state.runningHere),
                        IcodeMenuAction("Assistant Settings", Icons.Outlined.Settings, { actions.navigate(Route.AssistantSettings) }),
                    ),
                )
            },
        )
        when {
            state.settings == null -> AssistantSetUp(Modifier.weight(1f)) { actions.navigate(Route.AssistantSettings) }
            state.workspace == null -> EmptyState(
                title = "Open a workspace",
                description = "The assistant works in the open workspace: its files, its Git, its terminal.",
                icon = Icons.Outlined.AutoAwesome,
                modifier = Modifier.weight(1f).padding(IcodeSpacing.Space6),
            )
            else -> AssistantChat(state, gateway, Modifier.weight(1f))
        }
    }
    if (confirmingNewChat) {
        IcodeConfirmDialog(
            title = "Start a new chat?",
            message = "This workspace's chat is cleared. Every change the assistant made stays in its files.",
            confirmLabel = "New Chat",
            onConfirm = {
                gateway.dispatch(WorkbenchIntent.NewAssistantChat)
                confirmingNewChat = false
            },
            onDismiss = { confirmingNewChat = false },
        )
    }
}

@Composable
private fun AssistantChat(state: AssistantState, gateway: WorkbenchGateway, modifier: Modifier) {
    val entries = remember(state) { AssistantRules.entries(state) }
    val list = rememberLazyListState()
    // The newest stays in view as it is written.
    LaunchedEffect(entries.size, state.streaming.length, state.failure) {
        if (list.layoutInfo.totalItemsCount > 0) list.scrollToItem(list.layoutInfo.totalItemsCount - 1)
    }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(IcodeSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            if (entries.isEmpty()) item(key = "intro") { AssistantIntro(AssistantRules.workspaceName(state.workspace.orEmpty())) }
            items(entries, key = { it.key }) { entry -> AssistantEntryRow(entry) }
            state.failure?.let { failure ->
                item(key = "failure") {
                    NoticeCard(IcodeTone.Error, Modifier.fillMaxWidth(), title = "The assistant stopped", icon = Icons.Outlined.ErrorOutline) {
                        Text(failure, style = IcodeType.Caption)
                    }
                }
            }
        }
        state.turn?.let { turn ->
            Column(Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Space3), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                if (state.runningHere) IndeterminateProgressTrack(Modifier.fillMaxWidth())
                Text(
                    if (state.runningHere) turn.step else "Working in ${turn.workspaceName}: ${turn.step}",
                    style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
                )
            }
        }
        state.approval?.takeIf { state.runningHere }?.let { approval ->
            AssistantApprovalCard(approval, Modifier.padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space2)) { answer ->
                gateway.dispatch(WorkbenchIntent.AnswerAssistant(approval.call.id, answer))
            }
        }
        AssistantComposer(state, gateway)
    }
}

/** What to ask; while it works, Stop. Only one turn runs at a time, in whichever workspace. */
@Composable
private fun AssistantComposer(state: AssistantState, gateway: WorkbenchGateway) {
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = text.isNotBlank() && !state.running
    Row(
        Modifier.fillMaxWidth().padding(IcodeSpacing.Space3),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        IcodeTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = "Ask about this workspace",
            textStyle = IcodeType.Body,
            singleLine = false,
        )
        if (state.runningHere) {
            IconButton(onClick = { gateway.dispatch(WorkbenchIntent.StopAssistant) }) {
                Icon(Icons.Outlined.StopCircle, contentDescription = "Stop", tint = IcodeColors.Error)
            }
        } else {
            IconButton(
                onClick = {
                    gateway.dispatch(WorkbenchIntent.SendAssistantMessage(text))
                    text = ""
                },
                enabled = canSend,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = if (canSend) IcodeColors.Accent else IcodeColors.TextMuted)
            }
        }
    }
}
