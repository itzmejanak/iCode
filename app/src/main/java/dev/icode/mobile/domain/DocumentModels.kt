package dev.icode.mobile.domain

// Files and editing (S3): the tree, open documents, recents and recovery drafts.

data class FileEntry(
    val name: String,
    val path: String,
    val kind: FileKind,
    val depth: Int = 0,
    val expanded: Boolean = false,
    val gitStatus: String? = null,
)

enum class FileKind { Folder, TypeScript, Code, Json, Markdown, Other }

enum class TextEncoding { Utf8, Utf16LittleEndian, Utf16BigEndian }

enum class LineEnding { Lf, CrLf, Cr, None }

data class OpenDocument(
    val name: String,
    val location: String,
    val text: String,
    val encoding: TextEncoding,
    val lineEnding: LineEnding,
    val dirty: Boolean,
    val readOnly: Boolean,
)

/**
 * Unsaved text a session left behind when it ended: the document was edited and
 * never saved, and the draft kept the edits (Document Recovery).
 */
data class RecoverableDraft(
    val location: String,
    val name: String,
    val sizeBytes: Long,
    val savedAtEpochMillis: Long,
)

/** A file opened in the active workspace, including entries that have since gone missing. */
data class RecentDocument(
    val name: String,
    val location: String,
    val path: String,
    val lastOpened: String,
    val available: Boolean = true,
)
