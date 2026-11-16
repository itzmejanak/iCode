package dev.icode.mobile.domain

// Search: Quick Open's file index and find-in-files results.

/** Availability of the shared ripgrep engine, including first-use provisioning. */
sealed interface SearchEngineState {
    data object Unknown : SearchEngineState
    data object Installing : SearchEngineState
    data object Indexing : SearchEngineState
    data class Ready(val warning: String? = null) : SearchEngineState
    data class Failed(val reason: String) : SearchEngineState

    /** What it is doing before search can answer, in the one wording every search shows; null once it can. */
    val waiting: String?
        get() = when (this) {
            Installing -> "Installing ripgrep for workspace search…"
            Indexing -> "Indexing workspace files…"
            Unknown -> "Preparing workspace search…"
            is Ready, is Failed -> null
        }
}

/** One indexed workspace file used by Quick Open ranking. */
data class IndexedFile(
    val name: String,
    val path: String,
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
