package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.assistant.tools.bounded
import dev.icode.mobile.domain.AssistantAttachment
import dev.icode.mobile.domain.AssistantAttachmentKind
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.ByteSize
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

/** A file from the phone's storage, as Android names it: its name, its type, and a way to read it. */
internal class PhoneFile(val name: String, val mimeType: String?, val open: () -> InputStream)

/** The phone's files a picker handed over (`content://`), read through Android ([dev.icode.mobile.data.assistant.ContentResolverPhoneFiles]). */
internal fun interface PhoneFiles {
    /** Null when it can no longer be read: the picker's grant ended with the process. */
    fun open(uri: String): PhoneFile?
}

/**
 * Files sent with a message: the workspace's own — its links followed as the tools
 * follow them, an open tab's text as shown — and the phone's, from a picker or the camera. Text goes as text,
 * bounded; an image as JPEG ([prepareImage]); a PDF as itself. What the model cannot read is refused
 * before anything is read; whatever fails, in the words the page shows.
 */
internal class AssistantAttachments(
    private val phone: PhoneFiles,
    /** An image as it is sent — oriented, at most 1 568 px, JPEG — or null when it is not one this phone reads. */
    private val prepareImage: (ByteArray) -> ByteArray?,
    /** An open tab's text, unsaved edits and all; null when the file is not open. */
    private val openText: (String) -> String?,
    /** A file's text as saved. */
    private val readText: (String) -> String,
) {
    /**
     * [locations] for [model], kept by [keep] (an image's or a PDF's bytes, in its chat's folder,
     * returning where). Anything kept is removed again should a later file fail.
     */
    fun read(
        workspace: AssistantWorkspace,
        locations: List<String>,
        model: AssistantModel,
        keep: (ByteArray, String) -> String,
    ): List<AssistantAttachment> {
        val sources = locations.distinct().take(AssistantRules.MAX_ATTACHMENTS).map { source(workspace, it) }
        AssistantRules.attachmentProblem(sources.map { it.kind }, model)?.let { throw AssistantTurnException(it) }
        val kept = mutableListOf<String>()
        try {
            return sources.map { source -> attachment(source) { bytes, extension -> keep(bytes, extension).also(kept::add) } }
        } catch (failure: Exception) {
            if (failure !is CancellationException) kept.forEach { File(it).delete() }
            throw failure
        }
    }

    private class Source(val name: String, val mimeType: String?, val inWorkspace: Boolean, val text: () -> String, val bytes: (limit: Long) -> ByteArray) {
        val kind = AssistantRules.attachmentKind(name, mimeType)
    }

    private fun attachment(source: Source, keep: (ByteArray, String) -> String): AssistantAttachment = when (source.kind) {
        AssistantAttachmentKind.Text -> {
            val text = source.text()
            if ('\u0000' in text) throw AssistantTurnException("${source.name} is not text — only text, images and PDFs can be sent")
            AssistantAttachment(source.name, AssistantAttachmentKind.Text, text = bounded(text, MAX_CHARACTERS), inWorkspace = source.inWorkspace)
        }
        AssistantAttachmentKind.Image -> {
            val jpeg = prepareImage(source.bytes(MAX_IMAGE_BYTES)) ?: throw AssistantTurnException("${source.name} is not an image this phone can read")
            AssistantAttachment(source.name, AssistantAttachmentKind.Image, file = keep(jpeg, "jpg"), mimeType = "image/jpeg", inWorkspace = source.inWorkspace)
        }
        AssistantAttachmentKind.Document -> {
            val pdf = source.bytes(MAX_DOCUMENT_BYTES)
            if (!pdf.startsWith(PDF_MAGIC)) throw AssistantTurnException("${source.name} is not a PDF")
            AssistantAttachment(source.name, AssistantAttachmentKind.Document, file = keep(pdf, "pdf"), mimeType = "application/pdf", inWorkspace = source.inWorkspace)
        }
    }

    private fun source(workspace: AssistantWorkspace, location: String): Source =
        if (location.startsWith(CONTENT)) phoneSource(location) else workspaceSource(workspace, location)

    private fun workspaceSource(workspace: AssistantWorkspace, location: String): Source {
        // Named from the workspace's top, where the page opens it again.
        val path = workspace.relative(File(location)) ?: throw AssistantTurnException("${File(location).name} is not in this workspace")
        val file = runCatching { workspace.resolve(path) }.getOrElse { throw AssistantTurnException("$path cannot be read") }
        if (!file.isFile) throw AssistantTurnException("$path is not a file")
        return Source(
            name = path,
            mimeType = null,
            inWorkspace = true,
            text = { runCatching { openText(file.path) ?: readText(file.path) }.getOrElse { throw AssistantTurnException("Unable to read $path") } },
            bytes = { limit ->
                if (file.length() > limit) throw AssistantTurnException("$path is larger than ${ByteSize.format(limit)}")
                runCatching { file.readBytes() }.getOrElse { throw AssistantTurnException("Unable to read $path") }
            },
        )
    }

    private fun phoneSource(uri: String): Source {
        val file = runCatching { phone.open(uri) }.getOrNull() ?: throw AssistantTurnException("A file from the phone can no longer be read — attach it again")
        return Source(
            name = file.name,
            mimeType = file.mimeType,
            inWorkspace = false,
            text = { String(read(file, MAX_TEXT_BYTES), Charsets.UTF_8) },
            bytes = { limit -> read(file, limit) },
        )
    }

    /** All of [file], refused past [limit] bytes; a grant gone or a stream refused reads as unreadable. */
    private fun read(file: PhoneFile, limit: Long): ByteArray {
        val bytes = try {
            file.open().use { input -> input.readAtMost(limit) }
        } catch (failure: IOException) {
            throw AssistantTurnException("Unable to read ${file.name} — attach it again")
        } catch (failure: SecurityException) {
            throw AssistantTurnException("Unable to read ${file.name} — attach it again")
        }
        if (bytes.size > limit) throw AssistantTurnException("${file.name} is larger than ${ByteSize.format(limit)}")
        return bytes
    }

    /** Up to just past [limit] bytes: enough to tell it is too large. (`readNBytes` is Android 13's.) */
    private fun InputStream.readAtMost(limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        while (out.size() <= limit) {
            val count = read(buffer)
            if (count < 0) break
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        /** Characters of a text file the model reads; a longer one is sent as its start and its end. */
        const val MAX_CHARACTERS = 64_000

        /** A text file from the phone, read whole before it is bounded: the editor's own limit. */
        const val MAX_TEXT_BYTES = 5L * 1024 * 1024

        /** A photo as taken; it is sent much smaller. */
        const val MAX_IMAGE_BYTES = 25L * 1024 * 1024

        /** A PDF as sent: within every provider's limit. */
        const val MAX_DOCUMENT_BYTES = 10L * 1024 * 1024

        private const val CONTENT = "content://"
        private const val BUFFER = 64 * 1024
        private val PDF_MAGIC = "%PDF".toByteArray(Charsets.US_ASCII)
    }
}
