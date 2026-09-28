package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.WorkbenchEffect
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentRecoveryCoordinatorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val originalWriter = BackendLog.writer
    private val holder = WorkbenchStateHolder()
    private val sources = mutableMapOf<String, WorkspaceDocument>()
    private lateinit var drafts: DocumentDraftStore
    private lateinit var recovery: DocumentRecoveryCoordinator

    @Before fun setUp() {
        BackendLog.writer = { _, _, _ -> }
        drafts = DocumentDraftStore(File(temporaryFolder.root, "drafts"))
        // Unconfined: a draft write runs to completion inside the call that queued it.
        recovery = DocumentRecoveryCoordinator(holder, CoroutineScope(Dispatchers.Unconfined), drafts) { sources[it] }
    }

    @After fun restoreLog() {
        BackendLog.writer = originalWriter
    }

    private fun source(location: String, text: String, fingerprint: String = "disk-1", readOnly: Boolean = false) =
        WorkspaceDocument(
            name = location.substringAfterLast('/'),
            location = location,
            text = text,
            encoding = TextEncoding.Utf8,
            lineEnding = LineEnding.Lf,
            hasByteOrderMark = false,
            readOnly = readOnly,
            fingerprint = fingerprint,
        ).also { sources[location] = it }

    private fun open(document: WorkspaceDocument, text: String = document.text, dirty: Boolean = false): OpenDocument {
        val opened = OpenDocument(document.name, document.location, text, document.encoding, document.lineEnding, dirty, document.readOnly)
        holder.update { it.copy(openDocuments = it.openDocuments + opened, activeDocument = opened) }
        return opened
    }

    private fun tab(location: String) = holder.current.openDocuments.single { it.location == location }

    @Test fun `a draft equal to the file on disk is dropped without asking`() = runBlocking {
        val file = source("/w/a.kt", "same")
        drafts.write(file.location, file.name, "same", "disk-1")
        open(file)
        recovery.opened(file)
        assertNull(drafts.read(file.location))
        assertFalse(recovery.isUnresolved(file.location))
        assertFalse(tab(file.location).readOnly)
    }

    @Test fun `a different draft locks the tab and asks, saying whether the file changed since`() = runBlocking {
        val file = source("/w/a.kt", "on disk", fingerprint = "disk-2")
        drafts.write(file.location, file.name, "unsaved", "disk-1")
        open(file)
        val asked = async(start = CoroutineStart.UNDISPATCHED) { holder.effects.first() }
        recovery.opened(file)
        assertEquals(WorkbenchEffect.ConfirmDraftRecovery(file.location, file.name, sourceChanged = true), asked.await())
        assertTrue(tab(file.location).readOnly)
        assertEquals(tab(file.location), holder.current.activeDocument)
        assertTrue(recovery.isUnresolved(file.location))
    }

    @Test fun `keeping the disk version deletes the draft and unlocks the tab`() = runBlocking {
        val file = source("/w/a.kt", "on disk")
        drafts.write(file.location, file.name, "unsaved", "disk-1")
        open(file)
        recovery.opened(file)
        recovery.keepDiskVersion(file.location)
        assertNull(drafts.read(file.location))
        assertFalse(tab(file.location).readOnly)
        assertFalse(recovery.isUnresolved(file.location))
    }

    @Test fun `restoring puts the draft in the editor as unsaved work, off the recovery list`() = runBlocking {
        val file = source("/w/a.kt", "on disk", fingerprint = "disk-2")
        drafts.write(file.location, file.name, "unsaved", "disk-1")
        recovery.loadRecoverable()
        assertEquals(listOf(file.location), holder.current.recoverableDrafts.map { it.location })
        open(file)
        recovery.opened(file)
        recovery.restore(file.location)
        assertEquals("unsaved", tab(file.location).text)
        assertTrue(tab(file.location).dirty)
        assertFalse(tab(file.location).readOnly)
        assertTrue(holder.current.recoverableDrafts.isEmpty())
        // Taken before the file changed on disk: saving over it needs a yes.
        assertTrue(recovery.needsOverwriteConsent(file.location))
        assertNotNull(drafts.read(file.location))
    }

    @Test fun `closing an unanswered tab keeps its draft for next time, any other tab loses it`() = runBlocking {
        val waiting = source("/w/waiting.kt", "on disk")
        drafts.write(waiting.location, waiting.name, "unsaved", "disk-1")
        val waitingTab = open(waiting)
        recovery.opened(waiting)
        recovery.closed(waitingTab.copy(readOnly = true))
        assertNotNull(drafts.read(waiting.location))

        val edited = source("/w/edited.kt", "on disk")
        val editedTab = open(edited, text = "typed", dirty = true)
        recovery.sync(edited.location)
        assertEquals("typed", drafts.read(edited.location)?.text)
        recovery.closed(editedTab)
        assertNull(drafts.read(edited.location))
    }

    @Test fun `closing several tabs follows the same rule`() = runBlocking {
        val waiting = source("/w/waiting.kt", "on disk")
        drafts.write(waiting.location, waiting.name, "unsaved", "disk-1")
        val waitingTab = open(waiting)
        recovery.opened(waiting)
        val edited = source("/w/edited.kt", "on disk")
        val editedTab = open(edited, text = "typed", dirty = true)
        recovery.sync(edited.location)
        recovery.closedAll(listOf(waitingTab, editedTab))
        assertNotNull(drafts.read(waiting.location))
        assertNull(drafts.read(edited.location))
    }

    @Test fun `a save of an unanswered tab asks the recovery question again instead`() = runBlocking {
        val file = source("/w/a.kt", "on disk")
        drafts.write(file.location, file.name, "unsaved", "disk-1")
        val opened = open(file)
        recovery.opened(file)
        val asked = async(start = CoroutineStart.UNDISPATCHED) { holder.effects.first() }
        assertFalse(recovery.clearToSave(file.location, opened, file))
        assertEquals(WorkbenchEffect.ConfirmDraftRecovery(file.location, file.name, sourceChanged = false), asked.await())
    }

    @Test fun `switching workspace drops the drafts of the closing tabs only`() = runBlocking {
        val closing = source("/w/closing.kt", "on disk")
        drafts.write(closing.location, closing.name, "unsaved", "disk-1")
        val elsewhere = source("/other/b.kt", "on disk")
        drafts.write(elsewhere.location, elsewhere.name, "unsaved", "disk-1")
        recovery.loadRecoverable()
        recovery.workspaceClosing(listOf(closing.location))
        assertNull(drafts.read(closing.location))
        assertNotNull(drafts.read(elsewhere.location))
        assertEquals(listOf(elsewhere.location), holder.current.recoverableDrafts.map { it.location })
    }

    @Test fun `a draft follows the text, written while unsaved and removed once clean`() = runBlocking {
        val file = source("/w/a.kt", "on disk")
        open(file, text = "typed", dirty = true)
        recovery.sync(file.location)
        assertEquals("typed", drafts.read(file.location)?.text)
        holder.update { state -> state.copy(openDocuments = state.openDocuments.map { it.copy(text = "on disk", dirty = false) }) }
        recovery.sync(file.location)
        assertNull(drafts.read(file.location))
    }
}
