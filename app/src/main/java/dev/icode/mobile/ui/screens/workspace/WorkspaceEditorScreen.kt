package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.BottomBarEntry
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.BottomPanelBar
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSaveDiscardDialog
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.LineDiff
import dev.icode.mobile.domain.StageLineChange
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.markedLine
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.LocalCodeEditorSessions
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.editor.EditorCanvas
import dev.icode.mobile.domain.markdown.MarkdownPlace
import dev.icode.mobile.ui.markdown.MarkdownPreview

/** Editor with tab strip, syntax-highlighted document and panel bar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceEditorScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val editorSessions = LocalCodeEditorSessions.current
    val editorPreferences = LocalWorkspacePreferences.current.editor
    val document = state.activeDocument
    val activeSession = document?.let { editorSessions.get(it.location) }
    var markdownPreview by rememberSaveable(document?.location) { mutableStateOf(false) }
    var handledRevealVersion by rememberSaveable(document?.location) { mutableIntStateOf(0) }
    LaunchedEffect(activeSession?.revealVersion) {
        val version = activeSession?.revealVersion ?: 0
        if (version > handledRevealVersion) {
            markdownPreview = false
            handledRevealVersion = version
        }
    }
    // The file's Git changes (docs/editor-plan.md §3), and the one the change sheet shows.
    val lineChanges = document?.let { state.lineChanges[it.location] }
    val changes = lineChanges?.changes.orEmpty()
    var changeIndex by rememberSaveable(document?.location) { mutableStateOf<Int?>(null) }
    val shownChange = changeIndex?.takeIf { changes.isNotEmpty() }?.coerceAtMost(changes.lastIndex)
    val showChange: (Int) -> Unit = { index ->
        changeIndex = index
        activeSession?.revealAt(changes[index].markedLine + 1, 0)
    }
    var pendingClose by rememberSaveable { mutableStateOf<String?>(null) }
    // The tab whose sheet is shown, and the one kept while Close other tabs asks about unsaved files.
    var tabSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var closeOthers by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateFile by rememberSaveable { mutableStateOf(false) }
    val isMarkdown = document != null && FileTypes.of(document.name) == FileTypes.Markdown
    val title = document?.name ?: "No document"
    val pendingDocument = state.openDocuments.firstOrNull { it.location == pendingClose }
    if (pendingDocument?.dirty == true) {
        IcodeSaveDiscardDialog(
            title = "Close ${pendingDocument.name}?",
            description = "This file has unsaved changes.",
            onSave = {
                gateway.dispatch(ICodeIntent.CloseDocument(pendingDocument.location, saveChanges = true))
                pendingClose = null
            },
            onDiscard = {
                gateway.dispatch(ICodeIntent.CloseDocument(pendingDocument.location, saveChanges = false))
                pendingClose = null
            },
            onDismiss = { pendingClose = null },
        )
    }
    closeOthers?.let { keep ->
        val dirtyCount = state.openDocuments.count { it.location != keep && it.dirty }
        IcodeSaveDiscardDialog(
            title = "Close other tabs?",
            description = "${countOf(dirtyCount, "other file has", "other files have")} unsaved changes.",
            onSave = {
                gateway.dispatch(ICodeIntent.CloseOtherDocuments(keep, saveChanges = true))
                closeOthers = null
            },
            onDiscard = {
                gateway.dispatch(ICodeIntent.CloseOtherDocuments(keep, saveChanges = false))
                closeOthers = null
            },
            onDismiss = { closeOthers = null },
        )
    }
    val closeTab = { location: String ->
        if (state.openDocuments.any { it.location == location && it.dirty }) pendingClose = location
        else gateway.dispatch(ICodeIntent.CloseDocument(location))
    }
    val tabFor = state.openDocuments.firstOrNull { it.location == tabSheet }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(tabFor != null || shownChange != null)) {
            IcodeTopBar(
                title = title,
                actions = {
                    IconButton(
                        enabled = document?.let { it.dirty && !it.readOnly } ?: false,
                        onClick = { document?.let { gateway.dispatch(ICodeIntent.SaveDocument(it.location)) } },
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
                onSelect = { gateway.dispatch(ICodeIntent.SelectDocument(it)) },
                onReorder = { moving, target -> gateway.dispatch(ICodeIntent.ReorderDocument(moving, target)) },
                onClose = closeTab,
                onPress = { tabSheet = it },
            )
            if (document == null) {
                NoDocumentContent(Modifier.weight(1f))
            } else {
                if (markdownPreview && isMarkdown) {
                    MarkdownPreview(
                        text = document.text,
                        place = MarkdownPlace.ofFile(document.location, state.activeWorkspaceLocation),
                        fontSizeSp = editorPreferences.fontSizeSp,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    EditorCanvas(
                        session = requireNotNull(activeSession),
                        document = document,
                        onTextChanged = { gateway.dispatch(ICodeIntent.UpdateDocumentText(document.location, it)) },
                        // A little of the editor's own background above the first line: the selected
                        // tab flows into the editor, and line 1 does not sit against the tab bar.
                        modifier = Modifier.weight(1f).background(IcodeColors.BackgroundDeep).padding(top = IcodeSpacing.Space1),
                        changes = changes,
                        onChangeTapped = { changeIndex = it },
                    )
                }
                activeSession?.coloringError?.let { error ->
                    MonoLine(error, color = IcodeColors.Amber, style = IcodeType.MonoSmall)
                }
            }
            // While the keyboard is open the keys row takes the bottom bar's place (docs/editor-plan.md §4).
            val typing = WindowInsets.isImeVisible && document?.readOnly == false && !(markdownPreview && isMarkdown)
            if (typing && editorPreferences.keys.isNotEmpty()) {
                EditorKeyRow(editorPreferences.keys, onKey = { activeSession?.press(it) })
            } else {
                BottomPanelBar(
                    problemCount = state.diagnostics.size,
                    onTerminal = { actions.openPanel(ToolPanelTab.Terminal) },
                    onProblems = { actions.openPanel(ToolPanelTab.Problems) },
                    trailing = if (isMarkdown || changes.isNotEmpty()) {
                        {
                            if (changes.isNotEmpty()) {
                                BottomBarEntry(
                                    BottomBarItem(countOf(changes.size, "change"), onClick = {
                                        LineDiff.nextChange(changes, activeSession?.caretLine ?: 0)?.let(showChange)
                                    }),
                                )
                            }
                            if (isMarkdown) MarkdownModeToggle(showingPreview = markdownPreview, onToggle = { markdownPreview = !markdownPreview })
                        }
                    } else null,
                )
            }
        }
        if (shownChange != null && document != null && lineChanges != null) {
            val close = { changeIndex = null }
            IcodeSheetOverlay(onClose = close) {
                EditorChangeSheet(
                    changes = changes,
                    index = shownChange,
                    onRevert = { activeSession?.revert(it, lineChanges.base) },
                    onStage = { gateway.dispatch(StageLineChange(document.location, it)) },
                    onShow = showChange,
                    onClose = close,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        tabFor?.let { tab ->
            val close = { tabSheet = null }
            IcodeSheetOverlay(onClose = close) {
                TabSheet(
                    document = tab,
                    path = state.runtimePathOf(tab.location),
                    documents = state.openDocuments,
                    onClose = closeTab,
                    onCloseOthers = { keep ->
                        if (state.openDocuments.any { it.location != keep && it.dirty }) closeOthers = keep
                        else gateway.dispatch(ICodeIntent.CloseOtherDocuments(keep))
                    },
                    onCloseSaved = { gateway.dispatch(ICodeIntent.CloseSavedDocuments) },
                    onNewFile = { showCreateFile = true },
                    onDismiss = close,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
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
