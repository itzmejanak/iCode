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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeSelectVariant
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantPermission
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.CONTENT_URI
import dev.icode.mobile.ui.LocalICodeGateway
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.data.voice.Voice

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
    attached: List<DraftFile>,
    onDetach: (DraftFile) -> Unit,
    onAttach: () -> Unit,
    onModels: () -> Unit,
    onSend: () -> Unit,
    /** The workspace's files: where the assistant works, a tap away. */
    onWorkspace: () -> Unit,
) {
    val gateway = LocalICodeGateway.current
    val settings = state.settings
    // A file the chosen model cannot read waits for another model; nothing is sent to fail.
    val unreadable = AssistantRules.attachmentProblem(attached.map { it.kind }, settings.model)
    // Words, files or both: a photo alone is a question too.
    val canSend = (draft.isNotBlank() || attached.isNotEmpty()) && !state.running && unreadable == null
    Column(
        Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Gutter, vertical = IcodeSpacing.Space2),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        IcodeCard(Modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space2)) {
            if (attached.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                    attached.forEach { file -> AttachedFile(file) { onDetach(file) } }
                }
                unreadable?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Amber), modifier = Modifier.padding(top = IcodeSpacing.Space1)) }
            }
            Box(Modifier.fillMaxWidth().padding(IcodeSpacing.Space1)) {
                if (draft.isEmpty()) {
                    Text(
                        when (settings.mode) {
                            AssistantMode.Ask -> "Ask about ${AssistantPlace.name(state.workspace.orEmpty())}"
                            AssistantMode.Plan -> "Plan what to build"
                            AssistantMode.Code -> "Describe what to build"
                            AssistantMode.Orchestrator -> "What would you like to build?"
                        },
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
                    IcodeSelect(
                        options = AssistantMode.entries,
                        selected = settings.mode,
                        optionLabel = { it.label },
                        onSelect = { gateway.dispatch(ICodeIntent.ChooseAssistantMode(it)) },
                        label = settings.mode.label,
                        variant = IcodeSelectVariant.Chip,
                        chipRole = "Mode",
                        optionDescription = { it.description },
                    )
                    // Model opens the model sheet (not a DropdownMenu) — chip visuals kept consistent with IcodeSelect.
                    Row(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(IcodeShapes.Small)
                            .clickable(onClickLabel = "Choose Model") { onModels() }
                            .padding(horizontal = IcodeSpacing.Space1, vertical = IcodeSpacing.Micro),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro),
                    ) {
                        Text(modelLabel(state).ifEmpty { "Choose model" }, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
                    }
                }
                ComposerMicrophone(draft, onDraft)
                if (state.runningHere) {
                    IconButton(onClick = {
                        Voice.stopped = true
                        Voice.stopSpeaking()
                        gateway.dispatch(ICodeIntent.StopAssistant)
                    }) {
                        Icon(Icons.Outlined.StopCircle, contentDescription = "Stop", tint = IcodeColors.Error)
                    }
                } else {
                    IconButton(onClick = onSend, enabled = canSend) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = if (canSend) IcodeColors.Accent else IcodeColors.TextMuted)
                    }
                }
            }
        }
        // Under the box, level with its edges: where it works and what it may do unasked at its left, how full
        // the context is at its right. Only the workspace's name gives way, cut short; the rest keep their width.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Row(
                    Modifier.weight(1f, fill = false).minimumInteractiveComponentSize().clip(IcodeShapes.Small)
                        .clickable(onClickLabel = "Show the workspace's files", onClick = onWorkspace)
                        .padding(horizontal = IcodeSpacing.Space1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
                ) {
                    val placeIcon = Icons.Outlined.FolderOpen
                    Icon(placeIcon, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
                    Text(
                        AssistantPlace.name(state.workspace.orEmpty()),
                        style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Permissions are the chat's, chosen here as VS Code's are (docs/assistant-permissions-plan.md §4.1).
                Choice(
                    state.permission.label, "Permissions", AssistantPermission.entries, state.permission, AssistantPermission::label,
                    { permissionLine(it, AssistantPlace.name(state.workspace.orEmpty())) },
                    icon = true,
                ) { gateway.dispatch(ICodeIntent.SetAssistantPermission(it)) }
            }
            ContextMeter(state)
        }
    }
}

/** What [permission] lets run in [workspace], said where it is chosen. */
internal fun permissionLine(permission: AssistantPermission, workspace: String): String = when (permission) {
    AssistantPermission.AskFirst -> "Changes, commands and anything outside $workspace wait for Allow"
    AssistantPermission.AutoInWorkspace -> "Runs in $workspace without asking; anything outside it waits for Allow"
    AssistantPermission.FullAccess -> "Changes, commands and anything outside $workspace run without asking"
}

/** "o3 · High": the model, and its effort ("Auto" for the provider's own) where it offers one. */
internal fun modelLabel(state: AssistantState): String {
    val settings = state.settings
    val model = settings.model ?: return ""
    if (model.efforts.isEmpty()) return model.id
    return "${model.id} · ${AssistantRules.effortFor(settings.effort, model)?.label ?: AUTO}"
}

/** A toolbar choice and its menu, anchored to it — now via IcodeSelect Chip. */
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
    IcodeSelect(
        options = options,
        selected = selected,
        optionLabel = optionLabel,
        onSelect = onSelect,
        label = label,
        variant = IcodeSelectVariant.Chip,
        chipIcon = Icons.Outlined.Shield.takeIf { icon },
        chipRole = role,
        optionDescription = optionDescription,
    )
}

/**
 * How full the model's context is — "84k / 200k" — as of the chat's last request, so it moves with
 * every step of a turn; always shown, its ring amber where compaction begins. Compact is a tap away.
 */
@Composable
private fun ContextMeter(state: AssistantState) {
    val gateway = LocalICodeGateway.current
    val window = state.settings.model?.contextWindow
    val label = AssistantRules.contextLabel(state.contextTokens, window)
    val share = AssistantRules.contextShare(state.contextTokens, window) ?: 0f
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.minimumInteractiveComponentSize().clip(IcodeShapes.Small).clickable(onClickLabel = "Context") { open = true }
                .padding(horizontal = IcodeSpacing.Space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        ) {
            // A share of a window only where the window is known; the count alone otherwise.
            if (window != null) {
                CircularProgressIndicator(
                    progress = { share },
                    modifier = Modifier.size(IcodeSizes.IconSm),
                    color = if (share >= AssistantRules.COMPACT_AT) IcodeColors.Amber else IcodeColors.Accent,
                    trackColor = IcodeColors.SurfaceTrack,
                    strokeWidth = IcodeSpacing.Micro,
                )
            }
            Text(
                label,
                style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
                maxLines = 1,
            )
        }
        IcodeActionMenu(
            expanded = open,
            onDismiss = { open = false },
            actions = listOf(
                IcodeMenuAction(
                    "Compact Chat", Icons.Outlined.Compress, { gateway.dispatch(ICodeIntent.CompactAssistantChat) },
                    enabled = state.messages.isNotEmpty() && !state.running,
                ),
            ),
        )
    }
}

/**
 * A file going with the message — a photo as its thumbnail — and × to take it off. A tap shows a
 * photo, and opens a file of the workspace in the editor.
 */
@Composable
private fun AttachedFile(file: DraftFile, onRemove: () -> Unit) {
    val open = attachmentOpener(file.name, file.kind, file.location, file.location.takeUnless { it.startsWith(CONTENT_URI) })
    Row(
        Modifier.background(IcodeColors.SurfaceElevated, IcodeShapes.Small).tappable(open),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        AssistantThumbnail(file.location, file.kind, Modifier.size(DRAFT_THUMBNAIL))
        Text(file.name, style = IcodeType.MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = IcodeSizes.MenuWidth))
        IconButton(onClick = onRemove, modifier = Modifier.size(IcodeSizes.BadgeSm)) {
            Icon(Icons.Outlined.Close, contentDescription = "Remove ${file.name}", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
        }
    }
}

internal const val AUTO = "Auto"
private const val MAX_LINES = 8

/** The microphone (docs/phone-agent-plan.md §5.8): what is heard joins the message, for the user to send. */
@Composable
private fun ComposerMicrophone(draft: String, onDraft: (String) -> Unit) {
    val context = LocalContext.current
    if (!remember { Voice.canListen(context) }) return
    val listening by Voice.listening.collectAsState()
    val start = {
        Voice.listen(
            context,
            onHeard = { heard -> onDraft(listOf(draft, heard).filter { it.isNotBlank() }.joinToString(" ")) },
            onProblem = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) start() }
    IconButton(onClick = {
        when {
            listening -> Voice.stopListening()
            Voice.hasMicrophone(context) -> start()
            else -> ask.launch(Manifest.permission.RECORD_AUDIO)
        }
    }) {
        Icon(
            if (listening) Icons.Outlined.StopCircle else Icons.Outlined.Mic,
            contentDescription = if (listening) "Stop listening" else "Speak",
            tint = if (listening) IcodeColors.Accent else IcodeColors.TextSecondary,
            modifier = Modifier.size(IcodeSizes.IconMd),
        )
    }
}
