package dev.icode.mobile.data.search

import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.data.workspace.WorkspaceIndexer
import dev.icode.mobile.domain.ContentSearchState
import dev.icode.mobile.domain.IndexedFile
import java.util.regex.PatternSyntaxException

/** Search (S4): find in files, and the file index Quick Open ranks. */
internal class SearchCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val activeRoot: () -> String?,
) {
    @Volatile private var indexedWorkspace: String? = null

    /** The index no longer matches the workspace: it is rebuilt when next asked for. */
    fun invalidateIndex() {
        indexedWorkspace = null
    }

    suspend fun runContentSearch(query: String, matchCase: Boolean, wholeWord: Boolean, regex: Boolean) {
        val root = activeRoot()
        if (root == null) {
            workbench.message("Open a workspace before searching")
            return
        }
        if (query.isBlank()) {
            workbench.update { snapshot -> snapshot.copy(contentSearch = ContentSearchState.Empty) }
            return
        }
        try {
            val outcome = WorkspaceIndexer.search(root, query.trim(), matchCase, wholeWord, regex)
            workbench.update { snapshot -> snapshot.copy(
                contentSearch = ContentSearchState(
                    query = query.trim(),
                    matchCase = matchCase,
                    wholeWord = wholeWord,
                    regex = regex,
                    results = outcome.results,
                    searchedFiles = outcome.searchedFiles,
                    truncated = outcome.truncated,
                ),
            ) }
        } catch (error: PatternSyntaxException) {
            workbench.update { snapshot -> snapshot.copy(
                contentSearch = ContentSearchState(
                    query = query.trim(),
                    matchCase = matchCase,
                    wholeWord = wholeWord,
                    regex = regex,
                    results = emptyList(),
                    searchedFiles = 0,
                    truncated = false,
                    error = "Invalid regular expression: ${error.description ?: "check the pattern"}",
                ),
            ) }
        }
    }

    fun clear() {
        workbench.update { snapshot -> snapshot.copy(contentSearch = ContentSearchState.Empty) }
    }

    suspend fun ensureIndex(root: String): Pair<List<IndexedFile>, Boolean> {
        if (indexedWorkspace == root && workbench.current.fileIndex.isNotEmpty()) {
            return workbench.current.fileIndex to workbench.current.fileIndexTruncated
        }
        val (index, truncated) = WorkspaceIndexer.indexFiles(root)
        indexedWorkspace = root
        workbench.update { snapshot -> snapshot.copy(fileIndex = index, fileIndexTruncated = truncated) }
        return index to truncated
    }
}
