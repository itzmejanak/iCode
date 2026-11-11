package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.icode.mobile.designsystem.BottomBarEntry
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.IndexedFile
import dev.icode.mobile.domain.files.FileFolders
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import kotlinx.coroutines.delay

/** Debounce keeps typing responsive without running a scan per keystroke. */
private const val SEARCH_DEBOUNCE_MILLIS = 250L

/** The two things "find" can mean in a workspace. */
enum class FindMode(val title: String, val placeholder: String) {
    Files("Files", "Go to file by name or path"),
    Text("In Files", "Find text across this workspace"),
}

/**
 * Finding a file and finding text, as one surface.
 *
 * They were two pages that each linked to the other, so the user had to know
 * which one they were in before they could search — and the query was thrown
 * away crossing between them. One query, one result area, a mode switch: the
 * query survives the switch, which is what makes "was it a file name or
 * something inside the file?" a single decision instead of two screens.
 */
@Composable
fun WorkspaceFindScreen(actions: NavigationActions, initialMode: FindMode) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val search = state.contentSearch

    var mode by rememberSaveable { mutableStateOf(initialMode) }
    // The search stays while the workspace is open, as VS Code's search view: come back and the last
    // one is there (the workspace's close resets it). Quick Open starts empty, as VS Code's does.
    var query by rememberSaveable { mutableStateOf(if (initialMode == FindMode.Text) search.query else "") }
    var matchCase by rememberSaveable { mutableStateOf(search.matchCase) }
    var wholeWord by rememberSaveable { mutableStateOf(search.wholeWord) }
    var regex by rememberSaveable { mutableStateOf(search.regex) }

    val root = state.activeWorkspaceLocation
    val rankedFiles = remember(state.fileIndex, query, root) { rankFiles(state.fileIndex, query, root) }
    // Nothing typed: what was opened lately in this workspace comes first (docs/layout.md §14).
    val recentFiles = remember(state.explorer.recent, root) {
        state.explorer.recent.filter { FileFolders.relative(it.location, root) != null }.map { IndexedFile(it.name, it.location) }
    }
    val showingRecent = query.isBlank() && recentFiles.isNotEmpty()
    val resultsAreCurrent = query.isNotBlank() && query.trim() == search.query &&
        matchCase == search.matchCase && wholeWord == search.wholeWord && regex == search.regex

    // Quick Open asks for its index lazily: opening an ordinary workspace must
    // not install a package the user has not used yet.
    LaunchedEffect(mode, state.activeWorkspaceLocation) {
        if (mode == FindMode.Files && state.activeWorkspaceLocation != null) {
            gateway.dispatch(ICodeIntent.PrepareWorkspaceSearch)
        }
    }

    // Only text mode runs a scan; ranking the file index is instant and local, and leaves the text
    // search as it was.
    LaunchedEffect(mode, query, matchCase, wholeWord, regex, state.activeWorkspaceLocation) {
        if (mode != FindMode.Text) return@LaunchedEffect
        if (query.isBlank()) {
            gateway.dispatch(ICodeIntent.ClearContentSearch)
        } else {
            delay(SEARCH_DEBOUNCE_MILLIS)
            gateway.dispatch(ICodeIntent.RunContentSearch(query, matchCase, wholeWord, regex))
        }
    }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Find", subtitle = state.activeWorkspace, onBack = actions.back)
        IcodeTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Gutter),
            placeholder = mode.placeholder,
            leadingIcon = Icons.Outlined.Search,
        )
        Box(Modifier.weight(1f)) {
            when (mode) {
                FindMode.Files -> FileResults(
                    results = if (showingRecent) recentFiles else rankedFiles.entries,
                    recent = showingRecent,
                    query = query,
                    root = root,
                    rootName = state.activeWorkspace,
                    total = rankedFiles.total,
                    indexSize = state.fileIndex.size,
                    indexTruncated = state.fileIndexTruncated,
                    engine = state.searchEngine,
                    onOpen = { path -> gateway.dispatch(ICodeIntent.OpenFile(path)) },
                )
                FindMode.Text -> TextResults(
                    search = search,
                    query = query,
                    resultsAreCurrent = resultsAreCurrent,
                    engine = state.searchEngine,
                    root = root,
                    rootName = state.activeWorkspace,
                    onOpen = { path, line, column ->
                        gateway.dispatch(ICodeIntent.OpenFile(path, line, column))
                    },
                )
            }
        }
        // The mode and the text search's options: the bottom bar, as the
        // editor's (owner's design) — above the keyboard while typing.
        IcodeBottomBar(
            items = FindMode.entries.map { entry ->
                BottomBarItem(entry.title, onClick = { mode = entry }, active = entry == mode)
            },
            trailing = if (mode == FindMode.Text) {
                {
                    BottomBarEntry(BottomBarItem("Aa", { matchCase = !matchCase }, matchCase, description = "Match case"))
                    BottomBarEntry(BottomBarItem("Ab*", { wholeWord = !wholeWord }, wholeWord, description = "Whole word"))
                    BottomBarEntry(BottomBarItem(".*", { regex = !regex }, regex, description = "Regular expression"))
                }
            } else {
                null
            },
        )
    }
}

