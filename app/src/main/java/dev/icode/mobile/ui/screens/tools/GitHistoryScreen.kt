package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeLoadingLine
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.GitHistory
import dev.icode.mobile.domain.GitHistoryIntent
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import kotlinx.coroutines.flow.first

/**
 * Source Control's History sheet (docs/git-history-plan.md §4), opened from Last Commit's corner:
 * HEAD's commits, newest first, a line each beside the graph of where they branched and merged
 * ([GitHistoryLine]). The history is read afresh each time the sheet opens, and again when HEAD
 * moves while it is open; the next page as the list nears its end.
 */
@Composable
fun GitHistoryScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val workspace = state.activeWorkspaceLocation
    val head = state.gitRepository?.lastCommit?.id
    LaunchedEffect(workspace, head) { gateway.dispatch(GitHistoryIntent.Open) }
    // Another HEAD's history is never shown for this one's: the sheet waits for it instead.
    val history = state.gitHistory?.takeIf { it.isFor(workspace, head) }
    val more = { gateway.dispatch(GitHistoryIntent.More) }
    Box(Modifier.fillMaxSize()) {
        IcodeBottomSheet(
            title = "Commit History",
            onClose = actions.back,
            modifier = Modifier.align(Alignment.BottomCenter),
            scroll = false,
        ) {
            if (history != null && history.rows.isNotEmpty()) HistoryList(history, more) else HistoryWait(history, more)
        }
    }
}

/** Before the first line: the first page being read, why it could not be, or a history with no commits. */
@Composable
private fun HistoryWait(history: GitHistory?, onRetry: () -> Unit) {
    val failure = history?.failure
    when {
        failure != null -> HistoryFailure(failure, onRetry)
        history != null && history.complete -> Text("No commits yet", style = IcodeType.Caption)
        else -> IcodeLoadingLine("Reading the history…")
    }
}

/**
 * The lines, any number open at once, and after them the next page being read, why it could not be,
 * or how many commits there are once every one is read.
 */
@Composable
private fun HistoryList(history: GitHistory, onMore: () -> Unit) {
    var opened by rememberSaveable { mutableStateOf(emptySet<String>()) }
    // As wide as the widest line read: every line's text starts at the same place.
    val lanes = remember(history.rows) { history.rows.maxOf { it.graph.width } }
    val list = rememberLazyListState()
    LazyColumn(Modifier.fillMaxWidth(), state = list) {
        items(history.rows, key = { it.commit.id }) { row ->
            val id = row.commit.id
            GitHistoryLine(row, lanes, open = id in opened) { opened = if (id in opened) opened - id else opened + id }
        }
        item(key = END) {
            Column(Modifier.fillMaxWidth().padding(vertical = IcodeSpacing.Space3), horizontalAlignment = Alignment.CenterHorizontally) {
                val failure = history.failure
                when {
                    failure != null -> HistoryFailure(failure, onMore)
                    history.complete -> Text(countOf(history.rows.size, "commit"), style = IcodeType.Caption, color = IcodeColors.TextMuted)
                    else -> IcodeLoadingLine("Reading more commits…")
                }
            }
        }
    }
    // The next page once the list comes within AHEAD lines of its end; not while one is read, nor
    // after one failed until Try Again asks.
    val wanted = !history.loading && !history.complete && history.failure == null
    LaunchedEffect(list, wanted, history.rows.size) {
        if (!wanted) return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.first { it >= history.rows.size - AHEAD }
        onMore()
    }
}

/** Git's words for why a page could not be read, and Try Again, which reads that page again. */
@Composable
private fun HistoryFailure(failure: String, onRetry: () -> Unit) {
    Text(failure, style = IcodeType.Caption, color = IcodeColors.Error)
    IcodeTextButton("Try Again", onRetry)
}

private const val END = "end"
/** Lines short of the end at which the next page is asked for: read before the list reaches it. */
private const val AHEAD = 20
