package dev.icode.mobile.domain

// Search (S4): Quick Open's file index and find-in-files results.

/** One indexed workspace file used by Quick Open ranking. */
data class IndexedFile(
    val name: String,
    val path: String,
    val kind: FileKind,
)

/** One matching line inside a workspace content search result. */
data class ContentMatch(
    val lineNumber: Int,
    val column: Int,
    val lineText: String,
)

/** Matches grouped by file for the workspace search surface. */
data class ContentSearchResult(
    val path: String,
    val name: String,
    val matches: List<ContentMatch>,
)

/** Bounded result set of the latest workspace content search. */
data class ContentSearchState(
    val query: String,
    val matchCase: Boolean,
    val wholeWord: Boolean,
    val regex: Boolean,
    val results: List<ContentSearchResult>,
    val searchedFiles: Int,
    val truncated: Boolean,
    val error: String? = null,
) {
    val totalMatches: Int get() = results.sumOf { it.matches.size }

    companion object {
        val Empty = ContentSearchState(
            query = "",
            matchCase = true,
            wholeWord = false,
            regex = false,
            results = emptyList(),
            searchedFiles = 0,
            truncated = false,
        )
    }
}
