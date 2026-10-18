package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.remember
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeNavigationLink
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.KeyChip
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalCodeEditorSessions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.editor.EditorCanvas
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.EditorSearcher
import java.util.regex.PatternSyntaxException

/** Find in the same Sora document/caret/undo history used by the editor route. */
@Composable
fun EditorFindScreen(actions: NavigationActions) {
    val document = LocalICodeSnapshot.current.activeDocument
    if (document == null) {
        Column(Modifier.fillMaxSize()) {
            IcodeTopBar("Find", onBack = actions.back)
            CodeSurface(modifier = Modifier.fillMaxSize()) {
                Text("Open a document to find and replace inside it.", style = IcodeType.Body.copy(color = IcodeColors.TextMuted))
            }
        }
        return
    }
    val gateway = LocalICodeGateway.current
    val session = LocalCodeEditorSessions.current.get(document.location)
    val editor = session.editor
    val searcher = editor.searcher
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var wholeWord by rememberSaveable { mutableStateOf(false) }
    var regex by rememberSaveable { mutableStateOf(false) }
    var matchCount by rememberSaveable { mutableIntStateOf(0) }
    var currentIndex by rememberSaveable { mutableIntStateOf(-1) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectFirstOnResult by rememberSaveable { mutableStateOf(false) }
    val queryFocusRequester = remember { FocusRequester() }
    var queryFocused by remember { mutableStateOf(false) }

    DisposableEffect(session, document.location) {
        val resultsReceipt = editor.subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
            searching = false
            matchCount = if (searcher.hasQuery()) searcher.matchedPositionCount else 0
            if (selectFirstOnResult && matchCount > 0) {
                selectFirstOnResult = false
                val restoreQueryFocus = queryFocused
                searcher.gotoNext()
                if (restoreQueryFocus) queryFocusRequester.requestFocus()
            }
            currentIndex = if (searcher.hasQuery()) searcher.currentMatchedPositionIndex else -1
        }
        val selectionReceipt = editor.subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
            currentIndex = if (searcher.hasQuery()) searcher.currentMatchedPositionIndex else -1
        }
        onDispose {
            resultsReceipt.unsubscribe()
            selectionReceipt.unsubscribe()
            searcher.stopSearch()
        }
    }

    LaunchedEffect(document.location, query, matchCase, wholeWord, regex) {
        matchCount = 0
        currentIndex = -1
        searchError = null
        if (query.isEmpty()) {
            searching = false
            selectFirstOnResult = false
            searcher.stopSearch()
        } else {
            val type = when {
                regex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                wholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                else -> EditorSearcher.SearchOptions.TYPE_NORMAL
            }
            val pattern = if (regex && wholeWord) "\\b(?:$query)\\b" else query
            try {
                searching = true
                selectFirstOnResult = true
                searcher.setCyclicJumping(true)
                searcher.setEnsureOccurrenceVisible(true)
                searcher.search(pattern, EditorSearcher.SearchOptions(type, !matchCase))
            } catch (error: PatternSyntaxException) {
                searching = false
                selectFirstOnResult = false
                searchError = "Invalid regular expression: ${error.description}"
                searcher.stopSearch()
            }
        }
    }

    val canReplace = !document.readOnly && matchCount > 0 && searchError == null && !searching
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(document.name, onBack = actions.back)
        Column(
            modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(IcodeSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                IcodeTextField(
                    query,
                    { query = it },
                    Modifier.weight(1f),
                    placeholder = "Find in file",
                    trailingText = when {
                        searching -> "…"
                        matchCount == 0 -> "0"
                        else -> "${(currentIndex + 1).coerceAtLeast(0)} of $matchCount"
                    },
                    inputModifier = Modifier
                        .focusRequester(queryFocusRequester)
                        .onFocusChanged { queryFocused = it.isFocused },
                )
                KeyChip("⌃", onClick = if (matchCount > 0) ({
                    val restoreQueryFocus = queryFocused
                    searcher.gotoPrevious()
                    if (restoreQueryFocus) queryFocusRequester.requestFocus()
                }) else null)
                KeyChip("⌄", onClick = if (matchCount > 0) ({
                    val restoreQueryFocus = queryFocused
                    searcher.gotoNext()
                    if (restoreQueryFocus) queryFocusRequester.requestFocus()
                }) else null)
                IconButton(onClick = actions.back) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close find", tint = IcodeColors.TextSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                IcodeTextField(replacement, { replacement = it }, Modifier.weight(1f), placeholder = "Replace with")
                KeyChip(
                    "Replace",
                    active = canReplace,
                    onClick = if (canReplace) ({
                        if (!searcher.isMatchedPositionSelected()) searcher.gotoNext()
                        searcher.replaceCurrentMatch(replacement)
                        selectFirstOnResult = true
                    }) else null,
                )
                KeyChip(
                    "All",
                    active = canReplace,
                    onClick = if (canReplace) ({ searcher.replaceAll(replacement) }) else null,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                KeyChip("Aa", active = matchCase, onClick = { matchCase = !matchCase })
                KeyChip("Ab*", active = wholeWord, onClick = { wholeWord = !wholeWord })
                KeyChip(".*", active = regex, onClick = { regex = !regex })
            }
            IcodeNavigationLink(
                label = "Search Workspace",
                detail = "Find text across every file",
                icon = Icons.Outlined.Search,
                onClick = { actions.navigate(Route.WorkspaceSearch) },
            )
            searchError?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error)) }
            if (document.readOnly) {
                Text("This document is read-only; search is available, replacement is disabled.", style = IcodeType.Caption)
            }
        }
        EditorCanvas(
            session = session,
            document = document,
            onTextChanged = { gateway.dispatch(ICodeIntent.UpdateDocumentText(document.location, it)) },
            modifier = Modifier.weight(1f),
            // The same view as the editor's: its markers stay; their sheet is the editor's.
            changes = LocalICodeSnapshot.current.lineChanges[document.location]?.changes.orEmpty(),
        )
    }
}
