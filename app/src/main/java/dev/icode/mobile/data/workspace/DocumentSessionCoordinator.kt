package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.reorderDocuments
import dev.icode.mobile.domain.selectedAfterClose
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The editor's open files (S3): open, edit, save, select, reorder, close. A
 * save writes only over the version that was read — a file changed on disk
 * meanwhile is refused, never overwritten.
 */
internal class DocumentSessionCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val source: DirectFileWorkspaceSource,
    private val recovery: DocumentRecoveryCoordinator,
    private val library: WorkspaceLibraryCoordinator,
    private val activeRoot: () -> String?,
    /** A save changed the worktree, so Git's view of it is stale. */
    private val onSaved: () -> Unit,
) {
    /** Each open file as read from disk: what a save must still find there. */
    private val sources = ConcurrentHashMap<String, WorkspaceDocument>()
    private val savedTexts = ConcurrentHashMap<String, String>()

    fun sourceOf(location: String): WorkspaceDocument? = sources[location]

    suspend fun open(location: String, lineNumber: Int? = null, column: Int? = null) {
        val existing = workbench.current.openDocuments.firstOrNull { it.location == location }
        if (existing != null) {
            select(location)
            activeRoot()?.let { library.recordRecentDocument(it, location) }
            workbench.emit(WorkbenchEffect.DocumentOpened(location, lineNumber, column))
            return
        }
        val root = activeRoot()
        if (root == null || !runCatching {
                File(location).canonicalFile.toPath().startsWith(File(root).canonicalFile.toPath())
            }.getOrDefault(false)) {
            workbench.message("The selected file is outside the active workspace")
            return
        }
        runCatching {
            val document = source.readDocument(location)
            sources[document.location] = document
            savedTexts[document.location] = document.text
            val opened = OpenDocument(
                name = document.name,
                location = document.location,
                text = document.text,
                encoding = document.encoding,
                lineEnding = document.lineEnding,
                dirty = false,
                readOnly = document.readOnly,
            )
            workbench.update { snapshot -> snapshot.copy(
                openDocuments = snapshot.openDocuments + opened,
                activeDocument = opened,
            ) }
            activeRoot()?.let { library.recordRecentDocument(it, document.location) }
            workbench.emit(WorkbenchEffect.DocumentOpened(location, lineNumber, column))
            recovery.opened(document)
        }.onFailure { error ->
            workbench.report("documents", "Unable to open document", error)
        }
    }

    /** Keystrokes: never behind the mutation lock. */
    fun updateText(intent: WorkbenchIntent.UpdateDocumentText) {
        workbench.update { state ->
            val document = state.openDocuments.firstOrNull { it.location == intent.location }
                ?: return@update state
            if (document.readOnly) return@update state
            val changed = document.copy(
                text = intent.text,
                dirty = intent.text != savedTexts[intent.location],
            )
            state.copy(
                openDocuments = state.openDocuments.map { if (it.location == intent.location) changed else it },
                activeDocument = if (state.activeDocument?.location == intent.location) changed else state.activeDocument,
            )
        }
        recovery.sync(intent.location)
    }

    suspend fun save(
        location: String,
        automatic: Boolean = false,
        expectedText: String? = null,
        expectedWorkspace: String? = null,
        allowRecoveredOverwrite: Boolean = false,
    ): Boolean {
        val sourceDocument = sources[location]
        val openDocument = workbench.current.openDocuments.firstOrNull { it.location == location }
        if (automatic && (expectedWorkspace != activeRoot() ||
                !workbench.current.isCurrentAutoSaveRequest(location, expectedWorkspace, expectedText))) return false
        if (sourceDocument == null || openDocument == null) {
            workbench.message("The document is no longer open")
            return false
        }
        if (!recovery.clearToSave(location, openDocument, sourceDocument)) return false
        if (recovery.needsOverwriteConsent(location) && !allowRecoveredOverwrite) {
            if (!automatic) workbench.emit(WorkbenchEffect.ConfirmRecoveredOverwrite(location, openDocument.name))
            return false
        }
        val textBeingSaved = openDocument.text
        val saved = runCatching {
            sources[location] = source.writeDocument(sourceDocument, textBeingSaved)
            savedTexts[location] = textBeingSaved
            workbench.update { state ->
                val current = state.openDocuments.firstOrNull { it.location == location } ?: return@update state
                val saved = current.copy(dirty = current.text != textBeingSaved)
                state.copy(
                    openDocuments = state.openDocuments.map { if (it.location == location) saved else it },
                    activeDocument = if (state.activeDocument?.location == location) saved else state.activeDocument,
                )
            }
            // Saving changes the worktree, so Source Control and the Explorer
            // decorations are stale the moment the write lands.
            onSaved()
            true
        }.onFailure { error ->
            workbench.report("documents", "Unable to save document", error)
        }.getOrDefault(false)
        if (!saved) return false
        recovery.saved(location)
        workbench.emit(WorkbenchEffect.DocumentSaved(location, automatic))
        return true
    }

    fun select(location: String) {
        workbench.update { state ->
            val selected = state.openDocuments.firstOrNull { it.location == location }
            if (selected == null) state else state.copy(activeDocument = selected)
        }
    }

    fun reorder(movingLocation: String, targetLocation: String) {
        workbench.update { state ->
            state.copy(openDocuments = reorderDocuments(state.openDocuments, movingLocation, targetLocation))
        }
    }

    suspend fun close(location: String, saveChanges: Boolean?) {
        val document = workbench.current.openDocuments.firstOrNull { it.location == location } ?: return
        if (document.dirty && saveChanges == null) {
            workbench.message("Choose Save, Discard or Cancel before closing ${document.name}")
            return
        }
        if (document.dirty && saveChanges == true && !save(location)) return
        workbench.update { state ->
            val nextLocation = selectedAfterClose(state.openDocuments, state.activeDocument?.location, location)
            val remaining = state.openDocuments.filterNot { it.location == location }
            state.copy(openDocuments = remaining, activeDocument = remaining.firstOrNull { it.location == nextLocation })
        }
        forget(listOf(document))
        recovery.closed(document)
    }

    suspend fun closeOthers(keepLocation: String, saveChanges: Boolean?) {
        val others = workbench.current.openDocuments.filterNot { it.location == keepLocation }
        if (others.any { it.dirty } && saveChanges == null) {
            workbench.message("Choose Save, Discard or Cancel before closing dirty tabs")
            return
        }
        if (saveChanges == true) {
            for (document in others.filter { it.dirty }) if (!save(document.location)) return
        }
        forget(others)
        recovery.closedAll(others)
        workbench.update { state ->
            val kept = state.openDocuments.firstOrNull { it.location == keepLocation }
            state.copy(openDocuments = listOfNotNull(kept), activeDocument = kept)
        }
    }

    suspend fun closeSaved() {
        val removed = workbench.current.openDocuments.filterNot { it.dirty }
        forget(removed)
        recovery.closedAll(removed)
        workbench.update { state ->
            val remaining = state.openDocuments.filter { it.dirty }
            state.copy(openDocuments = remaining, activeDocument = remaining.firstOrNull { it.location == state.activeDocument?.location } ?: remaining.firstOrNull())
        }
    }

    /** The workspace is switching: every tab goes (the user was asked about unsaved edits). */
    fun clear() {
        sources.clear()
        savedTexts.clear()
    }

    private fun forget(documents: List<OpenDocument>) {
        documents.forEach { document ->
            sources.remove(document.location)
            savedTexts.remove(document.location)
        }
    }
}
