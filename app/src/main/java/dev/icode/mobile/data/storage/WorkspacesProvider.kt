package dev.icode.mobile.data.storage

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import dev.icode.mobile.data.io.FileHeads
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.domain.FileType
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.WorkspaceNameRules
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * The workspaces in Android's Files app, and to every app the user picks them for
 * (docs/workspace-storage-plan.md §4.6). One root, iCode. A document's ID is its path in the
 * runtime — `/workspace/app/src` — the path iCode shows everywhere. Only the workspaces are
 * exposed: an ID must stay inside them, and so must the file it leads to, so a link never takes
 * another app into the runtime or the rest of iCode's data. A whole workspace is made here with
 * New folder, but renamed, moved or deleted only in iCode, which follows its settings, history
 * and sessions.
 */
class WorkspacesProvider : DocumentsProvider() {
    private val paths by lazy { WorkspacePaths.of(requireContext()) }
    private val authority by lazy { authorityOf(requireContext()) }

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: ROOT_COLUMNS).apply {
        newRow().add(Root.COLUMN_ROOT_ID, ROOT_ID)
            .add(Root.COLUMN_DOCUMENT_ID, WorkspacePaths.GUEST_ROOT)
            .add(Root.COLUMN_TITLE, "iCode")
            .add(Root.COLUMN_SUMMARY, "Workspaces")
            .add(Root.COLUMN_ICON, requireContext().applicationInfo.icon)
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_LOCAL_ONLY)
            .add(Root.COLUMN_AVAILABLE_BYTES, paths.root.apply { mkdirs() }.usableSpace)
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply { addRow(this, fileOf(documentId)) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val parent = directoryOf(parentDocumentId)
        val atRoot = parent == paths.root
        return MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            parent.listFiles().orEmpty()
                // At the top, only what the launcher lists: a folder whose name is not iCode's staging.
                .filter { if (atRoot) !it.name.startsWith('.') && Files.isDirectory(it.toPath(), LinkOption.NOFOLLOW_LINKS) else it.exists() && leadsInside(it) }
                .forEach { addRow(this, it) }
            setNotificationUri(requireContext().contentResolver, DocumentsContract.buildChildDocumentsUri(authority, parentDocumentId))
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = fileOf(documentId)
        if (!file.isFile) throw FileNotFoundException("$documentId is not a file")
        val bits = ParcelFileDescriptor.parseMode(mode)
        if (bits and ParcelFileDescriptor.MODE_WRITE_ONLY == 0 && bits and ParcelFileDescriptor.MODE_READ_WRITE == 0) {
            return ParcelFileDescriptor.open(file, bits)
        }
        // Written by another app: its folder's listing is read again once it is closed.
        return ParcelFileDescriptor.open(file, bits, Handler(Looper.getMainLooper())) { file.parentFile?.let { changed(it) } }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = directoryOf(parentDocumentId)
        val folder = mimeType == Document.MIME_TYPE_DIR
        val rule = if (parent == paths.root) {
            if (!folder) throw FileNotFoundException("Only a workspace — a folder — can be made here")
            WorkspaceNameRules.workspaceNameError(displayName)
        } else nameError(displayName, folder)
        rule?.let { throw FileNotFoundException(it) }
        val made = freeName(parent, displayName.trim()).let { target -> if (folder) target.takeIf { it.mkdir() } else target.takeIf { it.createNewFile() } }
            ?: throw FileNotFoundException("$displayName could not be made")
        changed(parent)
        return idOf(made)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = changeable(documentId)
        nameError(displayName, file.isDirectory)?.let { throw FileNotFoundException(it) }
        val target = File(file.parentFile, displayName.trim())
        if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) throw FileNotFoundException("${target.name} already exists")
        val oldParent = file.parentFile
        Files.move(file.toPath(), target.toPath())
        if (oldParent != null) changed(oldParent)
        return idOf(target)
    }

    override fun deleteDocument(documentId: String) {
        val file = changeable(documentId)
        val oldParent = file.parentFile
        // A link goes as the link it is; what it points to stays.
        deleteTree(file)
        if (oldParent != null) changed(oldParent)
    }

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String {
        val file = changeable(sourceDocumentId)
        val destination = directoryOf(targetParentDocumentId)
        if (destination == paths.root) throw FileNotFoundException("A workspace is made in iCode, or with New folder here")
        if (destination.path.startsWith(file.path + "/")) throw FileNotFoundException("A folder cannot be moved into itself")
        val target = File(destination, file.name)
        if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) throw FileNotFoundException("${file.name} already exists there")
        val oldParent = file.parentFile
        Files.move(file.toPath(), target.toPath())
        if (oldParent != null) changed(oldParent)
        changed(destination)
        return idOf(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith(parentDocumentId.trimEnd('/') + "/") && runCatching { fileOf(documentId) }.isSuccess

    /** A document's file: its ID a path in the workspaces, and the file it leads to there too. */
    private fun fileOf(documentId: String): File {
        val segments = documentId.removePrefix(WorkspacePaths.GUEST_ROOT).split('/').drop(1)
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) throw FileNotFoundException("No document $documentId")
        val file = paths.locationOf(documentId)?.let(::File) ?: throw FileNotFoundException("No document $documentId")
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) || !leadsInside(file)) throw FileNotFoundException("No document $documentId")
        return file
    }

    private fun directoryOf(documentId: String) = fileOf(documentId).takeIf { it.isDirectory } ?: throw FileNotFoundException("$documentId is not a folder")

    /** Inside a workspace, not a workspace itself: those are iCode's to rename, move and delete. */
    private fun changeable(documentId: String): File = fileOf(documentId).also { file ->
        if (file.parentFile == paths.root) throw FileNotFoundException("Rename, move or delete a whole workspace in iCode")
    }

    private fun leadsInside(file: File) = runCatching { paths.workspaceOf(file.canonicalPath) != null || file.canonicalFile == paths.root }.getOrDefault(false)

    private fun idOf(file: File) = paths.guestOf(file.path) ?: throw FileNotFoundException("${file.name} is not in the workspaces")

    private fun addRow(cursor: MatrixCursor, file: File) {
        val folder = file.isDirectory
        val top = file == paths.root
        val workspace = file.parentFile == paths.root
        var flags = if (folder) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE
        if (!top && !workspace) flags = flags or Document.FLAG_SUPPORTS_RENAME or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_MOVE
        cursor.newRow().add(Document.COLUMN_DOCUMENT_ID, idOf(file))
            .add(Document.COLUMN_DISPLAY_NAME, if (top) "iCode" else file.name)
            .add(Document.COLUMN_MIME_TYPE, if (folder) Document.MIME_TYPE_DIR else mimeOf(file))
            .add(Document.COLUMN_SIZE, if (folder) null else file.length())
            .add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            .add(Document.COLUMN_FLAGS, flags)
    }

    private fun mimeOf(file: File) = mimeTypeOf(file) ?: "application/octet-stream"

    /** The Explorer's rules for a name inside a workspace. */
    private fun nameError(name: String, folder: Boolean) =
        if (folder) WorkspaceNameRules.workspaceFolderValidationError(name) else WorkspaceNameRules.fileValidationError(name)

    /** As Android names a copy: "name (1).ext". */
    private fun freeName(parent: File, name: String): File {
        val base = name.substringBeforeLast('.', name).ifEmpty { name }
        val extension = name.removePrefix(base)
        return generateSequence(0) { it + 1 }
            .map { index -> File(parent, if (index == 0) name else "$base ($index)$extension") }
            .first { !Files.exists(it.toPath(), LinkOption.NOFOLLOW_LINKS) }
    }

    private fun changed(folder: File) = idOf(folder).let { id ->
        requireContext().contentResolver.notifyChange(DocumentsContract.buildChildDocumentsUri(authority, id), null)
    }

    companion object {
        private const val ROOT_ID = "icode"
        private val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY,
            Root.COLUMN_ICON, Root.COLUMN_FLAGS, Root.COLUMN_AVAILABLE_BYTES,
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
        )

        /**
         * A file's kind as other apps are told it (docs/file-kinds-icons-plan.md D4): Android's name for
         * its extension, unless iCode's own table says it is text and Android calls it a video or a sound
         * — Android's `.ts` is an MPEG transport stream, TypeScript's is text. Null for one Android does
         * not know.
         */
        fun mimeTypeOf(name: String, type: FileType = FileTypes.of(name)): String? {
            val android = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            val media = android != null && (android.startsWith("video/") || android.startsWith("audio/"))
            return if (type.text && media) TEXT else android
        }

        /** As [mimeTypeOf], [file]'s first bytes read where its name cannot say what it is. */
        fun mimeTypeOf(file: File): String? = mimeTypeOf(file.name, FileHeads.typeOf(file))

        private const val TEXT = "text/plain"

        /** `<applicationId>.workspaces`, as the manifest declares it. */
        fun authorityOf(context: Context) = "${context.packageName}.workspaces"

        /**
         * Something in the workspaces changed in iCode or its terminal: every listing the Files app
         * shows is read again. Every listing's address is under this one.
         */
        fun changed(context: Context) =
            context.contentResolver.notifyChange(Uri.Builder().scheme("content").authority(authorityOf(context)).build(), null)
    }
}
