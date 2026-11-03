package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeOverflowMenu
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.designsystem.IcodeWatermark
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantStepRules
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * What is drawn over the chat: its history, the model picker, what to attach, the processes it
 * started, what a row of it holds (docs/assistant-sheets-plan.md §2), or what a row offers, from a
 * long press (docs/explorer-gaps-plan.md §3.1).
 */
internal enum class AssistantSheet { Chats, Models, Attach, Processes, Entry, Actions }

/**
 * The assistant (docs/assistant-koog-plan.md §3, §12): a chat of the open workspace — VS Code's
 * Chat view on a phone. The top bar names the chat and opens its history; the composer holds the
 * message, its files, the mode, the model, the effort and the permissions. Without a provider it
 * explains itself and offers its settings; nothing else in iCode asks for one.
 */
@Composable
fun AssistantScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val snapshot = LocalICodeSnapshot.current
    val state = snapshot.assistant
    // The open workspace's chats, again when the workspace changes under the page.
    LaunchedEffect(snapshot.activeWorkspaceLocation) { gateway.dispatch(ICodeIntent.OpenAssistant) }
    var sheet by rememberSaveable { mutableStateOf<AssistantSheet?>(null) }
    // The chat's rows, each run of steps one: drawn, and opened in a sheet by key, at a step of a run.
    val rows = remember(state) { AssistantStepRules.rows(AssistantRules.entries(state)) }
    var entryKey by rememberSaveable(state.workspace, state.session) { mutableStateOf<String?>(null) }
    var stepKey by rememberSaveable(state.workspace, state.session) { mutableStateOf<String?>(null) }
    val entry = rows.firstOrNull { it.key == entryKey }
    // What a sheet showed has gone — another chat, a compaction — and its sheet with it.
    LaunchedEffect(sheet, entry == null) { if ((sheet == AssistantSheet.Entry || sheet == AssistantSheet.Actions) && entry == null) sheet = null }
    // As Source Control opens a change: its diff, a step forward from the chat.
    val onDiff = { change: GitChange ->
        gateway.dispatch(ICodeIntent.OpenDiff(change.path, change.staged))
        actions.navigate(Route.DiffView)
    }
    // The message being written and the files going with it: this page's own until sent.
    var draft by rememberSaveable(state.workspace) { mutableStateOf("") }
    var attached by rememberSaveable(state.workspace, stateSaver = DRAFT_SAVER) { mutableStateOf(emptyList<DraftFile>()) }
    // Up to five, each once, in the order chosen; any past five are said to stay behind.
    val attach = { files: List<DraftFile> ->
        val all = (attached + files).distinctBy { it.location }
        if (all.size > AssistantRules.MAX_ATTACHMENTS) {
            gateway.dispatch(NoticeIntent.Notify(NoticeTone.HeadsUp, "Five files go with a message at most: ${all.size - AssistantRules.MAX_ATTACHMENTS} not attached"))
        }
        attached = all.take(AssistantRules.MAX_ATTACHMENTS)
    }
    val pickers = rememberAssistantPickers(onPicked = attach)
    val ready = state.settings.ready && state.workspace != null
    val title = state.sessions.firstOrNull { it.id == state.session }?.title ?: "New chat"

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(sheet != null)) {
            IcodeTopBar(
                if (ready) title else "Assistant",
                subtitle = state.workspace?.let(AssistantPlace::name),
                onBack = actions.back,
                actions = {
                    if (ready) {
                        IconButton(onClick = { sheet = AssistantSheet.Chats }) {
                            Icon(Icons.Outlined.History, contentDescription = "Chats", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconMd))
                        }
                        IconButton(onClick = { gateway.dispatch(ICodeIntent.NewAssistantChat) }, enabled = state.session != null) {
                            Icon(
                                Icons.Outlined.EditNote,
                                contentDescription = "New chat",
                                tint = if (state.session != null) IcodeColors.TextSecondary else IcodeColors.TextMuted,
                                modifier = Modifier.size(IcodeSizes.IconMd),
                            )
                        }
                    }
                    IcodeOverflowMenu(
                        contentDescription = "More options for the assistant",
                        actions = listOfNotNull(
                            IcodeMenuAction(
                                "Compact Chat", Icons.Outlined.Compress, { gateway.dispatch(ICodeIntent.CompactAssistantChat) },
                                enabled = ready && state.messages.isNotEmpty() && !state.running,
                            ).takeIf { ready },
                            IcodeMenuAction("Assistant Settings", Icons.Outlined.Settings, { actions.navigate(Route.AssistantSettings) }),
                        ),
                    )
                },
            )
            when {
                !state.settings.ready -> AssistantSetUp(Modifier.weight(1f)) { actions.navigate(Route.AssistantProviders) }
                // A workspace's chats are always shown once the page has opened.
                state.workspace == null -> Box(Modifier.weight(1f)) { IcodeWatermark(Modifier.align(Alignment.Center)) }
                else -> AssistantChatView(
                    state,
                    rows,
                    Modifier.weight(1f),
                    onSuggestion = { draft = it },
                    onOpen = { row, step ->
                        entryKey = row
                        stepKey = step
                        sheet = AssistantSheet.Entry
                    },
                    onHold = { row ->
                        entryKey = row
                        sheet = AssistantSheet.Actions
                    },
                )
            }
            // Above the composer on every state of the page: a process outlives the chat that started it.
            if (snapshot.processes.isNotEmpty()) AssistantProcessesRow(snapshot.processes) { sheet = AssistantSheet.Processes }
            if (ready) {
                AssistantComposer(
                    state = state,
                    draft = draft,
                    onDraft = { draft = it },
                    attached = attached,
                    onDetach = { file -> attached = attached - file },
                    onAttach = { sheet = AssistantSheet.Attach },
                    onModels = { sheet = AssistantSheet.Models },
                    onSend = { aloud ->
                        gateway.dispatch(ICodeIntent.SendAssistantMessage(draft, attached.map { it.location }, aloud))
                        draft = ""
                        attached = emptyList()
                    },
                    // The open workspace's tree, a step forward: Back is the chat again.
                    onWorkspace = { actions.navigate(Route.WorkspaceExplorer) },
                )
            }
        }
        sheet?.let { open ->
            val close = { sheet = null }
            IcodeSheetOverlay(onClose = close) {
                val modifier = Modifier.align(Alignment.BottomCenter)
                when (open) {
                    AssistantSheet.Chats -> AssistantChatsSheet(state, modifier, close)
                    AssistantSheet.Models -> AssistantModelsSheet(state, modifier, close, onProviders = { actions.navigate(Route.AssistantProviders) })
                    AssistantSheet.Attach -> AssistantAttachSheet(snapshot, attached, pickers, modifier, close, onAttached = { attached = it })
                    AssistantSheet.Processes -> AssistantProcessesSheet(snapshot.processes, state.workspace, modifier, close)
                    AssistantSheet.Entry -> entry?.let {
                        val approval = state.approval?.takeIf { state.runningHere }
                        AssistantEntrySheet(it, stepKey, approval, AssistantPlace.name(state.workspace.orEmpty()), modifier, close, onDiff)
                    }
                    AssistantSheet.Actions -> entry?.let { AssistantActionsSheet(it, modifier, close, onDiff) }
                }
            }
        }
    }
}

/** The files going with a draft, through a configuration change: three strings each. */
private val DRAFT_SAVER = listSaver<List<DraftFile>, String>(
    save = { files -> files.flatMap { listOf(it.location, it.name, it.kind.name) } },
    restore = { saved -> saved.chunked(3).map { (location, name, kind) -> DraftFile(location, name, AssistantAttachmentKind.valueOf(kind)) } },
)
