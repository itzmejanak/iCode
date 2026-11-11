package dev.icode.mobile.data.search

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.ContentSearchState
import dev.icode.mobile.domain.IndexedFile
import dev.icode.mobile.domain.notices.SearchNotices
import dev.icode.mobile.domain.SearchEngineState
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Search (S4): cancellable ripgrep content search and Quick Open indexing. */
internal class SearchCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val ripgrep: WorkspaceSearchEngine,
    private val activeRoot: () -> String?,
) {
    private val indexGeneration = AtomicInteger()
    private val contentGeneration = AtomicInteger()
    private var indexJob: Job? = null
    private var contentJob: Job? = null
    @Volatile private var indexedWorkspace: String? = null
    @Volatile private var indexingWorkspace: String? = null
    private var reportedProvisionFailure: Pair<String, String>? = null

    /** The index no longer matches the workspace: rebuild only if Quick Open had asked for it. */
    fun workspaceChanged(root: String) {
        val wasUsed = indexedWorkspace == root || indexingWorkspace == root
        invalidateIndex()
        if (wasUsed) prepareIndex(root)
    }

    fun invalidateIndex() {
        indexGeneration.incrementAndGet()
        indexJob?.cancel()
        indexJob = null
        indexedWorkspace = null
        indexingWorkspace = null
        stateHolder.update { it.copy(fileIndex = emptyList(), fileIndexTruncated = false) }
    }

    fun workspaceClosing() {
        invalidateIndex()
        clear()
    }

    /** Builds Quick Open's index without holding the gateway mutation lock. */
    fun prepareIndex(root: String) {
        if (root != activeRoot()) return
        if (indexedWorkspace == root) return
        val generation = indexGeneration.incrementAndGet()
        indexJob?.cancel()
        indexingWorkspace = root
        indexJob = scope.launch {
            val unavailable = ensureEngine()
            if (unavailable != null) {
                publishIndex(generation, root, emptyList(), truncated = false, SearchEngineState.Failed(unavailable))
                return@launch
            }
            if (generation == indexGeneration.get() && root == activeRoot()) {
                stateHolder.update { it.copy(searchEngine = SearchEngineState.Indexing) }
            }
            try {
                val outcome = ripgrep.index(root)
                val state = SearchEngineState.Ready(outcome.warning)
                publishIndex(generation, root, outcome.files, outcome.truncated, state)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = stateHolder.failureText("search", "Unable to index workspace files", error)
                publishIndex(generation, root, emptyList(), truncated = false, SearchEngineState.Failed(message))
            }
        }
    }

    /** A new query cancels the old `rg` process; only this generation may publish. */
    fun runContentSearch(query: String, matchCase: Boolean, wholeWord: Boolean, regex: Boolean) {
        val root = activeRoot()
        val normalized = query.trim()
        if (root == null) {
            scope.launch { stateHolder.tell(SearchNotices.openWorkspaceFirst()) }
            return
        }
        if (normalized.isBlank()) {
            clear()
            return
        }
        val generation = contentGeneration.incrementAndGet()
        contentJob?.cancel()
        contentJob = scope.launch {
            val unavailable = ensureEngine()
            if (unavailable != null) {
                publishContentError(generation, root, normalized, matchCase, wholeWord, regex, unavailable)
                return@launch
            }
            try {
                val outcome = ripgrep.search(root, normalized, matchCase, wholeWord, regex)
                if (!isCurrentContent(generation, root)) return@launch
                stateHolder.update { snapshot -> snapshot.copy(
                    contentSearch = ContentSearchState(
                        query = normalized,
                        matchCase = matchCase,
                        wholeWord = wholeWord,
                        regex = regex,
                        results = outcome.results,
                        searchedFiles = outcome.searchedFiles,
                        truncated = outcome.truncated,
                        error = outcome.warning,
                    ),
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = stateHolder.failureText("search", "Unable to search workspace files", error)
                publishContentError(generation, root, normalized, matchCase, wholeWord, regex, message)
            }
        }
    }

    fun clear() {
        contentGeneration.incrementAndGet()
        contentJob?.cancel()
        contentJob = null
        stateHolder.update { snapshot -> snapshot.copy(contentSearch = ContentSearchState.Empty) }
    }

    private suspend fun ensureEngine(): String? {
        if (!ripgrep.isAvailable()) {
            stateHolder.update { it.copy(searchEngine = SearchEngineState.Installing) }
        }
        val availability = try {
            ripgrep.ensureAvailable()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            RipgrepAvailability.Failed(error.message?.takeIf(String::isNotBlank) ?: "Ripgrep is unavailable")
        }
        return when (availability) {
            RipgrepAvailability.Ready -> {
                reportedProvisionFailure = null
                stateHolder.update { it.copy(searchEngine = SearchEngineState.Ready()) }
                null
            }
            is RipgrepAvailability.Failed -> {
                val message = synchronized(this) {
                    val reported = reportedProvisionFailure
                    if (reported?.first == availability.reason) reported.second
                    else stateHolder.failureText("search", "Ripgrep is unavailable", IOException(availability.reason)).also {
                        reportedProvisionFailure = availability.reason to it
                    }
                }
                stateHolder.update { it.copy(searchEngine = SearchEngineState.Failed(message)) }
                message
            }
        }
    }

    private fun publishIndex(
        generation: Int,
        root: String,
        files: List<IndexedFile>,
        truncated: Boolean,
        engine: SearchEngineState,
    ) {
        if (generation != indexGeneration.get() || root != activeRoot()) return
        indexedWorkspace = root.takeIf { engine is SearchEngineState.Ready }
        indexingWorkspace = null
        stateHolder.update { it.copy(fileIndex = files, fileIndexTruncated = truncated, searchEngine = engine) }
    }

    private fun publishContentError(
        generation: Int,
        root: String,
        query: String,
        matchCase: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
        message: String,
    ) {
        if (!isCurrentContent(generation, root)) return
        stateHolder.update { snapshot -> snapshot.copy(
            contentSearch = ContentSearchState(
                query = query,
                matchCase = matchCase,
                wholeWord = wholeWord,
                regex = regex,
                results = emptyList(),
                searchedFiles = 0,
                truncated = false,
                error = message,
            ),
        ) }
    }

    private fun isCurrentContent(generation: Int, root: String): Boolean =
        generation == contentGeneration.get() && root == activeRoot()
}
