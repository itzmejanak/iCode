package dev.icode.mobile.data.workspace

import dev.icode.mobile.domain.notices.EditorNotices
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.RecoverableDraft
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.withDocument
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Unsaved work that must survive the process: the draft kept for every edited document, the question a
 * edited document, the question a leftover draft raises when its file opens,
 * and Document Recovery's list of what the last session left behind.
 *
 * Draft files are written off the mutation lock under their own [draftMutex],
 * so slow storage never holds up the editor; and [draftDelayMillis] after a
 * file's last edit, not at every keystroke (docs/design-and-navigation.md).
 */
internal class DocumentRecoveryCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val draftStore: DocumentDraftStore,
    private val draftDelayMillis: Long = DRAFT_DELAY_MILLIS,
    /** The file as read from disk, for a document that is open. */
    private val sourceOf: (location: String) -> WorkspaceDocument?,
) {
    private val draftMutex = Mutex()
    private val lastDraftText = mutableMapOf<String, String>()

    /** Each file's draft waiting out [draftDelayMillis]; a newer edit replaces it. */
    private val waiting = ConcurrentHashMap<String, Job>()

    /** Opened with a draft that differs from disk and not yet answered: read-only until it is. */
    private val unresolved = mutableSetOf<String>()

    /** Restored from a draft taken before the file changed on disk: saving over it needs a yes. */
    private val recoveredOverChanges = mutableSetOf<String>()

    fun isUnresolved(location: String): Boolean = location in unresolved

    fun needsOverwriteConsent(location: String): Boolean = location in recoveredOverChanges

    /**
     * Drafts present at start are the work the last session never saved — what
     * Document Recovery offers. Drafts written from now on are this session's
     * live work, so the list only shrinks. Held under [draftMutex] with every
     * change to it, so a draft discarded meanwhile cannot come back.
     */
    suspend fun loadRecoverable() {
        runCatching {
            draftMutex.withLock {
                val drafts = draftStore.list().map { draft ->
                    RecoverableDraft(draft.location, draft.name, draft.sizeBytes, draft.savedAtEpochMillis)
                }
                stateHolder.update { it.copy(recoverableDrafts = drafts) }
            }
        }.onFailure { error ->
            stateHolder.tell(EditorNotices.draftsUnread(), "documents", error)
        }
    }

    /** A file just opened: a leftover draft that differs locks it and asks which to keep. */
    suspend fun opened(document: WorkspaceDocument) {
        val draft = draftMutex.withLock { draftStore.read(document.location) } ?: return
        if (draft.text == document.text) {
            discard(document.location)
            return
        }
        unresolved += document.location
        stateHolder.update { state -> state.withDocument(document.location) { it.copy(readOnly = true) } }
        stateHolder.emit(ICodeEffect.ConfirmDraftRecovery(
            location = document.location,
            name = document.name,
            sourceChanged = draft.sourceFingerprint != document.fingerprint,
        ))
    }

    /**
     * After an edit: the draft of unsaved text is written [draftDelayMillis] after the last edit, as
     * VS Code backs up a dirty file; a file back to clean has its draft removed at once.
     */
    fun sync(location: String) {
        if (stateHolder.current.openDocuments.firstOrNull { it.location == location }?.dirty != true) {
            waiting.remove(location)?.cancel()
            scope.launch { keepDraft(location) }
            return
        }
        val timer = scope.launch(start = CoroutineStart.LAZY) {
            delay(draftDelayMillis)
            val self = coroutineContext.job
            // Once begun, a write is not cut short by the next edit's timer.
            withContext(NonCancellable) { keepDraft(location, self) }
        }
        waiting.put(location, timer)?.cancel()
        timer.start()
    }

    /** iCode leaves the screen and Android may end the process: every waiting draft is written now. */
    fun flush() {
        waiting.keys.forEach { location ->
            waiting.remove(location)?.let { timer ->
                timer.cancel()
                scope.launch { keepDraft(location) }
            }
        }
    }

    /**
     * Writes the document's draft if it has unsaved text, removes it if not. For a [timer]'s write,
     * only while that timer still stands: a file saved, closed or edited again meanwhile has its
     * draft from what came after.
     */
    private suspend fun keepDraft(location: String, timer: Job? = null) {
        runCatching {
            draftMutex.withLock {
                if (timer != null && !waiting.remove(location, timer)) return@withLock
                val document = stateHolder.current.openDocuments.firstOrNull { it.location == location }
                    ?: return@withLock
                val sourceDocument = sourceOf(location) ?: return@withLock
                if (document.dirty) {
                    if (lastDraftText[location] != document.text) {
                        draftStore.write(location, document.name, document.text, sourceDocument.fingerprint)
                        lastDraftText[location] = document.text
                    }
                } else {
                    draftStore.delete(location)
                    lastDraftText.remove(location)
                }
            }
        }.onFailure { error ->
            stateHolder.tell(EditorNotices.draftNotKept(stateHolder.current, location), "documents", error)
        }
    }

    /** Callers hold [draftMutex]: [location]'s draft is settled by what they do. */
    private fun stopWaiting(location: String) {
        waiting.remove(location)?.cancel()
    }

    suspend fun restore(location: String) {
        val draft = draftMutex.withLock { draftStore.read(location) } ?: return
        val document = stateHolder.current.openDocuments.firstOrNull { it.location == location } ?: return
        if (document.dirty) {
            stateHolder.tell(EditorNotices.newerEditsFirst(stateHolder.current, location))
            return
        }
        val sourceDocument = sourceOf(location) ?: return
        if (draft.text == document.text) {
            discard(location)
            return
        }
        if (draft.sourceFingerprint != sourceDocument.fingerprint) recoveredOverChanges += location
        unresolved.remove(location)
        // Back in the editor, the draft is this session's live work again.
        draftMutex.withLock { forgetRecoverable(listOf(location)) }
        stateHolder.update { state -> state.withDocument(location) { it.copy(text = draft.text, dirty = true, readOnly = sourceDocument.readOnly) } }
        sync(location)
    }

    /** "Keep disk version": the draft goes and the file opens for editing again. */
    suspend fun keepDiskVersion(location: String) {
        if (stateHolder.current.openDocuments.firstOrNull { it.location == location }?.dirty == true) {
            stateHolder.tell(EditorNotices.newEditsFirst(stateHolder.current, location))
            return
        }
        discard(location)
        val sourceReadOnly = sourceOf(location)?.readOnly ?: false
        stateHolder.update { state -> state.withDocument(location) { it.copy(readOnly = sourceReadOnly) } }
    }

    suspend fun discard(location: String) {
        draftMutex.withLock {
            stopWaiting(location)
            draftStore.delete(location)
            lastDraftText.remove(location)
            forgetRecoverable(listOf(location))
        }
        unresolved.remove(location)
    }

    /**
     * Before a save of a clean document that is still waiting on its recovery
     * question: asks it again instead. True when the save may go ahead.
     */
    suspend fun clearToSave(location: String, document: OpenDocument, source: WorkspaceDocument): Boolean {
        if (document.dirty || location !in unresolved) return true
        draftMutex.withLock { draftStore.read(location) }?.let { draft ->
            stateHolder.emit(ICodeEffect.ConfirmDraftRecovery(
                location,
                document.name,
                draft.sourceFingerprint != source.fingerprint,
            ))
            return false
        }
        unresolved.remove(location)
        return true
    }

    /** After a save: the draft follows whatever is still unsaved. */
    suspend fun saved(location: String) {
        recoveredOverChanges.remove(location)
        runCatching {
            draftMutex.withLock {
                stopWaiting(location)
                val latest = stateHolder.current.openDocuments.firstOrNull { it.location == location }
                if (latest?.dirty == true) {
                    val refreshedSource = sourceOf(location)
                    if (refreshedSource != null) {
                        draftStore.write(location, latest.name, latest.text, refreshedSource.fingerprint)
                        lastDraftText[location] = latest.text
                    }
                } else {
                    draftStore.delete(location)
                    lastDraftText.remove(location)
                }
            }
        }.onFailure { error ->
            stateHolder.tell(EditorNotices.draftNotUpdated(stateHolder.current, location), "documents", error)
        }
    }

    /** One tab closed; an unanswered recovery keeps its draft for next time. */
    suspend fun closed(document: OpenDocument) {
        recoveredOverChanges.remove(document.location)
        if (document.location in unresolved && !document.dirty) {
            unresolved.remove(document.location)
        } else {
            discard(document.location)
        }
    }

    /** Several tabs closed at once; as for one, an unanswered recovery keeps its draft. */
    suspend fun closedAll(documents: List<OpenDocument>) {
        documents.forEach { recoveredOverChanges.remove(it.location) }
        draftMutex.withLock {
            documents.forEach { document ->
                if (document.location !in unresolved || document.dirty) {
                    stopWaiting(document.location)
                    draftStore.delete(document.location)
                    lastDraftText.remove(document.location)
                }
            }
        }
        documents.forEach { unresolved.remove(it.location) }
    }

    /**
     * The workspace is switching: the drafts of [closing] go (the user was asked
     * about unsaved edits); unanswered recoveries stay on disk for next time.
     */
    suspend fun workspaceClosing(closing: List<String>) {
        recoveredOverChanges.clear()
        unresolved.clear()
        draftMutex.withLock {
            closing.forEach { location ->
                stopWaiting(location)
                draftStore.delete(location)
                lastDraftText.remove(location)
            }
            forgetRecoverable(closing)
        }
    }

    /** Callers hold [draftMutex]. */
    private fun forgetRecoverable(locations: Collection<String>) {
        if (stateHolder.current.recoverableDrafts.none { it.location in locations }) return
        stateHolder.update { state ->
            state.copy(recoverableDrafts = state.recoverableDrafts.filterNot { it.location in locations })
        }
    }

    private companion object {
        /** VS Code's backup delay after an edit (`DEFAULT_BACKUP_SCHEDULE_DELAYS.default`, workingCopyBackupTracker.ts). */
        const val DRAFT_DELAY_MILLIS = 1000L
    }
}
