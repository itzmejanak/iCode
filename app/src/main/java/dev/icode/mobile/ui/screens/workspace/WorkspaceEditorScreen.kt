package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.BottomPanelBar
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSaveDiscardDialog
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot
import dev.icode.mobile.ui.LocalCodeEditorSessions
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.editor.EditorCanvas
import dev.icode.mobile.ui.editor.MarkdownPreview
import dev.icode.mobile.ui.editor.isMarkdownFile

/** Editor with tab strip, syntax-highlighted document and panel bar. */
@Composable
fun WorkspaceEditorScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val editorSessions = LocalCodeEditorSessions.current
    val editorPreferences = LocalWorkspacePreferences.current.editor
    val document = state.activeDocument
    val activeSession = document?.let { editorSessions.get(it.location) }
    var markdownPreview by rememberSaveable(document?.location) { mutableStateOf(false) }
    var markdownScale by rememberSaveable { mutableFloatStateOf(1f) }
    var handledRevealVersion by rememberSaveable(document?.location) { mutableIntStateOf(0) }
    LaunchedEffect(activeSession?.revealVersion) {
        val version = activeSession?.revealVersion ?: 0
        if (version > handledRevealVersion) {
            markdownPreview = false
            handledRevealVersion = version
        }
    }
    var pendingClose by rememberSaveable { mutableStateOf<String?>(null) }
    var closeOthers by rememberSaveable { mutableStateOf(false) }
    var showCreateFile by rememberSaveable { mutableStateOf(false) }
    val isMarkdown = document != null && isMarkdownFile(document.name)
    val title = document?.let { "${it.name}${if (it.dirty) "  •" else ""}" } ?: "No document"
    val pendingDocument = state.openDocuments.firstOrNull { it.location == pendingClose }
    if (pendingDocument?.dirty == true) {
        IcodeSaveDiscardDialog(
            title = "Close ${pendingDocument.name}?",
            description = "This file has unsaved changes.",
            onSave = {
                gateway.dispatch(WorkbenchIntent.CloseDocument(pendingDocument.location, saveChanges = true))
                pendingClose = null
            },
            onDiscard = {
                gateway.dispatch(WorkbenchIntent.CloseDocument(pendingDocument.location, saveChanges = false))
                pendingClose = null
            },
            onDismiss = { pendingClose = null },
        )
    }
    if (closeOthers && document != null) {
        val dirtyCount = state.openDocuments.count { it.location != document.location && it.dirty }
        IcodeSaveDiscardDialog(
            title = "Close other tabs?",
            description = "${countOf(dirtyCount, "other file has", "other files have")} unsaved changes.",
            onSave = {
                gateway.dispatch(WorkbenchIntent.CloseOtherDocuments(document.location, saveChanges = true))
                closeOthers = false
            },
            onDiscard = {
                gateway.dispatch(WorkbenchIntent.CloseOtherDocuments(document.location, saveChanges = false))
                closeOthers = false
            },
            onDismiss = { closeOthers = false },
        )
    }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = title,
            actions = {
                IconButton(
                    enabled = document?.let { it.dirty && !it.readOnly } ?: false,
                    onClick = { document?.let { gateway.dispatch(WorkbenchIntent.SaveDocument(it.location)) } },
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = "Save", tint = IcodeColors.TextPrimary)
                }
                IconButton(onClick = { actions.navigate(Route.EditorFind) }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Find", tint = IcodeColors.TextPrimary)
                }
                IconButton(onClick = { actions.navigate(Route.CommandPalette) }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Commands", tint = IcodeColors.TextPrimary)
                }
            },
        )
        DocumentTabStrip(
            documents = state.openDocuments,
            selectedLocation = document?.location,
            onSelect = { gateway.dispatch(WorkbenchIntent.SelectDocument(it)) },
            onReorder = { moving, target -> gateway.dispatch(WorkbenchIntent.ReorderDocument(moving, target)) },
            onClose = { location ->
                if (state.openDocuments.any { it.location == location && it.dirty }) pendingClose = location
                else gateway.dispatch(WorkbenchIntent.CloseDocument(location))
            },
            onCloseOthers = {
                if (document != null) {
                    if (state.openDocuments.any { it.location != document.location && it.dirty }) closeOthers = true
                    else gateway.dispatch(WorkbenchIntent.CloseOtherDocuments(document.location))
                }
            },
            onCloseSaved = { gateway.dispatch(WorkbenchIntent.CloseSavedDocuments) },
            onNewFile = { showCreateFile = true },
        )
        if (document == null) {
            NoDocumentContent(Modifier.weight(1f))
        } else {
            if (markdownPreview && isMarkdown) {
                MarkdownPreview(
                    text = document.text,
                    fontSizeSp = editorPreferences.fontSizeSp,
                    fontScale = markdownScale,
                    onScale = { markdownScale = (markdownScale * it).coerceIn(0.6f, 2.4f) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                EditorCanvas(
                    session = requireNotNull(activeSession),
                    document = document,
                    onTextChanged = { gateway.dispatch(WorkbenchIntent.UpdateDocumentText(document.location, it)) },
                    modifier = Modifier.weight(1f),
                )
            }
            activeSession?.coloringError?.let { error ->
                MonoLine(error, color = IcodeColors.Amber, style = IcodeType.MonoSmall)
            }
        }
        BottomPanelBar(
            problemCount = state.diagnostics.size,
            onTerminal = { actions.openPanel(ToolPanelTab.Terminal) },
            onProblems = { actions.openPanel(ToolPanelTab.Problems) },
            trailing = if (isMarkdown) {
                { MarkdownModeToggle(showingPreview = markdownPreview, onToggle = { markdownPreview = !markdownPreview }) }
            } else null,
        )
    }
    if (showCreateFile) CreateFileDialog(parentLocation = null, onDismiss = { showCreateFile = false })
}

@Composable
private fun MarkdownModeToggle(showingPreview: Boolean, onToggle: () -> Unit) {
    val action = if (showingPreview) "Source" else "Preview"
    Row(
        modifier = Modifier
            .heightIn(min = IcodeSizes.TouchTarget)
            .clickable(role = Role.Button, onClickLabel = "Show Markdown $action", onClick = onToggle)
            .semantics { stateDescription = if (showingPreview) "Markdown preview" else "Markdown source" }
            .padding(horizontal = IcodeSpacing.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        Icon(
            if (showingPreview) Icons.Outlined.Code else Icons.Outlined.Visibility,
            contentDescription = null,
            tint = IcodeColors.Accent,
            modifier = Modifier.size(IcodeSizes.IconMd),
        )
        Text(action, style = IcodeType.Caption.copy(color = IcodeColors.Accent))
    }
}

@Composable
private fun NoDocumentContent(modifier: Modifier) {
    CodeSurface(modifier = modifier, padding = PaddingValues(top = IcodeSpacing.Space2)) {
        Text(
            "Select a file in the Explorer or Quick Open to start editing.",
            style = IcodeType.Body.copy(color = IcodeColors.TextMuted),
        )
    }
}
