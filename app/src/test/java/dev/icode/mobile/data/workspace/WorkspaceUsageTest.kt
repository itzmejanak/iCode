package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitTransferLabel
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.RecoverableDraft
import dev.icode.mobile.domain.TextEncoding
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** One answer to "may this change now?", for a whole workspace and for an item in one. */
class WorkspaceUsageTest {
    private val originalWriter = BackendLog.writer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workbench = WorkbenchStateHolder()
    private val tasks = TaskRunner(workbench, scope)
    private val app = "/w/app"
    private var terminals = setOf<String>()
    private var processes = setOf<String>()
    private var unsaved: String? = null
    private val usage = WorkspaceUsage(
        workbench,
        tasks,
        terminalRunsIn = { it in terminals },
        processesRunIn = { it in processes },
        blockingEdits = { location -> unsaved?.takeIf { it.isAtOrUnder(location) }?.let { document(it) } },
        activeRoot = { app },
    )

    @Before fun quietLog() {
        BackendLog.writer = { _, _, _ -> }
    }

    @After fun stop() {
        scope.cancel()
        BackendLog.writer = originalWriter
    }

    private fun document(location: String) = OpenDocument(
        name = location.substringAfterLast('/'),
        location = location,
        text = "",
        encoding = TextEncoding.Utf8,
        lineEnding = LineEnding.Lf,
        dirty = true,
        readOnly = false,
    )

    @Test fun `nothing holding it, it may change`() = runBlocking {
        assertNull(usage.holding(app, whole = true))
        assertNull(usage.holding("$app/src", whole = false))
    }

    @Test fun `a terminal, a process and Git hold a whole workspace, not an item in it`() = runBlocking {
        terminals = setOf(app)
        assertEquals("A terminal session is running in app. End it first", usage.holding(app, whole = true))
        assertNull(usage.holding("$app/src", whole = false))
        terminals = emptySet()
        processes = setOf(app)
        assertEquals("A process is running in app. Stop it first", usage.holding(app, whole = true))
        assertNull(usage.holding("$app/src", whole = false))
        processes = emptySet()
        workbench.update { it.copy(gitOperation = GitOperationState.Running(GitTransferLabel.PULLING)) }
        assertEquals("Git is working in app — wait for it to finish", usage.holding(app, whole = true))
        assertNull(usage.holding("$app/src", whole = false))
    }

    @Test fun `unsaved edits and recovery drafts under it hold it, whole or not`() = runBlocking {
        unsaved = "$app/src/a.kt"
        assertEquals("a.kt has unsaved edits. Save or close it first", usage.holding("$app/src", whole = false))
        assertEquals("a.kt has unsaved edits. Save or close it first", usage.holding(app, whole = true))
        unsaved = null
        workbench.update { it.copy(recoverableDrafts = listOf(RecoverableDraft("$app/b.kt", "b.kt", 10, 0))) }
        assertEquals("app has unsaved work waiting in Document Recovery. Restore or discard it first", usage.holding(app, whole = true))
        assertNull(usage.holding("$app/src", whole = false))
    }

    @Test fun `a task holding it says what it does`() = runBlocking {
        val release = CountDownLatch(1)
        tasks.start(
            "Deleting…", "src", listOf("$app/src"), cancellable = false, failure = "Unable to delete",
            work = { release.await(5, TimeUnit.SECONDS) }, finished = {},
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (workbench.current.tasks.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertEquals("Deleting src — wait for it to finish", usage.holding("$app/src/a.kt", whole = false))
        release.countDown()
    }
}
