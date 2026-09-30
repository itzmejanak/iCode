package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.ExternalChange
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.reorderDocuments
import dev.icode.mobile.domain.selectedAfterClose
import dev.icode.mobile.domain.withDocument
import dev.icode.mobile.domain.withoutExternalChange
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The editor's open files (S3): open, edit, save, select, reorder, close. A
 * save writes only over the version that was read — a file changed on disk
 * meanwhile is refused, never overwritten.
 */
internal class DocumentSessionCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val source: WorkspaceFileSource,
    private val recovery: DocumentRecoveryCoordinator,
    private val library: WorkspaceLibraryCoordinator,
    private val activeRoot: () -> String?,
    /** A save changed the worktree, so Git's view of it is stale. */
    private val onSaved: () -> Unit,
) {
    /** Each open file as read from disk: what a save must still find there. */
    private val sources = ConcurrentHashMap<String, WorkspaceDocument>()
    private val savedTexts = ConcurrentHashMap<String, String>()

    /** Files already said to be gone from disk, so a refresh does not repeat it. */
    private val goneNotified = mutableSetOf<String>()

    fun sourceOf(location: String): WorkspaceDocument? = sources[location]

    /** [announce] false when reopening the last session: nothing navigates, recents stay as they are. */
    suspend fun open(location: String, lineNumber: Int? = null, column: Int? = null, announce: Boolean = true) {
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
            if (announce) {
                activeRoot()?.let { library.recordRecentDocument(it, document.location) }
                workbench.emit(WorkbenchEffect.DocumentOpened(location, lineNumber, column))
            }
            recovery.opened(document)
        }.onFailure { error ->
            workbench.report("documents", "Unable to open document", error)
        }
    }

    /** Keystrokes: never behind the mutation lock. */
    fun updateText(intent: WorkbenchIntent.UpdateDocumentText) {
        workbench.update { state ->
            state.withDocument(intent.location) { document ->
                if (document.readOnly) document
                else document.copy(text = intent.text, dirty = intent.text != savedTexts[intent.location])
            }
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
        // Changed on disk under these edits: nothing is written until the user picks a version.
        if (workbench.current.externalChanges.any { it.location == location }) {
            if (!automatic) workbench.emit(WorkbenchEffect.ExternalChangeDetected(location))
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
            workbench.update { state -> state.withDocument(location) { it.copy(dirty = it.text != textBeingSaved) } }
            // Saving changes the worktree, so Source Control and the Explorer
            // decorations are stale the moment the write lands.
            onSaved()
            true
        }.onFailure { error ->
            if (error is ChangedOnDiskException) changedUnderEdits(location, openDocument.name)
            else workbench.report("documents", "Unable to save document", error)
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

    /**
     * An open file at or under [path] whose edits an Explorer operation would
     * strand: unsaved text, or a draft question not yet answered.
     */
    fun blockingEdits(path: String): OpenDocument? = workbench.current.openDocuments.firstOrNull {
        it.location.isAtOrUnder(path) && (it.dirty || recovery.isUnresolved(it.location))
    }

    /** Tabs follow a rename or move of their file or of a folder above it. */
    fun relocate(from: String, to: String) {
        val moving = workbench.current.openDocuments.filter { it.location.isAtOrUnder(from) }
        if (moving.isEmpty()) return
        moving.forEach { document ->
            val target = document.location.relocated(from, to)
            sources.remove(document.location)?.let { sources[target] = it.copy(location = target, name = File(target).name) }
            savedTexts.remove(document.location)?.let { savedTexts[target] = it }
        }
        fun moved(document: OpenDocument) = if (!document.location.isAtOrUnder(from)) document else {
            val target = document.location.relocated(from, to)
            document.copy(location = target, name = File(target).name)
        }
        workbench.update { state ->
            state.copy(openDocuments = state.openDocuments.map(::moved), activeDocument = state.activeDocument?.let(::moved))
        }
    }

    /** Tabs of files a delete removed close; none had edits (the operation checks first). */
    fun closeDeleted(path: String) {
        val removed = workbench.current.openDocuments.filter { it.location.isAtOrUnder(path) }
        if (removed.isEmpty()) return
        forget(removed)
        workbench.update { state ->
            val remaining = state.openDocuments.filterNot { it.location.isAtOrUnder(path) }
            state.copy(
                openDocuments = remaining,
                activeDocument = state.activeDocument?.takeUnless { it.location.isAtOrUnder(path) } ?: remaining.firstOrNull(),
            )
        }
    }

    /** The workspace is switching: every tab goes (the user was asked about unsaved edits). */
    fun clear() {
        sources.clear()
        savedTexts.clear()
        goneNotified.clear()
    }

    /**
     * Compares every open file with disk (US-FILE-005): a clean one takes the
     * new version in place, one with unsaved edits is asked about, one deleted
     * is said once and stays open with its text.
     */
    suspend fun reconcileWithDisk() {
        for (document in workbench.current.openDocuments) {
            val read = sources[document.location] ?: continue
            val file = File(document.location)
            val disk = if (file.isFile) runCatching { source.readDocument(document.location) }.getOrNull() else null
            // Present but unreadable now (grown too large, say): nothing is claimed about it.
            val diskFingerprint = if (!file.exists()) null else disk?.fingerprint ?: read.fingerprint
            val asked = workbench.current.externalChanges.any { it.location == document.location }
            if (diskFingerprint != null) goneNotified.remove(document.location)
            when (decideOnDisk(read.fingerprint, diskFingerprint, document.dirty, recovery.isUnresolved(document.location), asked)) {
                DiskDecision.Keep -> if (asked && diskFingerprint == read.fingerprint) {
                    workbench.update { it.withoutExternalChange(document.location) }
                }
                DiskDecision.Reload -> disk?.let(::takeDiskVersion)
                DiskDecision.Ask -> changedUnderEdits(document.location, document.name)
                DiskDecision.Gone -> if (goneNotified.add(document.location)) {
                    workbench.message("${document.name} was deleted outside iCode; its text stays open here")
                }
            }
        }
    }

    /** The disk wins: the unsaved edits are dropped, by the user's choice. */
    suspend fun reloadFromDisk(location: String) {
        val disk = runCatching { source.readDocument(location) }.getOrElse { error ->
            workbench.report("documents", "Unable to read the file on disk", error)
            return
        }
        takeDiskVersion(disk)
        recovery.discard(location)
    }

    /** The editor wins: its text is saved over exactly the version now on disk. */
    suspend fun overwriteDisk(location: String) {
        val disk = runCatching { source.readDocument(location) }.getOrElse { error ->
            workbench.report("documents", "Unable to read the file on disk", error)
            return
        }
        sources[location] = disk
        workbench.update { it.withoutExternalChange(location) }
        save(location)
    }

    private fun takeDiskVersion(disk: WorkspaceDocument) {
        sources[disk.location] = disk
        savedTexts[disk.location] = disk.text
        workbench.update { state ->
            state.withDocument(disk.location) {
                it.copy(text = disk.text, encoding = disk.encoding, lineEnding = disk.lineEnding, dirty = false, readOnly = disk.readOnly)
            }.withoutExternalChange(disk.location)
        }
    }

    private suspend fun changedUnderEdits(location: String, name: String) {
        if (workbench.current.externalChanges.any { it.location == location }) return
        val root = activeRoot()
        val path = root?.let { runCatching { File(it).toPath().relativize(File(location).toPath()).toString() }.getOrNull() } ?: name
        val change = ExternalChange(location, name, path, File(location).lastModified())
        workbench.update { it.copy(externalChanges = it.externalChanges + change) }
        workbench.emit(WorkbenchEffect.ExternalChangeDetected(location))
    }

    private fun forget(documents: List<OpenDocument>) {
        documents.forEach { document ->
            sources.remove(document.location)
            savedTexts.remove(document.location)
            goneNotified.remove(document.location)
        }
        val closed = documents.map { it.location }.toSet()
        workbench.update { state -> state.copy(externalChanges = state.externalChanges.filterNot { it.location in closed }) }
    }
}
