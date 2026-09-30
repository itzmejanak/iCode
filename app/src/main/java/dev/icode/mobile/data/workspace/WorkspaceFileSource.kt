package dev.icode.mobile.data.workspace

import android.os.StatFs
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.io.readAtMost
import dev.icode.mobile.data.storage.WorkspacePaths
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
 * The files of the workspaces in iCode's storage (docs/workspace-storage-plan.md): the Explorer's
 * listing, the editor's reading and saving, new files and folders. Nothing outside the workspaces
 * is read or written, and a link leading out of them is not shown.
 */
internal class WorkspaceFileSource(private val paths: WorkspacePaths) {
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
                    // only while that stays inside this workspace.
                    if (paths.workspaceOf(child.canonicalPath) != workspace) return@runCatching null
                    DocumentNode(
                        name = child.name,
                        location = child.path,
                        kind = if (child.isDirectory) FileKind.Folder else FileKind.File,
                    )
                }.getOrNull()
            }?.filterNotNull()?.toList().orEmpty().sortedWith(
                compareBy<DocumentNode> { it.kind != FileKind.Folder }.thenBy { it.name.lowercase() },
            )
    }

    fun createDirectory(parentLocation: String, name: String): String {
        val parent = requireDirectory(parentLocation)
        val target = File(parent, name).canonicalFile
        if (!isInsideWorkspaces(target) || target.parentFile?.canonicalFile != parent) {
            throw IOException("Invalid folder destination")
        }
        if (target.exists()) throw IOException("A file or folder with this name already exists")
        if (!target.mkdir()) throw IOException("The folder could not be created")
        return target.path
    }

    /** Creates only a direct child and never truncates an existing document. */
    fun createFile(parentLocation: String, name: String): String {
        val parent = requireDirectory(parentLocation)
        val target = File(parent, name).canonicalFile
        if (!isInsideWorkspaces(target) || target.parentFile?.canonicalFile != parent) {
            throw IOException("Invalid file destination")
        }
        if (!target.createNewFile()) throw IOException("A file or folder with this name already exists")
        return target.path
    }

    fun readDocument(location: String): WorkspaceDocument {
        val file = requireFile(location)
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

    private fun requireDirectory(path: String): File {
        val directory = File(path).canonicalFile
        if (!isInsideWorkspaces(directory) || !directory.isDirectory || !directory.canRead()) {
            throw IOException("This directory is unavailable")
        }
        return directory
    }

    private fun requireFile(path: String): File {
        val file = File(path).canonicalFile
        if (!isInsideWorkspaces(file) || !file.isFile || !file.canRead()) {
            throw IOException("This file is unavailable")
        }
        return file
    }

    /** Inside a workspace: under the workspaces' folder, never that folder itself. */
    private fun isInsideWorkspaces(file: File): Boolean = paths.workspaceOf(file.path) != null

    private fun readBounded(input: InputStream): ByteArray =
        input.readAtMost(MAX_TEXT_BYTES, "Files larger than ${ByteSize.format(MAX_TEXT_BYTES.toLong())} are not supported yet")

    private fun fingerprint(bytes: ByteArray): String = Sha256.hex(bytes)

    private companion object {
        const val MAX_TEXT_BYTES = 5 * 1024 * 1024
    }
}
