package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.files.FileShortcuts
import dev.icode.mobile.domain.isAtOrUnder
import dev.icode.mobile.domain.relocated
import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.domain.ExternalChange
import dev.icode.mobile.domain.notices.NoticeTone
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.reorderDocuments
import dev.icode.mobile.domain.selectedAfterClose
import dev.icode.mobile.domain.withDocument
import dev.icode.mobile.domain.withoutExternalChange
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The editor's open files (S3): open, edit, save, select, reorder, close. A save writes only over
 * the version that was read — a file changed on disk meanwhile is refused, never overwritten.
 */
internal class DocumentSessionCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val source: WorkspaceFileSource,
    private val recovery: DocumentRecoveryCoordinator,
    private val shortcuts: FileShortcuts,
    private val paths: GuestPaths,
    private val activeRoot: () -> String?,
    /** A save changed the worktree at a location: Git's view of it is stale, its language servers are told. */
    private val onSaved: (String) -> Unit,
) {
    /** Each open file as read from disk: what a save must still find there. */
    private val sources = ConcurrentHashMap<String, WorkspaceDocument>()
    private val savedTexts = ConcurrentHashMap<String, String>()

    /** Files already said to be gone from disk, so a refresh does not repeat it. */
    private val goneNotified = mutableSetOf<String>()

    fun sourceOf(location: String): WorkspaceDocument? = sources[location]

    /** [announce] false when reopening the last session: nothing navigates, recents stay as they are. */
    suspend fun open(location: String, lineNumber: Int? = null, column: Int? = null, announce: Boolean = true) {
        val existing = stateHolder.current.openDocuments.firstOrNull { it.location == location }
        if (existing != null) {
            select(location)
            shortcuts.opened(location)
            stateHolder.emit(ICodeEffect.DocumentOpened(location, lineNumber, column))
            return
        }
        val root = activeRoot()
        if (root == null || paths.within(root, location) == null) {
            stateHolder.notify(NoticeTone.HeadsUp, "The selected file is outside the active workspace")
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
            stateHolder.update { it.copy(openDocuments = it.openDocuments + opened, activeDocument = opened) }
            if (announce) {
                shortcuts.opened(document.location)
                stateHolder.emit(ICodeEffect.DocumentOpened(location, lineNumber, column))
            }
            recovery.opened(document)
        }.onFailure { error ->
            if (error is NotTextException && announce) stateHolder.emit(ICodeEffect.OpenInOtherApp(location))
            else stateHolder.report("documents", "Unable to open document", error)
        }
    }

    /** Keystrokes: never behind the mutation lock. */
    fun updateText(intent: ICodeIntent.UpdateDocumentText) {
        stateHolder.update { state ->
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
        val openDocument = stateHolder.current.openDocuments.firstOrNull { it.location == location }
        if (automatic && (expectedWorkspace != activeRoot() ||
                !stateHolder.current.isCurrentAutoSaveRequest(location, expectedWorkspace, expectedText))) return false
        if (sourceDocument == null || openDocument == null) {
            stateHolder.notify(NoticeTone.HeadsUp, "The document is no longer open")
            return false
        }
        // Changed on disk under these edits: nothing is written until the user picks a version.
        if (stateHolder.current.externalChanges.any { it.location == location }) {
            if (!automatic) stateHolder.emit(ICodeEffect.ExternalChangeDetected(location))
            return false
        }
        if (!recovery.clearToSave(location, openDocument, sourceDocument)) return false
        if (recovery.needsOverwriteConsent(location) && !allowRecoveredOverwrite) {
            if (!automatic) stateHolder.emit(ICodeEffect.ConfirmRecoveredOverwrite(location, openDocument.name))
            return false
        }
        val textBeingSaved = openDocument.text
        val saved = runCatching {
            sources[location] = source.writeDocument(sourceDocument, textBeingSaved)
            savedTexts[location] = textBeingSaved
            stateHolder.update { state -> state.withDocument(location) { it.copy(dirty = it.text != textBeingSaved) } }
            // The worktree changed: Source Control and the Explorer's decorations are stale; the file goes to Recent's top.
            onSaved(location)
            shortcuts.opened(location)
            true
        }.onFailure { error ->
            if (error is ChangedOnDiskException) changedUnderEdits(location, openDocument.name)
            else stateHolder.report("documents", "Unable to save document", error)
        }.getOrDefault(false)
        if (!saved) return false
        recovery.saved(location)
        if (!automatic) stateHolder.notify(NoticeTone.Done, "Saved")
        return true
    }

    fun select(location: String) {
        stateHolder.update { state ->
            val selected = state.openDocuments.firstOrNull { it.location == location }
            if (selected == null) state else state.copy(activeDocument = selected)
        }
    }

    fun reorder(movingLocation: String, targetLocation: String) {
        stateHolder.update { state ->
            state.copy(openDocuments = reorderDocuments(state.openDocuments, movingLocation, targetLocation))
        }
    }

    suspend fun close(location: String, saveChanges: Boolean?) {
        val document = stateHolder.current.openDocuments.firstOrNull { it.location == location } ?: return
        if (document.dirty && saveChanges == null) {
            stateHolder.notify(NoticeTone.HeadsUp, "Choose Save, Discard or Cancel before closing ${document.name}")
            return
        }
        if (document.dirty && saveChanges == true && !save(location)) return
        stateHolder.update { state ->
            val nextLocation = selectedAfterClose(state.openDocuments, state.activeDocument?.location, location)
            val remaining = state.openDocuments.filterNot { it.location == location }
            state.copy(openDocuments = remaining, activeDocument = remaining.firstOrNull { it.location == nextLocation })
        }
        forget(listOf(document))
        recovery.closed(document)
    }

    suspend fun closeOthers(keepLocation: String, saveChanges: Boolean?) {
        val others = stateHolder.current.openDocuments.filterNot { it.location == keepLocation }
        if (others.any { it.dirty } && saveChanges == null) {
            stateHolder.notify(NoticeTone.HeadsUp, "Choose Save, Discard or Cancel before closing dirty tabs")
            return
        }
        if (saveChanges == true) {
            for (document in others.filter { it.dirty }) if (!save(document.location)) return
        }
        forget(others)
        recovery.closedAll(others)
        stateHolder.update { state ->
            val kept = state.openDocuments.firstOrNull { it.location == keepLocation }
            state.copy(openDocuments = listOfNotNull(kept), activeDocument = kept)
        }
    }

    suspend fun closeSaved() {
        val removed = stateHolder.current.openDocuments.filterNot { it.dirty }
        forget(removed)
        recovery.closedAll(removed)
        stateHolder.update { state ->
            val remaining = state.openDocuments.filter { it.dirty }
            state.copy(openDocuments = remaining, activeDocument = remaining.firstOrNull { it.location == state.activeDocument?.location } ?: remaining.firstOrNull())
        }
    }

    /**
     * An open file at or under [path] whose edits an Explorer operation would
     * strand: unsaved text, or a draft question not yet answered.
     */
    fun blockingEdits(path: String): OpenDocument? = stateHolder.current.openDocuments.firstOrNull {
        it.location.isAtOrUnder(path) && (it.dirty || recovery.isUnresolved(it.location))
    }

    /** Tabs follow a rename or move of their file or of a folder above it. */
    fun relocate(from: String, to: String) {
        fun moved(document: OpenDocument) = if (!document.location.isAtOrUnder(from)) document
        else document.location.relocated(from, to).let { target -> document.copy(location = target, name = File(target).name) }
        val moving = stateHolder.current.openDocuments.filter { it.location.isAtOrUnder(from) }
        if (moving.isEmpty()) return
        moving.forEach { document ->
            val target = moved(document)
            sources.remove(document.location)?.let { sources[target.location] = it.copy(location = target.location, name = target.name) }
            savedTexts.remove(document.location)?.let { savedTexts[target.location] = it }
        }
        stateHolder.update { state ->
            state.copy(openDocuments = state.openDocuments.map(::moved), activeDocument = state.activeDocument?.let(::moved))
        }
    }

    /** Tabs of files a delete removed close; none had edits (the operation checks first). */
    fun closeDeleted(path: String) {
        val removed = stateHolder.current.openDocuments.filter { it.location.isAtOrUnder(path) }
        if (removed.isEmpty()) return
        forget(removed)
        stateHolder.update { state ->
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
        for (document in stateHolder.current.openDocuments) {
            val read = sources[document.location] ?: continue
            val file = File(document.location)
            val disk = if (file.isFile) runCatching { source.readDocument(document.location) }.getOrNull() else null
            // Present but unreadable now (grown too large, say): nothing is claimed about it.
            val diskFingerprint = if (!file.exists()) null else disk?.fingerprint ?: read.fingerprint
            val asked = stateHolder.current.externalChanges.any { it.location == document.location }
            if (diskFingerprint != null) goneNotified.remove(document.location)
            when (decideOnDisk(read.fingerprint, diskFingerprint, document.dirty, recovery.isUnresolved(document.location), asked)) {
                DiskDecision.Keep -> if (asked && diskFingerprint == read.fingerprint) {
                    stateHolder.update { it.withoutExternalChange(document.location) }
                }
                DiskDecision.Reload -> disk?.let(::takeDiskVersion)
                DiskDecision.Ask -> changedUnderEdits(document.location, document.name)
                DiskDecision.Gone -> if (goneNotified.add(document.location)) {
                    stateHolder.notify(NoticeTone.Info, "${document.name} was deleted outside iCode; its text stays open here")
                }
            }
        }
    }

    /** The disk wins: the unsaved edits are dropped, by the user's choice. */
    suspend fun reloadFromDisk(location: String) {
        val disk = runCatching { source.readDocument(location) }.getOrElse { error ->
            stateHolder.report("documents", "Unable to read the file on disk", error)
            return
        }
        takeDiskVersion(disk)
        recovery.discard(location)
    }

    /** The editor wins: its text is saved over exactly the version now on disk. */
    suspend fun overwriteDisk(location: String) {
        val disk = runCatching { source.readDocument(location) }.getOrElse { error ->
            stateHolder.report("documents", "Unable to read the file on disk", error)
            return
        }
        sources[location] = disk
        stateHolder.update { it.withoutExternalChange(location) }
        save(location)
    }

    private fun takeDiskVersion(disk: WorkspaceDocument) {
        sources[disk.location] = disk
        savedTexts[disk.location] = disk.text
        stateHolder.update { state ->
            state.withDocument(disk.location) {
                it.copy(text = disk.text, encoding = disk.encoding, lineEnding = disk.lineEnding, dirty = false, readOnly = disk.readOnly)
            }.withoutExternalChange(disk.location)
        }
    }

    private suspend fun changedUnderEdits(location: String, name: String) {
        if (stateHolder.current.externalChanges.any { it.location == location }) return
        val root = activeRoot()
        val path = root?.let { runCatching { File(it).toPath().relativize(File(location).toPath()).toString() }.getOrNull() } ?: name
        val change = ExternalChange(location, name, path, File(location).lastModified())
        stateHolder.update { it.copy(externalChanges = it.externalChanges + change) }
        stateHolder.emit(ICodeEffect.ExternalChangeDetected(location))
    }

    private fun forget(documents: List<OpenDocument>) {
        documents.forEach { document ->
            sources.remove(document.location)
            savedTexts.remove(document.location)
            goneNotified.remove(document.location)
        }
        val closed = documents.map { it.location }.toSet()
        stateHolder.update { state -> state.copy(externalChanges = state.externalChanges.filterNot { it.location in closed }) }
    }
}
