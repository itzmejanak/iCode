package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeChoiceChip
import dev.icode.mobile.designsystem.IcodeChoiceMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.ui.LocalWorkbenchGateway
import java.io.File

/**
 * The composer, as VS Code's: the files going with the message, the message, and a toolbar — attach,
 * mode, model with its effort where it has one, how full the context is, send or stop. Under it,
 * where the assistant works and what it may do without asking.
 */
@Composable
internal fun AssistantComposer(
    state: AssistantState,
    draft: String,
    onDraft: (String) -> Unit,
    attached: List<String>,
    onDetach: (String) -> Unit,
    onAttach: () -> Unit,
    onModels: () -> Unit,
    onSend: () -> Unit,
) {
    val gateway = LocalWorkbenchGateway.current
    val settings = state.settings ?: return
    val canSend = draft.isNotBlank() && !state.running
    Column(
        Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space2),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        IcodeCard(Modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space2)) {
            if (attached.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                    attached.forEach { location -> AttachedFile(File(location).name) { onDetach(location) } }
                }
            }
            Box(Modifier.fillMaxWidth().padding(IcodeSpacing.Space1)) {
                if (draft.isEmpty()) {
                    Text(
                        if (settings.mode == AssistantMode.Ask) "Ask about ${AssistantRules.workspaceName(state.workspace.orEmpty())}" else "Describe what to build",
                        style = IcodeType.Body.copy(color = IcodeColors.TextMuted),
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = IcodeType.Body,
                    maxLines = MAX_LINES,
                    cursorBrush = SolidColor(IcodeColors.Accent),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The choices take what the meter and Send leave; a long model ID is cut short, never them.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onAttach, enabled = attached.size < AssistantRules.MAX_ATTACHMENTS) {
                        Icon(Icons.Outlined.Add, contentDescription = "Attach files", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconMd))
                    }
                    Choice(settings.mode.label, "Mode", AssistantMode.entries, settings.mode, { it.label }, { it.description }) {
                        gateway.dispatch(WorkbenchIntent.ChooseAssistantMode(it))
                    }
                    // The model and, where it has one, its reasoning effort: both chosen in the model sheet.
                    IcodeChoiceChip(modelLabel(state), onModels, Modifier.weight(1f, fill = false), role = "Model")
                }
                ContextMeter(state)
                if (state.runningHere) {
                    IconButton(onClick = { gateway.dispatch(WorkbenchIntent.StopAssistant) }) {
                        Icon(Icons.Outlined.StopCircle, contentDescription = "Stop", tint = IcodeColors.Error)
                    }
                } else {
                    IconButton(onClick = onSend, enabled = canSend) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = if (canSend) IcodeColors.Accent else IcodeColors.TextMuted)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
            Text(
                AssistantRules.workspaceName(state.workspace.orEmpty()),
                style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // Permissions are the chat's, chosen here as VS Code's are.
            Choice(
                if (settings.askBeforeChanges) ASK_FIRST else ALLOW_ALL, "Permissions", listOf(true, false), settings.askBeforeChanges,
                { if (it) ASK_FIRST else ALLOW_ALL },
                { if (it) "Each file change and command waits for Allow" else "Changes and commands run without asking" },
                icon = true,
            ) { gateway.dispatch(WorkbenchIntent.SetAssistantAskFirst(it)) }
        }
    }
}

/** "o3 · High": the model, and its effort ("Auto" for the provider's own) where it offers one. */
internal fun modelLabel(state: AssistantState): String {
    val settings = state.settings ?: return ""
    val model = state.model?.takeIf { it.efforts.isNotEmpty() } ?: return settings.model
    return "${settings.model} · ${AssistantRules.effortFor(settings.effort, model)?.label ?: AUTO}"
}

/** A toolbar choice and its menu, anchored to it. */
@Composable
private fun <T> Choice(
    label: String,
    role: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    optionDescription: (T) -> String?,
    icon: Boolean = false,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IcodeChoiceChip(label, { open = true }, icon = Icons.Outlined.Shield.takeIf { icon }, role = role)
        IcodeChoiceMenu(open, { open = false }, options, selected, optionLabel, onSelect, optionDescription)
    }
}

/** How full the model's context is after the last request; Compact a tap away. */
@Composable
private fun ContextMeter(state: AssistantState) {
    val gateway = LocalWorkbenchGateway.current
    val window = state.model?.contextWindow ?: return
    val share = AssistantRules.contextShare(state.contextTokens, window)
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.minimumInteractiveComponentSize().clip(IcodeShapes.Small).clickable(onClickLabel = "Context") { open = true }
                .padding(horizontal = IcodeSpacing.Space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        ) {
            CircularProgressIndicator(
                progress = { share ?: 0f },
                modifier = Modifier.size(IcodeSizes.IconSm),
                color = if ((share ?: 0f) >= AssistantRules.COMPACT_AT) IcodeColors.Amber else IcodeColors.Accent,
                trackColor = IcodeColors.SurfaceTrack,
                strokeWidth = IcodeSpacing.Micro,
            )
            if (share != null) Text("${(share * 100).toInt()}%", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
        }
        IcodeActionMenu(
            expanded = open,
            onDismiss = { open = false },
            actions = listOf(
                IcodeMenuAction(
                    state.contextTokens?.let { "${AssistantRules.tokenCount(it)} of ${AssistantRules.tokenCount(window)} tokens" }
                        ?: "${AssistantRules.tokenCount(window)} tokens of context",
                    Icons.Outlined.DataUsage, {}, enabled = false,
                ),
                IcodeMenuAction(
                    "Compact Chat", Icons.Outlined.Compress, { gateway.dispatch(WorkbenchIntent.CompactAssistantChat) },
                    enabled = state.messages.isNotEmpty() && !state.running,
                ),
            ),
        )
    }
}

/** A file going with the message; × takes it off. */
@Composable
private fun AttachedFile(name: String, onRemove: () -> Unit) {
    Row(
        Modifier.background(IcodeColors.SurfaceElevated, IcodeShapes.Small).padding(start = IcodeSpacing.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
        Text(name, style = IcodeType.MonoSmall, maxLines = 1)
        IconButton(onClick = onRemove, modifier = Modifier.size(IcodeSizes.BadgeSm)) {
            Icon(Icons.Outlined.Close, contentDescription = "Remove $name", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
        }
    }
}

internal const val AUTO = "Auto"
private const val ASK_FIRST = "Ask before changes"
private const val ALLOW_ALL = "Allow all changes"
private const val MAX_LINES = 8
