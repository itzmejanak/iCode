package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeCheckbox
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.SearchEngineState
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.screens.workspace.rankFiles

/** The workspace's chats, newest first: open one, delete one, or start another. */
@Composable
internal fun AssistantChatsSheet(state: AssistantState, modifier: Modifier, onClose: () -> Unit) {
    val gateway = LocalWorkbenchGateway.current
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<AssistantSession?>(null) }
    val shown = state.sessions.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    IcodeBottomSheet(
        title = "Chats",
        onClose = onClose,
        modifier = modifier,
        footer = {
            PrimaryButton(
                "New Chat",
                {
                    gateway.dispatch(WorkbenchIntent.NewAssistantChat)
                    onClose()
                },
                Modifier.fillMaxWidth(),
            )
        },
    ) {
        if (state.sessions.size > SEARCH_FROM) Search(query, "Search chats") { query = it }
        if (shown.isEmpty()) Hint(if (state.sessions.isEmpty()) "No chats in this workspace yet" else "No chat matches")
        shown.forEach { session ->
            val working = state.turn?.let { it.workspace == state.workspace && it.session == session.id } == true
            IcodeListRow(
                title = session.title,
                subtitle = if (working) "Working…" else RelativeTime.since(session.updatedAt),
                subtitleStyle = IcodeType.Caption,
                selected = session.id == state.session,
                onClick = {
                    gateway.dispatch(WorkbenchIntent.OpenAssistantSession(session.id))
                    onClose()
                },
                trailing = {
                    IconButton(onClick = { deleting = session }, enabled = !working) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete ${session.title}",
                            tint = if (working) IcodeColors.TextMuted else IcodeColors.TextSecondary,
                            modifier = Modifier.size(IcodeSizes.IconMd),
                        )
                    }
                },
            )
        }
    }
    deleting?.let { session ->
        IcodeConfirmDialog(
            title = "Delete this chat?",
            message = "“${session.title}” is deleted from this phone. Every change the assistant made stays in the files.",
            confirmLabel = "Delete",
            onConfirm = {
                gateway.dispatch(WorkbenchIntent.DeleteAssistantSession(session.id))
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * The model's reasoning effort, where it has one; then the provider's models — the ones used before
 * first — or any ID typed; the provider's settings a tap away.
 */
@Composable
internal fun AssistantModelsSheet(state: AssistantState, modifier: Modifier, onClose: () -> Unit, onProvider: () -> Unit) {
    val gateway = LocalWorkbenchGateway.current
    val settings = state.settings ?: return
    var query by rememberSaveable { mutableStateOf("") }
    val typed = query.trim()
    val catalogue = state.catalogue[settings.kind].orEmpty()
    fun matches(id: String) = typed.isEmpty() || id.contains(typed, ignoreCase = true)
    val recent = state.recentModels.filter(::matches)
    val listed = catalogue.filter { it.id !in state.recentModels && matches(it.id) }
    fun choose(id: String) {
        gateway.dispatch(WorkbenchIntent.ChooseAssistantModel(id))
        onClose()
    }
    IcodeBottomSheet(
        title = "Model",
        onClose = onClose,
        modifier = modifier,
        footer = {
            SecondaryButton(
                "${settings.kind.label} Settings",
                {
                    onClose()
                    onProvider()
                },
                Modifier.fillMaxWidth(),
            )
        },
    ) {
        state.model?.takeIf { it.efforts.isNotEmpty() }?.let { model ->
            // How much it reasons before answering: the provider's levels for this model, or its default.
            SectionLabel("Reasoning")
            val choices = listOf<AssistantEffort?>(null) + model.efforts
            SegmentedControl(
                values = choices.map { it?.label ?: AUTO },
                active = AssistantRules.effortFor(settings.effort, model)?.label ?: AUTO,
                modifier = Modifier.fillMaxWidth(),
            ) { label -> gateway.dispatch(WorkbenchIntent.ChooseAssistantEffort(choices.first { (it?.label ?: AUTO) == label })) }
        }
        Search(query, "Search, or type a model ID", literal = true) { query = it }
        if (typed.isNotEmpty() && typed.none(Char::isWhitespace) && catalogue.none { it.id == typed } && typed !in state.recentModels) {
            IcodeListRow(title = "Use “$typed”", subtitle = "A model ${settings.kind.label} serves", subtitleStyle = IcodeType.Caption, onClick = { choose(typed) })
        }
        if (recent.isNotEmpty()) {
            SectionLabel("Recent")
            recent.forEach { id -> ModelRow(id, catalogue.firstOrNull { it.id == id }, id == settings.model) { choose(id) } }
        }
        if (listed.isNotEmpty()) {
            SectionLabel("${settings.kind.label} models", Modifier.padding(top = IcodeSpacing.Space2))
            listed.forEach { model -> ModelRow(model.id, model, model.id == settings.model) { choose(model.id) } }
        }
        if (catalogue.isEmpty() && recent.isEmpty()) Hint("Type the ID of a model your server serves")
    }
}

@Composable
private fun ModelRow(id: String, known: AssistantModel?, selected: Boolean, onClick: () -> Unit) {
    val detail = listOfNotNull(
        known?.contextWindow?.let { "${AssistantRules.tokenCount(it)} context" },
        "reasons".takeIf { known?.efforts?.isNotEmpty() == true },
    ).joinToString(" · ").ifEmpty { null }
    IcodeListRow(title = id, titleStyle = IcodeType.Mono, subtitle = detail, subtitleStyle = IcodeType.Caption, selected = selected, onClick = onClick)
}

/** The workspace's files, as Quick Open finds them, the open one first: up to five go with the message. */
@Composable
internal fun AssistantFilesSheet(
    snapshot: WorkbenchSnapshot,
    attached: List<String>,
    modifier: Modifier,
    onClose: () -> Unit,
    onAttached: (List<String>) -> Unit,
) {
    val gateway = LocalWorkbenchGateway.current
    val root = snapshot.activeWorkspaceLocation
    // As Quick Open does: the index is asked for when it is needed, not before.
    LaunchedEffect(root) { if (root != null) gateway.dispatch(WorkbenchIntent.PrepareWorkspaceSearch) }
    var query by rememberSaveable { mutableStateOf("") }
    val ranked = remember(snapshot.fileIndex, query) { rankFiles(snapshot.fileIndex, query) }
    val full = attached.size >= AssistantRules.MAX_ATTACHMENTS
    val open = snapshot.activeDocument?.takeIf { query.isBlank() && root != null && it.location.startsWith("$root/") }
    fun pathOf(location: String) = if (root != null) location.removePrefix("$root/") else location
    @Composable
    fun FileRow(name: String, location: String) {
        val chosen = location in attached
        IcodeListRow(
            title = name,
            subtitle = pathOf(location),
            onClick = { if (chosen) onAttached(attached - location) else if (!full) onAttached(attached + location) },
            leading = { IcodeCheckbox(chosen, onCheckedChange = null, enabled = chosen || !full) },
        )
    }
    IcodeBottomSheet(
        title = "Attach Files",
        onClose = onClose,
        modifier = modifier,
        footer = {
            PrimaryButton(if (attached.isEmpty()) "Done" else "Done · ${attached.size} of ${AssistantRules.MAX_ATTACHMENTS}", onClose, Modifier.fillMaxWidth())
        },
    ) {
        Search(query, "Search files", literal = true) { query = it }
        open?.let { document ->
            SectionLabel("Open file")
            FileRow(document.name, document.location)
        }
        if (ranked.entries.isNotEmpty()) SectionLabel(if (query.isBlank()) "Files" else "Matching files", Modifier.padding(top = IcodeSpacing.Space2))
        ranked.entries.forEach { file -> FileRow(file.name, file.path) }
        when {
            ranked.truncated -> Hint("More files match: narrow the search")
            ranked.entries.isNotEmpty() -> Unit
            else -> Hint(
                when (val engine = snapshot.searchEngine) {
                    SearchEngineState.Installing -> "Installing the file index…"
                    SearchEngineState.Indexing, SearchEngineState.Unknown -> "Indexing the workspace…"
                    is SearchEngineState.Failed -> engine.reason
                    is SearchEngineState.Ready -> if (query.isBlank()) "This workspace has no files" else "No file matches"
                },
            )
        }
    }
}

@Composable
private fun Search(query: String, placeholder: String, literal: Boolean = false, onQuery: (String) -> Unit) {
    IcodeTextField(query, onQuery, Modifier.fillMaxWidth(), placeholder = placeholder, leadingIcon = Icons.Outlined.Search, literal = literal)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted), modifier = Modifier.padding(vertical = IcodeSpacing.Space2))
}

/** Chats are searched once there are more than fit a sheet. */
private const val SEARCH_FROM = 8
