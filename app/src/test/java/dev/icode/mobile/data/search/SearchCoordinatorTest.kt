package dev.icode.mobile.data.search

import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.ContentMatch
import dev.icode.mobile.domain.ContentSearchResult
import dev.icode.mobile.domain.IndexedFile
import dev.icode.mobile.domain.SearchEngineState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCoordinatorTest {
    @Test fun `new content query cancels old process and only publishes new result`() = runBlocking {
        val workbench = WorkbenchStateHolder()
        val engine = FakeEngine()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = SearchCoordinator(workbench, scope, engine) { "/workspace" }
        try {
            coordinator.runContentSearch("first", matchCase = true, wholeWord = false, regex = false)
            engine.firstSearchStarted.await()

            coordinator.runContentSearch("second", matchCase = true, wholeWord = false, regex = false)
            val state = withTimeout(2_000) {
                workbench.snapshot.first { it.contentSearch.query == "second" }
            }

            withTimeout(2_000) { engine.firstSearchCancelled.await() }
            assertEquals("second", state.contentSearch.query)
            assertEquals("second", state.contentSearch.results.single().matches.single().lineText)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `index from previous workspace is cancelled and cannot publish`() = runBlocking {
        val workbench = WorkbenchStateHolder()
        val engine = FakeEngine()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var root = "/one"
        val coordinator = SearchCoordinator(workbench, scope, engine) { root }
        try {
            coordinator.prepareIndex(root)
            engine.firstIndexStarted.await()

            coordinator.invalidateIndex()
            root = "/two"
            coordinator.prepareIndex(root)
            val state = withTimeout(2_000) {
                workbench.snapshot.first { it.fileIndex.singleOrNull()?.path == "/two/new.kt" }
            }

            withTimeout(2_000) { engine.firstIndexCancelled.await() }
            assertEquals(listOf("/two/new.kt"), state.fileIndex.map { it.path })
        } finally {
            scope.cancel()
        }
    }

    @Test fun `first use says installing until shared provisioning completes`() = runBlocking {
        val workbench = WorkbenchStateHolder()
        val engine = FakeEngine(available = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = SearchCoordinator(workbench, scope, engine) { "/workspace" }
        try {
            coordinator.prepareIndex("/workspace")
            withTimeout(2_000) {
                workbench.snapshot.first { it.searchEngine == SearchEngineState.Installing }
            }
            engine.provisioning.complete(RipgrepAvailability.Ready)

            val state = withTimeout(2_000) {
                workbench.snapshot.first { it.searchEngine is SearchEngineState.Ready && it.fileIndex.isNotEmpty() }
            }
            assertTrue(state.fileIndex.isNotEmpty())
        } finally {
            scope.cancel()
        }
    }

    private class FakeEngine(available: Boolean = true) : WorkspaceSearchEngine {
        private var ready = available
        val provisioning = CompletableDeferred<RipgrepAvailability>()
        val firstSearchStarted = CompletableDeferred<Unit>()
        val firstSearchCancelled = CompletableDeferred<Unit>()
        val firstIndexStarted = CompletableDeferred<Unit>()
        val firstIndexCancelled = CompletableDeferred<Unit>()

        override fun isAvailable(): Boolean = ready

        override suspend fun ensureAvailable(): RipgrepAvailability {
            if (ready) return RipgrepAvailability.Ready
            return provisioning.await().also { if (it == RipgrepAvailability.Ready) ready = true }
        }

        override suspend fun index(rootLocation: String): RipgrepIndexOutcome {
            if (rootLocation == "/one") {
                firstIndexStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    firstIndexCancelled.complete(Unit)
                }
            }
            return RipgrepIndexOutcome(
                files = listOf(IndexedFile("new.kt", "$rootLocation/new.kt")),
                truncated = false,
                warning = null,
            )
        }

        override suspend fun search(
            rootLocation: String,
            query: String,
            matchCase: Boolean,
            wholeWord: Boolean,
            regex: Boolean,
        ): RipgrepSearchOutcome {
            if (query == "first") {
                firstSearchStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    firstSearchCancelled.complete(Unit)
                }
            }
            return RipgrepSearchOutcome(
                results = listOf(
                    ContentSearchResult(
                        path = "$rootLocation/result.txt",
                        name = "result.txt",
                        matches = listOf(ContentMatch(1, 0, query)),
                    ),
                ),
                searchedFiles = 1,
                truncated = false,
            )
        }
    }
}
