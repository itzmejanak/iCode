package dev.icode.mobile.data.storage

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException
import java.io.InputStream

/**
 * The folder Android's folder picker returned for Import, read through its provider with the grant
 * the picker gave: for this import only, nothing is kept (docs/workspace-storage-plan.md §4.5).
 */
internal class DocumentTreeImport(private val resolver: ContentResolver, private val tree: Uri) : ImportTree {

    override val top: ImportEntry =
        query(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), "The chosen folder")
            .firstOrNull() ?: throw IOException("The chosen folder could not be read")

    override fun children(folder: ImportEntry): List<ImportEntry> =
        query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.id), folder.name)

    override fun open(file: ImportEntry): InputStream =
        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, file.id))
            ?: throw IOException("${file.name} could not be read")

    private fun query(uri: Uri, what: String): List<ImportEntry> {
        val cursor = resolver.query(uri, COLUMNS, null, null, null) ?: throw IOException("$what could not be read")
        return cursor.use {
            // A provider still fetching the listing (a cloud folder) would give half of it.
            if (it.extras?.getBoolean(DocumentsContract.EXTRA_LOADING) == true) {
                throw IOException("$what is still loading in its app. Try again once it has loaded")
            }
            buildList { while (it.moveToNext()) add(entryOf(it)) }
        }
    }

    private fun entryOf(cursor: Cursor) = ImportEntry(
        id = cursor.getString(0),
        name = cursor.getString(1).orEmpty(),
        folder = cursor.getString(2) == Document.MIME_TYPE_DIR,
        size = if (cursor.isNull(3)) 0 else cursor.getLong(3),
        modified = if (cursor.isNull(4)) 0 else cursor.getLong(4),
        virtual = !cursor.isNull(5) && cursor.getInt(5) and Document.FLAG_VIRTUAL_DOCUMENT != 0,
    )

    private companion object {
        val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
    }
}
