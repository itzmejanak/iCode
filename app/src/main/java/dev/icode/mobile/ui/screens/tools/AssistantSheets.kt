package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeChoiceRow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.mark

/**
 * The workspace's chats, newest first: open one, delete one, or start another.
 */
@Composable
internal fun AssistantChatsSheet(state: AssistantState, modifier: Modifier, onClose: () -> Unit) {
    val gateway = LocalICodeGateway.current
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
                    gateway.dispatch(ICodeIntent.NewAssistantChat)
                    onClose()
                },
                Modifier.fillMaxWidth(),
            )
        },
    ) {
        if (state.sessions.size > SEARCH_FROM) Search(query, "Search chats") { query = it }
        if (shown.isEmpty()) Hint(if (state.sessions.isEmpty()) "No chats in ${AssistantPlace.name(state.workspace)} yet" else "No chat matches")
        shown.forEach { session ->
            val working = state.turnOf(state.workspace, session.id) != null
            IcodeListRow(
                title = session.title,
                subtitle = if (working) "Working…" else RelativeTime.since(session.updatedAt),
                subtitleStyle = IcodeType.Caption,
                selected = session.id == state.session,
                onClick = {
                    gateway.dispatch(ICodeIntent.OpenAssistantSession(state.workspace, session.id))
                    onClose()
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { gateway.dispatch(ICodeIntent.ExportAssistantSession(session.id)) }, enabled = !working) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = "Share ${session.title}",
                                tint = if (working) IcodeColors.TextMuted else IcodeColors.TextSecondary,
                                modifier = Modifier.size(IcodeSizes.IconMd),
                            )
                        }
                        IconButton(onClick = { deleting = session }, enabled = !working) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete ${session.title}",
                                tint = if (working) IcodeColors.TextMuted else IcodeColors.TextSecondary,
                                modifier = Modifier.size(IcodeSizes.IconMd),
                            )
                        }
                    }
                },
            )
        }
    }
    deleting?.let { session ->
        IcodeConfirmSheet(
            title = "Delete this chat?",
            message = "“${session.title}” is deleted from this phone. Every change the assistant made stays in the files.",
            confirmLabel = "Delete",
            onConfirm = {
                gateway.dispatch(ICodeIntent.DeleteAssistantSession(session.id))
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * A model to choose, from every provider: grouped by provider, each said with what it
 * reads and whether it reasons; [showEffort] adds the chosen one's reasoning effort above them;
 * [reset] sets the hope back to what the tool uses by default. Chosen and chosen-for decide what
 * picking does (the chat then, a commit message in Source Control's settings).
 */
@Composable
internal fun AssistantModelsSheet(
    state: AssistantState,
    modifier: Modifier,
    onClose: () -> Unit,
    onProviders: () -> Unit,
    title: String = "Model",
    chosen: (AssistantChoice) -> Boolean = { it == state.choice },
    onChoose: ((AssistantChoice) -> Unit)? = null,
    showEffort: Boolean = true,
    /** A row that means "no choice" — the tool's default — shown above the providers. */
    reset: String? = null,
    resetChosen: Boolean = false,
    onReset: (() -> Unit)? = null,
    /** Coding Agents, where an agent not installed is: the chat's sheet only, with its Agents tab. */
    onAgents: (() -> Unit)? = null,
) {
    val gateway = LocalICodeGateway.current
    val settings = state.settings
    val pick: (AssistantChoice) -> Unit = onChoose
        ?: { choice -> gateway.dispatch(ICodeIntent.ChooseAssistantModel(choice.provider, choice.model)) }
    var query by rememberSaveable { mutableStateOf("") }
    // Models or Agents: the tab of who answers now (docs/assistant-and-mcp.md).
    var agentsTab by rememberSaveable { mutableStateOf(state.answering is AssistantAgent) }
    val typed = query.trim()
    val count = settings.providers.sumOf { it.models.size }
    val groups = settings.providers
        .map { provider -> provider to provider.models.filter { typed.isEmpty() || it.id.contains(typed, ignoreCase = true) } }
        .filter { (_, models) -> models.isNotEmpty() }
    IcodeBottomSheet(
        title = title,
        onClose = onClose,
        modifier = modifier,
        footer = {
            SecondaryButton(
                "Manage Providers",
                {
                    onClose()
                    onProviders()
                },
                Modifier.fillMaxWidth(),
            )
        },
    ) {
        if (onAgents != null) {
            SegmentedControl(values = listOf(MODELS, AGENTS), active = if (agentsTab) AGENTS else MODELS, modifier = Modifier.fillMaxWidth()) { agentsTab = it == AGENTS }
            if (agentsTab) return@IcodeBottomSheet AgentRows(state, onClose, onAgents)
        }
        if (showEffort) {
            state.model?.takeIf { it.efforts.isNotEmpty() }?.let { model ->
                // How much it reasons before answering: the provider's levels for this model, or its default.
                SectionLabel("Reasoning")
                val choices = listOf<AssistantEffort?>(null) + model.efforts
                SegmentedControl(
                    values = choices.map { it?.label ?: AUTO },
                    active = AssistantRules.effortFor(settings.effort, model)?.label ?: AUTO,
                    modifier = Modifier.fillMaxWidth(),
                ) { label -> gateway.dispatch(ICodeIntent.ChooseAssistantEffort(choices.first { (it?.label ?: AUTO) == label })) }
            }
        }
        if (reset != null && onReset != null) {
            IcodeListRow(
                title = reset,
                titleStyle = IcodeType.Mono,
                titleColor = if (resetChosen) IcodeColors.Accent else IcodeColors.TextPrimary,
                subtitle = settings.model?.let { "Now ${it.id}" },
                subtitleStyle = IcodeType.Caption,
                selected = resetChosen,
                onClick = {
                    onReset()
                    onClose()
                },
                trailing = {
                    if (resetChosen) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
                },
            )
        }
        if (count > SEARCH_FROM) Search(query, "Search models", literal = true) { query = it }
        groups.forEachIndexed { index, (provider, models) ->
            // Each provider under its own mark, as the chat names who answers (docs/assistant-and-mcp.md).
            Row(
                if (index > 0 || state.model?.efforts?.isNotEmpty() == true || (reset != null && onReset != null)) Modifier.padding(top = IcodeSpacing.SectionBreak) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
            ) {
                Icon(provider.kind.mark(), contentDescription = null, tint = IcodeColors.TextPrimary, modifier = Modifier.size(IcodeSizes.IconSm))
                SectionLabel(provider.name)
            }
            models.forEach { model ->
                ModelRow(model, chosen(AssistantChoice(provider.id, model.id))) {
                    pick(AssistantChoice(provider.id, model.id))
                    onClose()
                }
            }
        }
        if (groups.isEmpty()) Hint(if (count == 0) "No models yet: add one to a provider" else "No model matches")
    }
}

/** A model to choose: its ID, what is known of it, a tick on the chosen one. */
@Composable
private fun ModelRow(model: AssistantModel, chosen: Boolean, onChoose: () -> Unit) {
    IcodeListRow(
        title = model.id,
        titleStyle = IcodeType.Mono,
        titleColor = if (chosen) IcodeColors.Accent else IcodeColors.TextPrimary,
        subtitle = AssistantRules.modelSummary(model).ifEmpty { null },
        subtitleStyle = IcodeType.Caption,
        selected = chosen,
        onClick = onChoose,
        trailing = {
            if (chosen) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
        },
    )
}

@Composable
internal fun Search(query: String, placeholder: String, literal: Boolean = false, onQuery: (String) -> Unit) {
    IcodeTextField(query, onQuery, Modifier.fillMaxWidth(), placeholder = placeholder, leadingIcon = Icons.Outlined.Search, literal = literal)
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted), modifier = Modifier.padding(vertical = IcodeSpacing.Space2))
}

/** Chats and models are searched once there are more than fit a sheet. */
private const val SEARCH_FROM = 8

private const val MODELS = "Models"
private const val AGENTS = "Agents"
