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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

/** Result cap keeps the list responsive on large workspaces. */
private const val MAX_FILE_RESULTS = 50

@Composable
internal fun FileResults(
    results: List<IndexedFile>,
    query: String,
    indexSize: Int,
    truncated: Boolean,
    onOpen: (String) -> Unit,
) {
    if (truncated) {
        MonoLine(
            "Large workspace: showing the first $indexSize files",
            modifier = Modifier.padding(horizontal = IcodeSpacing.Space4),
            color = IcodeColors.Amber,
            style = IcodeType.MonoSmall,
        )
    }
    if (results.isEmpty()) {
        Text(
            if (query.isBlank()) "No files indexed yet" else "No file matches \"$query\"",
            modifier = Modifier.padding(IcodeSpacing.Space4),
            style = IcodeType.Caption,
            color = IcodeColors.TextSecondary,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = IcodeSpacing.Space3),
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

@Composable
internal fun TextResults(
    search: ContentSearchState,
    query: String,
    resultsAreCurrent: Boolean,
    onOpen: (String, Int, Int) -> Unit,
) {
    SearchSummary(search, query, resultsAreCurrent)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = IcodeSpacing.Space4),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4 + IcodeSpacing.Micro),
    ) {
        items(if (resultsAreCurrent) search.results else emptyList(), key = { it.path }) { result ->
            Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "⌄  ${result.name}",
                        style = IcodeType.Mono.copy(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold),
                    )
                    Text("  (${result.matches.size} matches)", style = IcodeType.MonoSmall)
                }
                MonoLine(
                    result.path,
                    modifier = Modifier.padding(start = IcodeSpacing.Space4 + IcodeSpacing.Micro),
                    style = IcodeType.MonoMicro,
                    color = IcodeColors.TextMuted,
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

@Composable
private fun SearchSummary(search: ContentSearchState, query: String, resultsAreCurrent: Boolean) {
    val summary = when {
        query.isBlank() -> "Type to search across this workspace"
        !resultsAreCurrent -> "Searching this workspace…"
        search.error != null -> search.error
        search.results.isEmpty() -> "No matches for \"${search.query}\""
        else -> "${search.totalMatches} ${if (search.totalMatches == 1) "result" else "results"} in " +
            "${search.results.size} ${if (search.results.size == 1) "file" else "files"}" +
            " · ${search.searchedFiles} files scanned"
    }
    Text(
        summary,
        modifier = Modifier.padding(IcodeSpacing.Space4),
        style = IcodeType.Caption,
        color = if (resultsAreCurrent && search.error != null) IcodeColors.Error else IcodeColors.TextSecondary,
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
internal fun rankFiles(files: List<IndexedFile>, query: String): List<IndexedFile> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return files.take(MAX_FILE_RESULTS)
    val needle = trimmed.lowercase()
    return files.asSequence()
        .mapIndexed { index, file -> Triple(file, score(file, needle), index) }
        .filter { (_, score, _) -> score > 0 }
        .sortedWith(compareByDescending<Triple<IndexedFile, Int, Int>> { it.second }.thenBy { it.third })
        .take(MAX_FILE_RESULTS)
        .map { it.first }
        .toList()
}

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
