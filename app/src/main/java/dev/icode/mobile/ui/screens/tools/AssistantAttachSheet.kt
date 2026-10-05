package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeCheckbox
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.SearchEngineState
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.screens.workspace.rankFiles

/**
 * What goes with the message (§13 P6), as ChatGPT's +: the camera, the phone's photos and its files
 * across the top — each the phone's own picker, no permission asked — then the workspace's files as
 * Quick Open finds them, the open one first, ticked to go. Five at most, each once.
 */
@Composable
internal fun AssistantAttachSheet(
    snapshot: ICodeSnapshot,
    attached: List<DraftFile>,
    pickers: AssistantPickers,
    modifier: Modifier,
    onClose: () -> Unit,
    onAttached: (List<DraftFile>) -> Unit,
) {
    val gateway = LocalICodeGateway.current
    val root = snapshot.activeWorkspaceLocation
    // As Quick Open does: the index is asked for when it is needed, not before.
    LaunchedEffect(root) { if (root != null) gateway.dispatch(ICodeIntent.PrepareWorkspaceSearch) }
    var query by rememberSaveable { mutableStateOf("") }
    val ranked = remember(snapshot.fileIndex, query) { rankFiles(snapshot.fileIndex, query) }
    val full = attached.size >= AssistantRules.MAX_ATTACHMENTS
    val open = snapshot.activeDocument?.takeIf { query.isBlank() && root != null && it.location.startsWith("$root/") }
    val listed = ranked.entries.filter { it.path != open?.location }

    // A picker opens over the chat, not over this sheet: what it hands back shows in the composer.
    fun pick(start: () -> Unit) {
        onClose()
        start()
    }

    @Composable
    fun FileRow(name: String, location: String, subtitle: String) {
        val chosen = attached.any { it.location == location }
        IcodeListRow(
            title = name,
            subtitle = subtitle,
            onClick = {
                when {
                    chosen -> onAttached(attached.filterNot { it.location == location })
                    !full -> onAttached(attached + DraftFile(location, name, AssistantRules.attachmentKind(name, null)))
                }
            },
            leading = { IcodeCheckbox(chosen, onCheckedChange = null, enabled = chosen || !full) },
        )
    }

    fun pathOf(location: String) = if (root != null) location.removePrefix("$root/") else location
    IcodeBottomSheet(
        title = "Attach",
        onClose = onClose,
        modifier = modifier,
        footer = {
            PrimaryButton(if (attached.isEmpty()) "Done" else "Done · ${attached.size} of ${AssistantRules.MAX_ATTACHMENTS}", onClose, Modifier.fillMaxWidth())
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            pickers.camera?.let { camera -> AttachTile("Camera", Icons.Outlined.PhotoCamera, !full, Modifier.weight(1f)) { pick(camera) } }
            AttachTile("Photos", Icons.Outlined.PhotoLibrary, !full, Modifier.weight(1f)) { pick(pickers.photos) }
            AttachTile("Files", Icons.Outlined.FolderOpen, !full, Modifier.weight(1f)) { pick(pickers.files) }
        }
        Hint(if (full) "Five files go with a message at most: take one off to add another" else "Photos and PDFs go to models that read them; text files to any model")
        // The workspace's own files; the chat is only open on one.
        if (root != null) {
            SectionLabel("From ${AssistantRules.workspaceName(root)}")
            Search(query, "Search files", literal = true) { query = it }
            open?.let { document -> FileRow(document.name, document.location, "Open · ${pathOf(document.location)}") }
            listed.forEach { file -> FileRow(file.name, file.path, pathOf(file.path)) }
            when {
                ranked.truncated -> Hint("More files match: narrow the search")
                listed.isNotEmpty() || open != null -> Unit
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
}

/** One of the phone's pickers: its icon over its name; muted, and not a button, while five are attached. */
@Composable
private fun AttachTile(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    IcodeCard(modifier, elevated = true, padding = PaddingValues(vertical = IcodeSpacing.Space3), onClick = onClick.takeIf { enabled }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Icon(icon, contentDescription = null, tint = if (enabled) IcodeColors.Accent else IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconXl))
            Text(label, style = IcodeType.Caption.copy(color = if (enabled) IcodeColors.TextPrimary else IcodeColors.TextMuted))
        }
    }
}
