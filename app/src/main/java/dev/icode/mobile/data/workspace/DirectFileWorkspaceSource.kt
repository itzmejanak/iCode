package dev.icode.mobile.data.workspace

import android.os.Environment
import android.os.StatFs
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.io.readAtMost
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.FolderBrowserEntry
import dev.icode.mobile.domain.FolderBrowserPurpose
import dev.icode.mobile.domain.FolderBrowserState
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.StorageSummary
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class WorkspaceRoot(
    val name: String,
    val location: String,
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

internal class DirectFileWorkspaceSource {
    private val storageRoot: File
        get() = Environment.getExternalStorageDirectory().canonicalFile

    fun initialBrowser(purpose: FolderBrowserPurpose): FolderBrowserState =
        browserState(storageRoot.path, purpose)

    fun storageSummary(): StorageSummary {
        val stats = StatFs(storageRoot.path)
        return StorageSummary(
            availableBytes = stats.availableBytes,
            totalBytes = stats.totalBytes,
        )
    }

    fun browse(path: String, purpose: FolderBrowserPurpose): FolderBrowserState =
        browserState(path, purpose)

    fun parent(path: String, purpose: FolderBrowserPurpose): FolderBrowserState {
        val current = requireDirectory(path)
        val parent = current.parentFile?.canonicalFile
        val destination = if (parent != null && isInsideStorage(parent)) parent else storageRoot
        return browserState(destination.path, purpose)
    }

    fun describeRoot(location: String): WorkspaceRoot {
        val directory = requireDirectory(location)
        return WorkspaceRoot(
            name = directory.name.ifBlank { "Internal storage" },
            location = directory.path,
            displayPath = displayPath(directory),
        )
    }

    fun listChildren(parentLocation: String): List<DocumentNode> {
        val parent = requireDirectory(parentLocation)
        return parent.listFiles()?.asSequence()
            ?.filterNot { it.name == ".git" }
            ?.mapNotNull { child ->
            runCatching {
                val canonical = child.canonicalFile
                if (!isInsideStorage(canonical)) return@runCatching null
                DocumentNode(
                    name = child.name,
                    location = canonical.path,
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
        if (!isInsideStorage(target) || target.parentFile?.canonicalFile != parent) {
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
        if (!isInsideStorage(target) || target.parentFile?.canonicalFile != parent) {
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
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
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

    fun isAvailable(location: String): Boolean = runCatching {
        val file = File(location).canonicalFile
        isInsideStorage(file) && file.isDirectory && file.canRead()
    }.getOrDefault(false)

    private fun browserState(path: String, purpose: FolderBrowserPurpose): FolderBrowserState {
        val directory = requireDirectory(path)
        val folders = directory.listFiles()?.asSequence()
            ?.filter { it.isDirectory && !it.isHidden && !it.name.startsWith('.') }
            ?.mapNotNull { folder ->
                runCatching {
                    val canonical = folder.canonicalFile
                    if (!isInsideStorage(canonical)) return@runCatching null
                    val children = folder.listFiles()
                    FolderBrowserEntry(
                        name = folder.name,
                        path = canonical.path,
                        directoryCount = children?.count { it.isDirectory } ?: 0,
                        fileCount = children?.count { it.isFile } ?: 0,
                    )
                }.getOrNull()
            }
            ?.filterNotNull()
            ?.sortedBy { it.name.lowercase() }
            ?.toList()
            .orEmpty()
        return FolderBrowserState(
            purpose = purpose,
            rootPath = storageRoot.path,
            currentPath = directory.path,
            displayPath = displayPath(directory),
            folders = folders,
        )
    }

    private fun requireDirectory(path: String): File {
        val directory = File(path).canonicalFile
        if (!isInsideStorage(directory) || !directory.isDirectory || !directory.canRead()) {
            throw IOException("This directory is unavailable")
        }
        return directory
    }

    private fun requireFile(path: String): File {
        val file = File(path).canonicalFile
        if (!isInsideStorage(file) || !file.isFile || !file.canRead()) {
            throw IOException("This file is unavailable")
        }
        return file
    }

    private fun isInsideStorage(file: File): Boolean =
        file.toPath().startsWith(storageRoot.toPath())

    private fun displayPath(file: File): String {
        val relative = storageRoot.toPath().relativize(file.toPath()).toString()
        return if (relative.isBlank()) "Internal storage" else "Internal storage/$relative"
    }

    private fun readBounded(input: InputStream): ByteArray =
        input.readAtMost(MAX_TEXT_BYTES, "Files larger than 5 MB are not supported yet")

    private fun fingerprint(bytes: ByteArray): String = Sha256.hex(bytes)

    private companion object {
        const val MAX_TEXT_BYTES = 5 * 1024 * 1024
    }
}
