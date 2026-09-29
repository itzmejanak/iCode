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

/** A folder or a file; what kind of file is [FileTypes]' answer, from its name. */
enum class FileKind { Folder, File }

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

/**
 * An open file whose disk version moved while it had unsaved edits: the
 * External Change screen asks which version wins (US-EDIT-009). Until it is
 * answered, autosave leaves the file alone.
 */
data class ExternalChange(
    val location: String,
    val name: String,
    /** Relative to the workspace, as the screen and the comparison show it. */
    val path: String,
    val diskChangedAt: Long,
)

/** A file opened in the active workspace, including entries that have since gone missing. */
data class RecentDocument(
    val name: String,
    val location: String,
    val path: String,
    /** When it was last opened; screens say how long ago. */
    val lastOpenedAt: Long,
    val available: Boolean = true,
)
