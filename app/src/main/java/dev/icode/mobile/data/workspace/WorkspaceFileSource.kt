package dev.icode.mobile.data.workspace

import android.os.StatFs
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.io.readAtMost
import dev.icode.mobile.data.runtime.GuestPaths
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.StorageSummary
import dev.icode.mobile.domain.TextEncoding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class WorkspaceRoot(
    val name: String,
    val location: String,
    /** Its path in the runtime, `/workspace/<name>`: the path people see. */
    val displayPath: String,
)

internal data class WorkspaceDocument(
    val name: String,
    val location: String,
    val text: String,
    val encoding: TextEncoding,
    val lineEnding: LineEnding,
    val hasByteOrderMark: Boolean,
    val readOnly: Boolean,
    val fingerprint: String,
)

/** A save found a different file on disk from the one that was read: nothing was written. */
internal class ChangedOnDiskException : IOException("The file changed on disk since it was opened")

/**
 * Files as iCode reads and writes them (docs/workspace-storage-plan.md): the Explorer's listing of a
 * workspace, where a link leading out of it is not shown; the editor's and the assistant's reading
 * and saving, the assistant's anywhere in the runtime (docs/runtime-contract-plan.md §4.3); new files
 * and folders.
 */
internal class WorkspaceFileSource(private val guest: GuestPaths) {
    private val paths = guest.workspaces

    /** The folder every workspace is in: watched, so one made from the terminal appears. */
    val workspacesRoot: File get() = paths.root

    /** Space on the volume the workspaces live on. */
    fun storageSummary(): StorageSummary {
        paths.root.mkdirs()
        val stats = StatFs(paths.root.path)
        return StorageSummary(
            availableBytes = stats.availableBytes,
            totalBytes = stats.totalBytes,
        )
    }

    /** A workspace by its location: its name, and its path in the runtime. */
    fun describeRoot(location: String): WorkspaceRoot {
        val directory = requireWorkspace(location)
        return WorkspaceRoot(
            name = directory.name,
            location = directory.path,
            displayPath = paths.guestOf(directory.path) ?: throw IOException("This workspace no longer exists"),
        )
    }

    /** The workspace [location] lies in; null for the workspaces folder itself or outside it. */
    fun workspaceOf(location: String): String? = paths.workspaceOf(location)

    /** [location] is a workspace: a folder right under the workspaces, there now. */
    fun isWorkspace(location: String): Boolean = runCatching { requireWorkspace(location) }.isSuccess

    fun listChildren(parentLocation: String): List<DocumentNode> {
        val parent = requireDirectory(parentLocation)
        val workspace = paths.workspaceOf(parent.path)
        return parent.listFiles()?.asSequence()
            ?.filterNot { it.name == ".git" }
            ?.mapNotNull { child ->
                runCatching {
                    // A link (node_modules/.bin, a virtualenv's) is named where it is, so a rename,
                    // move or delete acts on the link and never on what it points to; it is shown
                    // only while that stays inside this workspace, followed as the runtime follows it.
                    val target = guest.follow(child.path)
                    if (paths.workspaceOf(target.path) != workspace) return@runCatching null
                    DocumentNode(
                        name = child.name,
                        location = child.path,
                        kind = if (target.isDirectory) FileKind.Folder else FileKind.File,
                    )
                }.getOrNull()
            }?.filterNotNull()?.toList().orEmpty().sortedWith(
                compareBy<DocumentNode> { it.kind != FileKind.Folder }.thenBy { it.name.lowercase() },
            )
    }

    /** Creates only a direct child and never truncates an existing document. */
    fun createFile(parentLocation: String, name: String): String {
        val parent = requireDirectory(parentLocation)
        val target = File(parent, name).canonicalFile
        if (target.parentFile?.canonicalFile != parent.canonicalFile) throw IOException("Invalid file destination")
        if (!target.createNewFile()) throw IOException("A file or folder with this name already exists")
        return target.path
    }

    fun readDocument(location: String): WorkspaceDocument {
        val file = requireFile(location)
        if (file.length() > MAX_TEXT_BYTES) throw NotTextException(TOO_LARGE)
        val bytes = file.inputStream().use(::readBounded)
        val decoded = DocumentCodec.decode(bytes)
        return WorkspaceDocument(
            name = file.name,
            location = file.path,
            text = decoded.text,
            encoding = decoded.encoding,
            lineEnding = decoded.lineEnding,
            hasByteOrderMark = decoded.hasByteOrderMark,
            readOnly = !file.canWrite(),
            fingerprint = fingerprint(bytes),
        )
    }

    /** Written beside the file and renamed over it: a save is whole or not at all. */
    fun writeDocument(document: WorkspaceDocument, text: String): WorkspaceDocument {
        if (document.readOnly) throw IOException("This document is read-only")
        val target = requireFile(document.location)
        val currentBytes = target.inputStream().use(::readBounded)
        if (fingerprint(currentBytes) != document.fingerprint) {
            throw ChangedOnDiskException()
        }

        val savedBytes = DocumentCodec.encode(text, document.encoding, document.hasByteOrderMark)
        val temporary = File.createTempFile(".${target.name}.icode-", ".tmp", target.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(savedBytes)
                output.flush()
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
        val decoded = DocumentCodec.decode(savedBytes)
        return document.copy(
            text = text,
            lineEnding = decoded.lineEnding,
            fingerprint = fingerprint(savedBytes),
        )
    }

    private fun requireWorkspace(location: String): File {
        val directory = File(location).canonicalFile
        if (paths.workspaceOf(directory.path) != directory.path || !directory.isDirectory) {
            throw IOException("This workspace no longer exists")
        }
        return directory
    }

    /** [path], its links followed as the runtime follows them, as a folder that can be read. */
    private fun requireDirectory(path: String): File {
        val directory = guest.follow(path)
        if (!directory.isDirectory || !directory.canRead()) throw IOException("This directory is unavailable")
        return directory
    }

    /** [path], its links followed as the runtime follows them, as a file that can be read. */
    private fun requireFile(path: String): File {
        val file = guest.follow(path)
        if (!file.isFile || !file.canRead()) throw IOException("This file is unavailable")
        return file
    }

    private fun readBounded(input: InputStream): ByteArray =
        input.readAtMost(MAX_TEXT_BYTES, TOO_LARGE)

    private fun fingerprint(bytes: ByteArray): String = Sha256.hex(bytes)

    private companion object {
        const val MAX_TEXT_BYTES = 5 * 1024 * 1024
        val TOO_LARGE = "Files larger than ${ByteSize.format(MAX_TEXT_BYTES.toLong())} are not supported yet"
    }
}
