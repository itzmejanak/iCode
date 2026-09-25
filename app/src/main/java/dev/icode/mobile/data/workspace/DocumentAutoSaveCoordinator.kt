package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.AutoSaveMode
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Debounces each dirty document; the gateway remains the sole guarded disk-write path. */
internal class DocumentAutoSaveCoordinator(
    private val gateway: WorkbenchGateway,
    private val mode: AutoSaveMode,
) {
    private data class Pending(val workspace: String, val text: String, val job: Job)

    suspend fun run() = coroutineScope {
        val pending = mutableMapOf<String, Pending>()
        val delayMillis = mode.delayMillis ?: return@coroutineScope
        try {
            gateway.snapshot.collect { snapshot ->
                val root = snapshot.activeWorkspaceLocation
                val dirty = if (root == null) emptyMap() else snapshot.openDocuments
                    .filter { it.dirty && !it.readOnly }
                    .associateBy { it.location }

                pending.keys.toList().forEach { location ->
                    val previous = pending.getValue(location)
                    val document = dirty[location]
                    if (document == null || previous.workspace != root || previous.text != document.text) {
                        pending.remove(location)?.job?.cancel()
                    }
                }
                if (root != null) dirty.forEach { (location, document) ->
                    if (location !in pending) {
                        val capturedText = document.text
                        val job = launch {
                            delay(delayMillis)
                            val current = gateway.snapshot.value
                            val stillCurrent = current.isCurrentAutoSaveRequest(location, root, capturedText)
                            if (stillCurrent) gateway.dispatch(
                                WorkbenchIntent.SaveDocument(
                                    location = location,
                                    automatic = true,
                                    expectedText = capturedText,
                                    expectedWorkspace = root,
                                ),
                            )
                        }
                        pending[location] = Pending(root, capturedText, job)
                    }
                }
            }
        } finally {
            pending.values.forEach { it.job.cancel() }
        }
    }
}

/** The same revision guard is checked before scheduling and again inside the gateway's write lock. */
internal fun WorkbenchSnapshot.isCurrentAutoSaveRequest(
    location: String,
    workspace: String?,
    text: String?,
): Boolean = workspace != null && text != null && activeWorkspaceLocation == workspace &&
    openDocuments.any { it.location == location && it.dirty && !it.readOnly && it.text == text }
