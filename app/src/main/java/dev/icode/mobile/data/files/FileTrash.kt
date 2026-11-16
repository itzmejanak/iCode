package dev.icode.mobile.data.files

import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.domain.isAtOrUnder
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.domain.files.TrashRules
import dev.icode.mobile.domain.files.TrashedItem
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONObject

/**
 * Trash, one per place (docs/workspaces-and-files.md), so a delete is a rename on the item's own
 * volume, never a copy: Private's beside the workspaces, outside what the runtime, the assistant and
 * Android's Files app see; Shared's in `Android/media/<package>/Trash`, which Android removes with
 * iCode, a `.nomedia` keeping trashed pictures out of galleries. Each item is `files/<id>`, the item
 * itself, and `info/<id>.json`, what it was — written whole before the item moves, so nothing is in
 * Trash without one. An item without its record is removed at start; one 30 days old, at start and
 * when Trash is read. Beside them, `staging/` holds what an operation makes before it is put in
 * place, on the volume it goes to; what a stopped one left goes at start too.
 */
internal class FileTrash(
    private val privateTrash: File,
    private val sharedTrash: File,
    private val storage: PhoneStorage,
    private val paths: WorkspacePaths,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** A record and its item change together; removing an item's files happens outside it. */
    private val lock = Any()

    /** [location] is a trash, is in one, or holds one: it is never put in Trash, nor moved. */
    fun holds(location: String): Boolean = isTrash(location) || listOf(privateTrash.path, sharedTrash.path).any { it.isAtOrUnder(location) }

    /** [location] is a trash or in one: Trash's own, which nothing but Trash lists. */
    fun isTrash(location: String): Boolean = listOf(privateTrash.path, sharedTrash.path).any { location.isAtOrUnder(it) }

    /**
     * [location] into its place's trash, measured at [bytes], [workspace] when it is a whole one.
     * Refused, with nothing removed, when Trash is on another volume and the move would be a copy.
     */
    fun put(location: String, bytes: Long?, workspace: Boolean): TrashedItem = synchronized(lock) {
        val source = File(location)
        val place = placeOf(location)
        val trash = trashOf(place)
        prepare(trash)
        val folder = Files.isDirectory(source.toPath(), LinkOption.NOFOLLOW_LINKS)
        val item = UUID.randomUUID().toString().let { id -> TrashedItem(id, source.name, location, paths.runtimePathOf(location), itemOf(trash, id).path, place, clock(), folder, bytes, workspace) }
        write(trash, item)
        try {
            Files.move(source.toPath(), itemOf(trash, item.id).toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (error: Exception) {
            recordOf(trash, item.id).delete()
            if (error is AtomicMoveNotSupportedException) throw IOException("Trash is on another volume: ${source.name} was not deleted")
            throw error
        }
        item
    }

    /** Both places' items, newest first: Shared's only with All Files Access. A record whose item is gone goes too. */
    fun list(): List<TrashedItem> = synchronized(lock) {
        trashes().flatMap { (place, trash) ->
            trash.info.listFiles { file -> file.name.endsWith(RECORD) }.orEmpty().mapNotNull { record ->
                val id = record.name.removeSuffix(RECORD)
                if (!Files.exists(itemOf(trash, id).toPath(), LinkOption.NOFOLLOW_LINKS)) null.also { record.delete() } else read(record, id, place)
            }
        }.sortedByDescending { it.deletedAt }
    }

    /** [id], from either trash; null when it is in neither. */
    fun item(id: String): TrashedItem? = synchronized(lock) {
        trashes().firstNotNullOfOrNull { (place, trash) ->
            recordOf(trash, id).takeIf { it.isFile && Files.exists(itemOf(trash, id).toPath(), LinkOption.NOFOLLOW_LINKS) }?.let { read(it, id, place) }
        }
    }

    /** A new, empty folder on [location]'s own volume, for what an operation makes before it is put in place there. */
    fun staging(location: String): File = synchronized(lock) {
        File(trashOf(placeOf(location)).root, "$STAGING/${UUID.randomUUID()}").also { Files.createDirectories(it.toPath()) }
    }

    /** [item] back at [to], its folder made when gone; refused when [to] is taken. */
    fun restore(item: TrashedItem, to: String) = synchronized(lock) {
        val target = File(to).toPath()
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw IOException("“${target.fileName}” is already there")
        Files.createDirectories(target.parent)
        Files.move(File(item.heldAt).toPath(), target)
        recordOf(trashOf(item.place), item.id).delete()
    }

    /**
     * [item] gone for good, each entry reported to [onEntry] as a path from its name: its record
     * first, so it leaves Trash at once and is never removed twice; then its files.
     */
    fun delete(item: TrashedItem, onEntry: (String) -> Unit = {}) {
        val held = synchronized(lock) { recordOf(trashOf(item.place), item.id).delete() }
        if (!held) return
        val top = itemOf(trashOf(item.place), item.id).toPath()
        deleteTree(top) { entry -> top.relativize(entry).toString().let { onEntry(if (it.isEmpty()) item.name else "${item.name}/$it") } }
    }

    /** Items [TrashRules.KEEP_DAYS] old, gone for good; and [orphans] too, and what was staged — at start, when nothing else works in Trash. */
    fun purge(orphans: Boolean) {
        val now = clock()
        list().filter { TrashRules.expired(it, now) }.forEach { runCatching { delete(it) } }
        if (!orphans) return
        val left = synchronized(lock) {
            trashes().flatMap { (_, trash) ->
                trash.info.listFiles { file -> !file.name.endsWith(RECORD) }.orEmpty().forEach(File::delete)
                trash.files.listFiles().orEmpty().filterNot { recordOf(trash, it.name).isFile } + File(trash.root, STAGING).listFiles().orEmpty()
            }
        }
        left.forEach { runCatching { deleteTree(it) } }
    }

    private class Trash(val root: File, val shared: Boolean) {
        val files = File(root, "files")
        val info = File(root, "info")
    }

    private fun placeOf(location: String) = if (storage.contains(location)) StoragePlace.Shared else StoragePlace.Private

    private fun trashOf(place: StoragePlace) = when (place) {
        StoragePlace.Private -> Trash(privateTrash, shared = false)
        StoragePlace.Shared -> Trash(sharedTrash, shared = true)
    }

    private fun trashes(): List<Pair<StoragePlace, Trash>> =
        listOfNotNull(StoragePlace.Private to trashOf(StoragePlace.Private), (StoragePlace.Shared to trashOf(StoragePlace.Shared)).takeIf { storage.hasAccess() })

    private fun prepare(trash: Trash) {
        if (!trash.files.isDirectory) Files.createDirectories(trash.files.toPath())
        if (!trash.info.isDirectory) Files.createDirectories(trash.info.toPath())
        File(trash.root, ".nomedia").takeIf { trash.shared && !it.exists() }?.createNewFile()
    }

    private fun itemOf(trash: Trash, id: String) = File(trash.files, id)

    private fun recordOf(trash: Trash, id: String) = File(trash.info, id + RECORD)

    /** The record, whole or not at all: written beside, then renamed into place. */
    private fun write(trash: Trash, item: TrashedItem) {
        val record = JSONObject()
            .put("name", item.name)
            .put("from", item.from)
            .put("deletedAt", item.deletedAt)
            .put("folder", item.folder)
            .put("workspace", item.workspace)
        item.bytes?.let { record.put("bytes", it) }
        val part = File(trash.info, item.id + PART)
        part.writeText(record.toString())
        Files.move(part.toPath(), recordOf(trash, item.id).toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun read(record: File, id: String, place: StoragePlace): TrashedItem? = runCatching {
        val json = JSONObject(record.readText())
        val from = json.getString("from")
        TrashedItem(
            id = id,
            name = json.getString("name"),
            from = from,
            path = paths.runtimePathOf(from),
            heldAt = itemOf(trashOf(place), id).path,
            place = place,
            deletedAt = json.getLong("deletedAt"),
            folder = json.getBoolean("folder"),
            bytes = if (json.has("bytes")) json.getLong("bytes") else null,
            workspace = json.getBoolean("workspace"),
        )
    }.getOrNull()

    private companion object {
        const val RECORD = ".json"
        const val PART = ".part"
        const val STAGING = "staging"
    }
}
