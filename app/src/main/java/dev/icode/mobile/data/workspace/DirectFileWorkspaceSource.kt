package dev.icode.mobile.data.workspace

import android.os.Environment
import android.os.StatFs
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.io.readAtMost
import dev.icode.mobile.data.storage.LogicalEntry
import dev.icode.mobile.data.storage.LogicalFiles
import dev.icode.mobile.data.storage.WorkspaceStorage
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.FolderBrowserEntry
import dev.icode.mobile.domain.FolderBrowserPurpose
import dev.icode.mobile.domain.FolderBrowserState
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.TextEncoding
import dev.icode.mobile.domain.ByteSize
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

/**
 * The phone's storage as the folder picker sees it, and the open workspace as the Explorer and
 * editor see it: one logical tree (docs/workspace-storage-plan.md §8). A location in the workspace
 * is named by its path under the user's folder and read, written or created where its bytes are —
 * the user's folder, or a mount's private folder.
 */
internal class DirectFileWorkspaceSource(
    private val workspaces: WorkspaceStorage,
    /** The open workspace: a location in it is read in its logical tree. */
    private val openRoot: () -> String?,
) {
    private val storageRoot: File
        get() = Environment.getExternalStorageDirectory().canonicalFile

    /** [root]'s logical tree: its folder and its mounts. */
    fun workspace(root: String): LogicalFiles = LogicalFiles(workspaces.of(root))

    /** Where [location]'s bytes are, in [root]'s tree when it holds it — the open workspace unless told. */
    fun physical(location: String, root: String? = openRoot()): File =
        root?.takeIf { location.isAtOrUnder(it) }?.let { workspace(it).bytes(location) } ?: File(location)

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
        val current = requireDirectory(path, root = null).logical
        val parent = current.parentFile?.canonicalFile
        val destination = if (parent != null && isInsideStorage(parent)) parent else storageRoot
        return browserState(destination.path, purpose)
    }

    fun describeRoot(location: String): WorkspaceRoot {
        val directory = requireDirectory(location, root = null).logical
        return WorkspaceRoot(
            name = directory.name.ifBlank { "Internal storage" },
            location = directory.path,
            displayPath = displayPath(directory),
        )
    }

    /** [parentLocation]'s entries in [root]'s logical tree: the open workspace's, unless one is opening. */
    fun listChildren(parentLocation: String, root: String? = openRoot()): List<DocumentNode> {
        val parent = requireDirectory(parentLocation, root)
        val entries = parent.tree?.list(parent.logical.path)
            ?: parent.bytes.list().orEmpty().map { LogicalEntry(it, File(parent.logical, it).path, File(parent.bytes, it)) }
        return entries.filterNot { it.name == ".git" }.mapNotNull { entry ->
            runCatching {
                // A link in private storage is a tool's own (node_modules/.bin), named where it is;
                // anywhere else, one leading out of storage is not shown.
                val location = if (parent.tree?.isPrivate(entry.location) == true) entry.location
                else entry.bytes.canonicalFile.takeIf(::isInsideStorage)?.path ?: return@runCatching null
                DocumentNode(entry.name, location, if (entry.bytes.isDirectory) FileKind.Folder else FileKind.File)
            }.getOrNull()
        }.sortedWith(
            compareBy<DocumentNode> { it.kind != FileKind.Folder }.thenBy { it.name.lowercase() },
        )
    }

    /** [root] null for the folder picker, which makes folders in the phone's storage as it is. */
    fun createDirectory(parentLocation: String, name: String, root: String? = openRoot()): String {
        val target = newChild(parentLocation, name, root, "Invalid folder destination")
        if (target.bytes.exists()) throw IOException("A file or folder with this name already exists")
        if (!target.bytes.mkdir()) throw IOException("The folder could not be created")
        return target.logical.path
    }

    /** Creates only a direct child and never truncates an existing document. */
    fun createFile(parentLocation: String, name: String): String {
        val target = newChild(parentLocation, name, openRoot(), "Invalid file destination")
        if (!target.bytes.createNewFile()) throw IOException("A file or folder with this name already exists")
        return target.logical.path
    }

    fun readDocument(location: String): WorkspaceDocument {
        val file = requireFile(location)
        val bytes = file.bytes.inputStream().use(::readBounded)
        val decoded = DocumentCodec.decode(bytes)
        return WorkspaceDocument(
            name = file.logical.name,
            location = file.logical.path,
            text = decoded.text,
            encoding = decoded.encoding,
            lineEnding = decoded.lineEnding,
            hasByteOrderMark = decoded.hasByteOrderMark,
            readOnly = !file.bytes.canWrite(),
            fingerprint = fingerprint(bytes),
        )
    }

    fun writeDocument(document: WorkspaceDocument, text: String): WorkspaceDocument {
        if (document.readOnly) throw IOException("This document is read-only")
        val target = requireFile(document.location).bytes
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
        val directory = requireDirectory(path, root = null).logical
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

    /** A location as the workspace names it — canonical, in storage — where its bytes are, and its tree. */
    private class Located(val logical: File, val bytes: File, val tree: LogicalFiles?)

    /** Null outside storage. The user's side holds no links, so its canonical path is the logical one. */
    private fun locate(path: String, root: String?): Located? {
        val logical = File(path).canonicalFile
        if (!isInsideStorage(logical)) return null
        val tree = root?.takeIf { logical.path.isAtOrUnder(it) }?.let(::workspace)
        return Located(logical, tree?.bytes(logical.path) ?: logical, tree)
    }

    private fun requireDirectory(path: String, root: String? = openRoot()): Located {
        val directory = locate(path, root)
        if (directory == null || !directory.bytes.isDirectory || !directory.bytes.canRead()) {
            throw IOException("This directory is unavailable")
        }
        return directory
    }

    private fun requireFile(path: String): Located {
        val file = locate(path, openRoot())
        if (file == null || !file.bytes.isFile || !file.bytes.canRead()) {
            throw IOException("This file is unavailable")
        }
        return file
    }

    /** [name] directly in [parentLocation], checked to be exactly there; whether it exists is the caller's. */
    private fun newChild(parentLocation: String, name: String, root: String?, invalid: String): Located {
        val parent = requireDirectory(parentLocation, root)
        val target = locate(File(parent.logical, name).path, root)
        if (target == null || target.logical.parentFile?.canonicalFile != parent.logical) throw IOException(invalid)
        return target
    }

    private fun isInsideStorage(file: File): Boolean =
        file.toPath().startsWith(storageRoot.toPath())

    private fun displayPath(file: File): String {
        val relative = storageRoot.toPath().relativize(file.toPath()).toString()
        return if (relative.isBlank()) "Internal storage" else "Internal storage/$relative"
    }

    private fun readBounded(input: InputStream): ByteArray =
        input.readAtMost(MAX_TEXT_BYTES, "Files larger than ${ByteSize.format(MAX_TEXT_BYTES.toLong())} are not supported yet")

    private fun fingerprint(bytes: ByteArray): String = Sha256.hex(bytes)

    private companion object {
        const val MAX_TEXT_BYTES = 5 * 1024 * 1024
    }
}
