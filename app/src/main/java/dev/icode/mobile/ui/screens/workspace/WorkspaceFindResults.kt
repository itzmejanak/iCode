package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.domain.ContentSearchState
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.IndexedFile
import dev.icode.mobile.domain.SearchEngineState
import dev.icode.mobile.domain.countOf

/** Result cap keeps the list responsive on large workspaces. */
private const val MAX_FILE_RESULTS = 50

@Composable
internal fun FileResults(
    results: List<IndexedFile>,
    query: String,
    indexSize: Int,
    indexTruncated: Boolean,
    rankTruncated: Boolean,
    engine: SearchEngineState,
    onOpen: (String) -> Unit,
) {
    val waitingWithoutResults = results.isEmpty() && engine !is SearchEngineState.Ready
    Column(Modifier.fillMaxSize()) {
        when (engine) {
            SearchEngineState.Installing -> MonoLine(
                "Installing ripgrep for workspace search…",
                modifier = Modifier.padding(horizontal = IcodeSpacing.Space4),
                color = IcodeColors.Amber,
                style = IcodeType.MonoSmall,
            )
            SearchEngineState.Indexing -> Text(
                "Indexing workspace files…",
                modifier = Modifier.padding(IcodeSpacing.Space4),
                style = IcodeType.Caption,
                color = IcodeColors.TextSecondary,
            )
            is SearchEngineState.Failed -> Text(
                engine.reason,
                modifier = Modifier.padding(IcodeSpacing.Space4),
                style = IcodeType.Caption,
                color = IcodeColors.Error,
            )
            is SearchEngineState.Ready -> engine.warning?.let { warning ->
                MonoLine(
                    warning,
                    modifier = Modifier.padding(horizontal = IcodeSpacing.Space4),
                    color = IcodeColors.Amber,
                    style = IcodeType.MonoSmall,
                )
            }
            SearchEngineState.Unknown -> if (results.isEmpty()) {
                Text(
                    "Preparing workspace search…",
                    modifier = Modifier.padding(IcodeSpacing.Space4),
                    style = IcodeType.Caption,
                    color = IcodeColors.TextSecondary,
                )
            }
        }
        if (!waitingWithoutResults && (indexTruncated || rankTruncated)) {
            val limitMessage = when {
                indexTruncated && rankTruncated -> "Indexed the first $indexSize files · showing the best $MAX_FILE_RESULTS"
                indexTruncated -> "Large workspace: indexed the first $indexSize files"
                else -> "Showing the best $MAX_FILE_RESULTS matches"
            }
            MonoLine(
                limitMessage,
                modifier = Modifier.padding(horizontal = IcodeSpacing.Space4),
                color = IcodeColors.Amber,
                style = IcodeType.MonoSmall,
            )
        }
        if (!waitingWithoutResults && results.isEmpty()) {
            Text(
                if (query.isBlank()) "No workspace files found" else "No file matches \"$query\"",
                modifier = Modifier.padding(IcodeSpacing.Space4),
                style = IcodeType.Caption,
                color = IcodeColors.TextSecondary,
            )
        } else if (results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = IcodeSpacing.Space3),
                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
            ) {
                itemsIndexed(results, key = { _, entry -> entry.path }) { index, entry ->
                    IcodeCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = PaddingValues(IcodeSpacing.Space1),
                    ) {
                        IcodeListRow(
                            title = entry.name,
                            subtitle = entry.path,
                            // The best match leads, matching the frame's highlighted first row.
                            selected = index == 0,
                            titleColor = if (index == 0) IcodeColors.Accent else IcodeColors.TextPrimary,
                            onClick = { onOpen(entry.path) },
                            leading = {
                                Icon(
                                    iconFor(entry.kind),
                                    contentDescription = null,
                                    tint = toneFor(entry.kind).tint(),
                                    modifier = Modifier.size(IcodeSizes.IconXl),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TextResults(
    search: ContentSearchState,
    query: String,
    resultsAreCurrent: Boolean,
    engine: SearchEngineState,
    onOpen: (String, Int, Int) -> Unit,
) {
    var collapsedPaths by remember(search.query) { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxSize()) {
        SearchSummary(search, query, resultsAreCurrent, engine)
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            items(if (resultsAreCurrent) search.results else emptyList(), key = { it.path }) { result ->
                val expanded = result.path !in collapsedPaths
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                collapsedPaths = if (expanded) collapsedPaths + result.path else collapsedPaths - result.path
                            }
                            .padding(vertical = IcodeSpacing.Space1),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
                    ) {
                        Icon(
                            if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = if (expanded) "Collapse ${result.name}" else "Expand ${result.name}",
                            tint = IcodeColors.Accent,
                            modifier = Modifier.size(IcodeSizes.IconSm),
                        )
                        Text(
                            result.name,
                            modifier = Modifier.weight(1f),
                            style = IcodeType.Mono.copy(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "(${countOf(result.matches.size, "match", "matches")})",
                            style = IcodeType.MonoSmall,
                            color = IcodeColors.TextSecondary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (expanded) {
                        MonoLine(
                            result.path,
                            modifier = Modifier.padding(start = IcodeSpacing.Space4 + IcodeSpacing.Micro),
                            style = IcodeType.MonoMicro,
                            color = IcodeColors.TextMuted,
                            maxLines = 2,
                        )
                        result.matches.forEach { match ->
                            MonoLine(
                                text = "${match.lineNumber}:${match.column + 1}   ${match.lineText.trim()}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpen(result.path, match.lineNumber, match.column) }
                                    .padding(start = IcodeSpacing.Space4 + IcodeSpacing.Micro),
                                style = IcodeType.MonoSmall,
                            )
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
        engine == SearchEngineState.Installing -> "Installing ripgrep for workspace search…"
        engine is SearchEngineState.Failed -> engine.reason
        !resultsAreCurrent -> "Searching this workspace…"
        search.error != null -> search.error
        search.results.isEmpty() -> "No matches for \"${search.query}\""
        else -> "${countOf(search.totalMatches, "result")} in ${countOf(search.results.size, "file")}" +
            if (search.truncated) "" else " · ${countOf(search.searchedFiles, "file")} scanned"
    }
    Text(
        summary,
        modifier = Modifier.padding(IcodeSpacing.Space4),
        style = IcodeType.Caption,
        color = if (engine is SearchEngineState.Failed || resultsAreCurrent && search.error != null) IcodeColors.Error else IcodeColors.TextSecondary,
    )
    if (resultsAreCurrent && search.truncated) {
        MonoLine(
            "Result limit reached — narrow the query to see more",
            modifier = Modifier.padding(horizontal = IcodeSpacing.Space4),
            color = IcodeColors.Amber,
            style = IcodeType.MonoSmall,
        )
    }
}

private fun iconFor(kind: FileKind) = when (kind) {
    FileKind.TypeScript, FileKind.Code -> Icons.Outlined.Code
    else -> Icons.Outlined.Description
}

private fun toneFor(kind: FileKind) = when (kind) {
    FileKind.TypeScript -> IcodeTone.Info
    FileKind.Code -> IcodeTone.Accent
    FileKind.Json -> IcodeTone.Warning
    else -> IcodeTone.Neutral
}

/** Match on file name first, then path; keep the workspace-relative order. */
internal fun rankFiles(files: List<IndexedFile>, query: String): RankedFiles {
    val trimmed = query.trim()
    val ranked = if (trimmed.isEmpty()) {
        files
    } else {
        val needle = trimmed.lowercase()
        files.asSequence()
            .mapIndexed { index, file -> Triple(file, score(file, needle), index) }
            .filter { (_, score, _) -> score > 0 }
            .sortedWith(compareByDescending<Triple<IndexedFile, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
            .toList()
    }
    return RankedFiles(ranked.take(MAX_FILE_RESULTS), ranked.size > MAX_FILE_RESULTS)
}

internal data class RankedFiles(val entries: List<IndexedFile>, val truncated: Boolean)

private fun score(file: IndexedFile, needle: String): Int {
    val name = file.name.lowercase()
    val path = file.path.lowercase()
    return when {
        name == needle -> 100
        name.startsWith(needle) -> 80
        name.contains(needle) -> 60
        path.contains(needle) -> 30
        else -> 0
    }
}
