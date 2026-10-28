package dev.icode.mobile.data.files

import dev.icode.mobile.data.io.ArchiveLinks
import dev.icode.mobile.data.io.ArchiveWriting
import dev.icode.mobile.data.io.Archives
import dev.icode.mobile.data.io.Unpacking
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.files.ArchiveFormat
import dev.icode.mobile.domain.files.ArchiveFormats
import dev.icode.mobile.domain.files.CompressFormat
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.FileWords
import dev.icode.mobile.domain.files.PasswordAsk
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.apache.commons.compress.PasswordRequiredException

/**
 * Extract and Compress (docs/explorer-gaps-plan.md §3.11), inside their task. An archive is unpacked
 * into a folder staged on its destination's own volume ([FileTrash.staging]) — counted first from
 * its own index where it has one, and room made for it — then put in place as one folder or file:
 * the one folder all its entries sit in, else a folder named after it; a name already there is asked
 * about. In Private a link is made only pointing inside what was unpacked; on phone storage none is,
 * nor a name that differs from another only in case; what was left out is said. Compress writes its
 * archive the same way, then puts it in the folder under a free name. Stopped or failed, nothing
 * staged is left; what was put in place is kept, as made, to undo. Each is checked whole first, as
 * every operation is ([FileRefusals]); a 7z locked by a password waits on it, never kept.
 */
internal class FileArchives(
    private val stateHolder: ICodeStateHolder,
    private val places: FilePlaces,
    private val trash: FileTrash,
    private val transfers: StorageTransfers,
    private val refusals: FileRefusals,
    private val tasks: FileTasks,
    private val history: FileHistory,
) {
    /** [locations], of one folder, into one archive there: named as asked, or after the one item or the folder. */
    suspend fun compress(locations: List<String>, format: CompressFormat, name: String?) {
        val folder = locations.map { File(it).parent }.distinct().singleOrNull() ?: return tasks.said("Compress items of one folder at a time")
        refusals.copies(locations)?.let { return tasks.said(it) }
        val only = locations.singleOrNull()?.let { File(it).name to Files.isDirectory(File(it).toPath(), LinkOption.NOFOLLOW_LINKS) }
        val archive = name?.trim()?.takeIf { it.isNotEmpty() } ?: ArchiveFormats.archiveName(only, File(folder).name.takeUnless { refusals.isPlaceTop(folder) })
        WorkspaceNameRules.fileValidationError(archive + format.extension)?.let { return tasks.said(it) }
        val names = locations.map { File(it).name }
        tasks.run("Compressing…", FileWords.subject(names), locations + folder, "Unable to compress", keep = { steps ->
            val made = (steps.firstOrNull() as? FileStep.Made)?.location?.let { File(it).name } ?: (archive + format.extension)
            history.record(FileWords.compressed(names, made).copy(steps = steps))
        }) { compress(this, locations, folder, format, archive) }
    }

    /** What an archive holds, into the folder asked or its own; a 7z locked by a password waits on it first. */
    suspend fun extract(intent: FilesIntent.Extract) {
        asking(null)
        val into = intent.folder ?: File(intent.location).parent.orEmpty()
        (refusal(intent.location) ?: refusals.extraction(intent.location, into))?.let { return tasks.said(it) }
        val password = intent.password?.toCharArray()
        if (needsPassword(intent.location, password)) return asking(PasswordAsk(intent.location, intent.folder))
        val name = File(intent.location).name
        tasks.run("Extracting…", name, listOf(intent.location, into), "Unable to extract", keep = tasks.kept(FileWords.extracted(name))) {
            extract(this, intent.location, into, password)
        }
    }

    /** The archive Extract waits on the password of, or none. */
    fun asking(password: PasswordAsk?) = stateHolder.update { it.copy(explorer = it.explorer.copy(password = password)) }

    /** Why [archive] cannot be extracted at all, or null. */
    private fun refusal(archive: String): String? {
        val name = File(archive).name
        val format = ArchiveFormats.of(name) ?: return "$name is not an archive iCode opens"
        return format.unreadable
    }

    /** [archive] is a 7z locked by a password, and none was given. */
    private fun needsPassword(archive: String, password: CharArray?): Boolean =
        password == null && ArchiveFormats.of(File(archive).name) == ArchiveFormat.SevenZ && Archives.locked(File(archive))

    private suspend fun extract(batch: FileBatch, archive: String, into: String, password: CharArray?) {
        val source = File(archive)
        val format = ArchiveFormats.of(source.name) ?: throw IOException(refusal(archive))
        val shared = places.storage.contains(into)
        val staged = trash.staging(into)
        try {
            reading(source, password) { Archives.index(source, format, password) }?.let { index ->
                batch.progress.counted(index.entries)
                transfers.roomRefusal(index.bytes)?.let { throw IOException(it) }
            }
            val unpacked = File(staged, UNPACKED)
            val links = if (shared) ArchiveLinks.None else ArchiveLinks.Inside
            val result = reading(source, password) { Archives.unpack(source, format, unpacked, Unpacking(links, shared, password, onEntry = batch.progress::item)) }
            if (result.skipped > 0) batch.notes += "Extracted ${source.name} without ${countOf(result.skipped, "item")} ${if (shared) SHARED_LEFT_OUT else PRIVATE_LEFT_OUT}"
            val held = unpacked.listFiles().orEmpty()
            val made = when {
                format.single || (held.size == 1 && Files.isDirectory(held.single().toPath(), LinkOption.NOFOLLOW_LINKS)) -> held.single()
                else -> File(staged, ArchiveFormats.stem(source.name)).also { Files.move(unpacked.toPath(), it.toPath()) }
            }
            batch.settle(made.path, into, asking = true)
        } finally {
            withContext(NonCancellable) { runCatching { deleteTree(staged) } }
        }
    }

    /** [sources], of one folder, into one archive of [format] named [name] in [into], under a free name. */
    private suspend fun compress(batch: FileBatch, sources: List<String>, into: String, format: CompressFormat, name: String) {
        val staged = trash.staging(into)
        try {
            batch.progress.counted(sources.sumOf { countEntries(File(it).toPath()) })
            val archive = File(staged, name + format.extension)
            val packed = ArchiveWriting.pack(sources.map(::File), archive, format, batch.progress::item)
            if (packed.skippedLinks > 0) batch.notes += "${archive.name} was made without ${countOf(packed.skippedLinks, "link")}: a 7z cannot hold one"
            batch.settle(archive.path, into, asking = false)
        } finally {
            withContext(NonCancellable) { runCatching { deleteTree(staged) } }
        }
    }

    /**
     * [read] of [source], its failures said in the archive's words: a 7z read with the wrong
     * [password] decodes to nonsense, which its own checks refuse.
     */
    private fun <T> reading(source: File, password: CharArray?, read: () -> T): T = try {
        read()
    } catch (locked: PasswordRequiredException) {
        throw IOException("${source.name} needs its password")
    } catch (failed: IOException) {
        throw if (password != null) IOException("The password for ${source.name} is wrong, or the archive is damaged") else failed
    }

    private companion object {
        /** What is unpacked goes here first, so what the archive held is known before it is named. */
        const val UNPACKED = "unpacked"
        const val SHARED_LEFT_OUT = "phone storage cannot hold (links, or names that differ only in case)"
        const val PRIVATE_LEFT_OUT = "that would lead out of it (links)"
    }
}
