package dev.icode.mobile.data.workspace

import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * Unsaved editor text, kept outside the workspace so it survives the process.
 *
 * Android kills a backgrounded app without warning, so the only work that
 * survives is work already written down. A draft is written as the user types
 * and removed the moment the document is saved, which is what makes "recover
 * unsaved work" possible at all.
 *
 * Drafts live in app-private storage, never in the user's workspace: a
 * half-typed file must never be picked up by a build, a search or Git.
 */
internal class DocumentDraftStore(private val root: File) {

    /**
     * One recoverable document.
     *
     * [sourceFingerprint] is the file as it was when the draft was taken, so a
     * restore can tell whether the file has since changed underneath it.
     */
    data class Draft(
        val location: String,
        val name: String,
        val text: String,
        val savedAtEpochMillis: Long,
        val sourceFingerprint: String,
    ) {
        val sizeBytes: Int get() = text.toByteArray().size
    }

    fun write(location: String, name: String, text: String, sourceFingerprint: String) {
        root.mkdirs()
        val draft = Draft(location, name, text, System.currentTimeMillis(), sourceFingerprint)
        // Written to a sibling then moved, so a kill mid-write cannot leave a
        // truncated draft in place of a good one.
        val target = fileFor(location)
        val pending = File(target.parentFile, target.name + PENDING_SUFFIX)
        pending.writeText(encode(draft))
        if (!pending.renameTo(target)) {
            target.writeText(encode(draft))
            pending.delete()
        }
    }

    fun read(location: String): Draft? = decode(fileFor(location))

    fun delete(location: String) {
        fileFor(location).delete()
    }

    /** Every draft currently held, newest first. */
    fun list(): List<Draft> = (root.listFiles().orEmpty())
        .filter { it.isFile && it.name.endsWith(DRAFT_SUFFIX) }
        .mapNotNull(::decode)
        .sortedByDescending { it.savedAtEpochMillis }

    fun clear() {
        root.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun fileFor(location: String): File {
        root.mkdirs()
        return File(root, digest(location) + DRAFT_SUFFIX)
    }

    /**
     * A small header plus the text.
     *
     * Header values are Base64 so a path containing a newline or a colon — both
     * legal on Android — cannot corrupt the record.
     */
    private fun encode(draft: Draft): String = buildString {
        appendLine(FORMAT_MARKER)
        appendLine(encodeValue(draft.location))
        appendLine(encodeValue(draft.name))
        appendLine(draft.savedAtEpochMillis.toString())
        appendLine(draft.sourceFingerprint)
        append(draft.text)
    }

    private fun decode(file: File): Draft? {
        if (!file.isFile) return null
        return runCatching {
            val raw = file.readText()
            val header = raw.split('\n', limit = HEADER_LINES + 1)
            if (header.size <= HEADER_LINES || header[0] != FORMAT_MARKER) return null
            Draft(
                location = decodeValue(header[1]),
                name = decodeValue(header[2]),
                text = header[HEADER_LINES],
                savedAtEpochMillis = header[3].toLong(),
                sourceFingerprint = header[4],
            )
        }.getOrNull()
    }

    private fun encodeValue(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray())

    private fun decodeValue(value: String): String =
        String(Base64.getDecoder().decode(value))

    private fun digest(location: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(location.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val FORMAT_MARKER = "icode-draft/1"
        const val DRAFT_SUFFIX = ".draft"
        const val PENDING_SUFFIX = ".pending"

        /** Marker, location, name, timestamp, fingerprint. */
        const val HEADER_LINES = 5
    }
}
