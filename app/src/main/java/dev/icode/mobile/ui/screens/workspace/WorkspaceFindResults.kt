package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFileRow
import dev.icode.mobile.designsystem.IcodeLoadingLine
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.ContentSearchState
import dev.icode.mobile.domain.IndexedFile
import dev.icode.mobile.domain.SearchEngineState
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.files.FileFolders
import dev.icode.mobile.ui.FileTypeIcon
import dev.icode.mobile.ui.parentName

/** Result cap keeps the list responsive on large workspaces. */
private const val MAX_FILE_RESULTS = 50

/**
 * Quick Open's files (docs/screens-polish-plan.md D3): each as the explorer lists files, one height —
 * its icon, its name, its folder in the workspace ([root], named [rootName]). The best match leads,
 * in the accent. With nothing typed, what was opened lately ([recent]).
 */
@Composable
internal fun FileResults(
    results: List<IndexedFile>,
    recent: Boolean,
    query: String,
    root: String?,
    rootName: String,
    total: Int,
    indexSize: Int,
    indexTruncated: Boolean,
    engine: SearchEngineState,
    onOpen: (String) -> Unit,
) {
    val waitingWithoutResults = results.isEmpty() && engine !is SearchEngineState.Ready
    val typed = query.isNotBlank()
    Column(Modifier.fillMaxSize()) {
        when (engine) {
            SearchEngineState.Installing, SearchEngineState.Indexing -> IcodeLoadingLine(engine.waiting.orEmpty(), Modifier.padding(IcodeSpacing.Gutter))
            is SearchEngineState.Failed -> Text(
                engine.reason,
                modifier = Modifier.padding(IcodeSpacing.Gutter),
                style = IcodeType.Caption,
                color = IcodeColors.Error,
            )
            is SearchEngineState.Ready -> engine.warning?.let { warning ->
                Text(warning, Modifier.padding(horizontal = IcodeSpacing.Gutter), style = IcodeType.Caption, color = IcodeColors.Amber)
            }
            SearchEngineState.Unknown -> if (results.isEmpty()) IcodeLoadingLine(engine.waiting.orEmpty(), Modifier.padding(IcodeSpacing.Gutter))
        }
        // What is not shown, said quietly: a workspace too large to index whole, a query matching more than fits.
        val limits = listOfNotNull(
            "Large workspace: the first $indexSize files are searched".takeIf { indexTruncated },
            "${results.size} of $total shown".takeIf { typed && total > results.size },
        )
        if (!waitingWithoutResults && limits.isNotEmpty()) {
            Text(limits.joinToString(" · "), Modifier.padding(horizontal = IcodeSpacing.Gutter), style = IcodeType.Caption)
        }
        if (!waitingWithoutResults && results.isEmpty()) {
            Text(
                if (typed) "No file matches \"$query\"" else "No workspace files found",
                modifier = Modifier.padding(IcodeSpacing.Gutter),
                style = IcodeType.Caption,
                color = IcodeColors.TextSecondary,
            )
        } else if (results.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (recent) {
                    item(key = "recent") {
                        SectionLabel("Recent", Modifier.padding(start = IcodeSpacing.Gutter, top = IcodeSpacing.Space1, bottom = IcodeSpacing.Space1))
                    }
                }
                itemsIndexed(results, key = { _, entry -> entry.path }) { index, entry ->
                    val best = typed && index == 0
                    IcodeFileRow(
                        name = entry.name,
                        leading = { FileTypeIcon(entry.name, folder = false, size = IcodeSizes.IconXl, parent = parentName(entry.path)) },
                        onClick = { onOpen(entry.path) },
                        detail = FileFolders.of(entry.path, root, rootName),
                        detailIsPath = true,
                        nameColor = if (best) IcodeColors.Accent else IcodeColors.TextPrimary,
                        selected = best,
                    )
                }
            }
        }
    }
}

/**
 * In Files (docs/screens-polish-plan.md D3): each file's matches under its row — the explorer's row,
 * with how many it has; a tap folds them — each match its line number in a gutter, then its line.
 */
@Composable
internal fun TextResults(
    search: ContentSearchState,
    query: String,
    resultsAreCurrent: Boolean,
    engine: SearchEngineState,
    root: String?,
    rootName: String,
    onOpen: (String, Int, Int) -> Unit,
) {
    var collapsedPaths by remember(search.query) { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxSize()) {
        SearchSummary(search, query, resultsAreCurrent, engine)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            (if (resultsAreCurrent) search.results else emptyList()).forEach { result ->
                val expanded = result.path !in collapsedPaths
                item(key = result.path) {
                    IcodeFileRow(
                        name = result.name,
                        leading = { FileTypeIcon(result.name, folder = false, size = IcodeSizes.IconXl, parent = parentName(result.path)) },
                        onClick = { collapsedPaths = if (expanded) collapsedPaths + result.path else collapsedPaths - result.path },
                        detail = FileFolders.of(result.path, root, rootName),
                        detailIsPath = true,
                        trailing = {
                            Text(countOf(result.matches.size, "match", "matches"), style = IcodeType.Caption, maxLines = 1)
                            Icon(
                                if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = if (expanded) "Fold ${result.name}'s matches" else "Show ${result.name}'s matches",
                                tint = IcodeColors.TextSecondary,
                                modifier = Modifier.size(IcodeSizes.IconSm),
                            )
                        },
                    )
                }
                if (expanded) {
                    // The line numbers as one column: the widest sets it, in the mono face.
                    val width = result.matches.maxOfOrNull { it.lineNumber.toString().length } ?: 1
                    itemsIndexed(result.matches, key = { index, _ -> "${result.path}:$index" }) { _, match ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(result.path, match.lineNumber, match.column) }
                                .padding(
                                    start = IcodeSpacing.Gutter + IcodeSizes.IconXl + IcodeSpacing.Space2,
                                    end = IcodeSpacing.Gutter,
                                    top = IcodeSpacing.Space1,
                                    bottom = IcodeSpacing.Space1,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
                        ) {
                            MonoLine(match.lineNumber.toString().padStart(width), color = IcodeColors.TextMuted, style = IcodeType.MonoSmall, maxLines = 1)
                            MonoLine(match.lineText.trim(), Modifier.weight(1f), style = IcodeType.MonoSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSummary(search: ContentSearchState, query: String, resultsAreCurrent: Boolean, engine: SearchEngineState) {
    val summary = when {
        query.isBlank() -> "Type to search across this workspace"
        engine == SearchEngineState.Installing -> engine.waiting.orEmpty()
        engine is SearchEngineState.Failed -> engine.reason
        !resultsAreCurrent -> "Searching this workspace…"
        search.error != null -> search.error
        search.results.isEmpty() -> "No matches for \"${search.query}\""
        else -> "${countOf(search.totalMatches, "result")} in ${countOf(search.results.size, "file")}" +
            if (search.truncated) "" else " · ${countOf(search.searchedFiles, "file")} scanned"
    }
    // Waiting on the search, or on its engine's install: said beside the spinner.
    val waiting = query.isNotBlank() && (engine == SearchEngineState.Installing || engine !is SearchEngineState.Failed && !resultsAreCurrent)
    if (waiting) {
        IcodeLoadingLine(summary, Modifier.padding(IcodeSpacing.Gutter))
    } else {
        Text(
            summary,
            modifier = Modifier.padding(IcodeSpacing.Gutter),
            style = IcodeType.Caption,
            color = if (engine is SearchEngineState.Failed || resultsAreCurrent && search.error != null) IcodeColors.Error else IcodeColors.TextSecondary,
        )
    }
    if (resultsAreCurrent && search.truncated) {
        MonoLine(
            "Result limit reached — narrow the query to see more",
            modifier = Modifier.padding(horizontal = IcodeSpacing.Gutter),
            color = IcodeColors.Amber,
            style = IcodeType.MonoSmall,
        )
    }
}

/**
 * Match on file name first, then on its path in the workspace ([root]) — never the phone's folders
 * iCode keeps it in, which every file shares — keeping the index's order among equals.
 */
internal fun rankFiles(files: List<IndexedFile>, query: String, root: String? = null): RankedFiles {
    val trimmed = query.trim()
    val ranked = if (trimmed.isEmpty()) {
        files
    } else {
        val needle = trimmed.lowercase()
        files.asSequence()
            .mapIndexed { index, file -> Triple(file, score(file, needle, root), index) }
            .filter { (_, score, _) -> score > 0 }
            .sortedWith(compareByDescending<Triple<IndexedFile, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
            .toList()
    }
    return RankedFiles(ranked.take(MAX_FILE_RESULTS), ranked.size)
}

/** The best [entries], of [total] that matched. */
internal data class RankedFiles(val entries: List<IndexedFile>, val total: Int) {
    val truncated: Boolean get() = total > entries.size
}

private fun score(file: IndexedFile, needle: String, root: String?): Int {
    val name = file.name.lowercase()
    val path = (FileFolders.relative(file.path, root) ?: file.path).lowercase()
    return when {
        name == needle -> 100
        name.startsWith(needle) -> 80
        name.contains(needle) -> 60
        path.contains(needle) -> 30
        else -> 0
    }
}
