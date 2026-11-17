package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.io.DigestAlgorithm
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.io.toHex
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.Checkpoint
import dev.icode.mobile.domain.CheckpointFile
import dev.icode.mobile.domain.CheckpointRules
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * Each place's checkpoints (docs/assistant-and-mcp.md): its files before each message, and as each
 * turn left them, so Undo can put them back. A folder per place in iCode's storage — named by the
 * SHA-256 of its workspace's location, or `home` for the chats in none — holds each file's content
 * once, by its SHA-256, and a list per checkpoint. A file unchanged since the place's last checkpoint
 * is not read again; one changed in the last moments always is.
 */
internal class AssistantCheckpoints(private val folder: File) {
    /** A place's work is one at a time: two chats' checkpoints of the same files never cross. */
    private val locks = HashMap<String, Any>()

    /** Each place's latest checkpoint, as this process took or read it. */
    private val latest = ConcurrentHashMap<String, Checkpoint>()

    /** [place]'s files now, kept: the checkpoint's ID, or null when they could not be read. */
    fun take(place: AssistantWorkspace): String? = UUID.randomUUID().toString().takeIf { keep(place, it) }

    /** The turn after [id] ended: the files as it left them, kept with it. */
    fun ended(place: AssistantWorkspace, id: String) {
        keep(place, id + AFTER)
    }

    /**
     * What changed outside a chat's own work since the first of [ids] — its messages' checkpoints from
     * the one undone on, in order — as [CheckpointRules.outside] says it.
     */
    fun outside(place: AssistantWorkspace, ids: List<String>): Set<String> = locked(place.place) { key ->
        val spans = ids.zipWithNext { done, next -> read(key, done + AFTER) to read(key, next) }.mapNotNull { (end, next) -> next?.let { end to it } }
        CheckpointRules.outside(spans + (read(key, ids.last() + AFTER) to scan(place, key, keep = false)))
    }

    /** [place]'s files back as checkpoint [id] kept them. The paths that could not be put back; null when [id] is gone. */
    fun restore(place: AssistantWorkspace, id: String): List<String>? = locked(place.place) { key ->
        val target = read(key, id) ?: return@locked null
        val now = scan(place, key, keep = false)
        val root = place.root
        val failed = mutableListOf<String>()
        // What came since goes first, so a file can come back where a folder of new ones was.
        (now.files.keys - target.files.keys).forEach { path ->
            val file = File(root, path)
            if (!file.delete()) failed += path
            generateSequence(file.parentFile) { it.parentFile }.takeWhile { it != root }.forEach { it.delete() }
        }
        target.files.forEach { (path, kept) ->
            val current = now.files[path]
            if (current?.hash == kept.hash && current.executable == kept.executable) return@forEach
            runCatching { put(File(blobs(key), kept.hash), File(root, path), kept.executable) }.onFailure { failed += path }
        }
        failed
    }

    /** [ids] are no message's any more: forgotten, with the contents only they kept. */
    fun forget(place: String?, ids: Collection<String>) = locked(place) { key ->
        ids.forEach { id ->
            listFile(key, id).delete()
            listFile(key, id + AFTER).delete()
        }
        val kept = folderOf(key).listFiles { file -> file.name.endsWith(LIST) }.orEmpty()
            .flatMapTo(HashSet()) { file -> runCatching { decode(file) }.getOrNull()?.files?.values?.map { it.hash }.orEmpty() }
        blobs(key).listFiles().orEmpty().filter { it.name !in kept }.forEach { it.delete() }
    }

    /** The workspace was renamed or moved: its checkpoints go with it. */
    fun moved(from: String, to: String) = locked(from) { key ->
        latest.remove(key)
        latest.remove(keyOf(to))
        val source = folderOf(key)
        if (source.isDirectory) Files.move(source.toPath(), folderOf(keyOf(to)).apply { deleteRecursively() }.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    /** The workspace was deleted: its checkpoints go with it. */
    fun forgotten(workspace: String) = locked(workspace) { key ->
        latest.remove(key)
        folderOf(key).deleteRecursively()
    }

    private fun keep(place: AssistantWorkspace, name: String): Boolean = runCatching {
        locked(place.place) { key ->
            val checkpoint = scan(place, key, keep = true)
            write(listFile(key, name), checkpoint)
            latest[key] = checkpoint
        }
    }.onFailure { BackendLog.w(TAG, "Unable to keep a checkpoint (${it.javaClass.simpleName})") }.isSuccess

    /**
     * [place]'s files now, each kept by its content when [keep]. A folder [CheckpointRules.skips], a file
     * larger than [CheckpointRules.MAX_FILE], a link, or what cannot be read, is left out.
     */
    private fun scan(place: AssistantWorkspace, key: String, keep: Boolean): Checkpoint {
        val taken = System.currentTimeMillis()
        val before = latest[key] ?: newest(key)
        val blobs = blobs(key).apply { if (keep) mkdirs() }
        val root = place.root.toPath()
        val home = place.workspace == null
        val files = sortedMapOf<String, CheckpointFile>()
        if (!Files.isDirectory(root)) return Checkpoint(taken, files)
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if (dir != root && CheckpointRules.skips(dir.fileName.toString(), home)) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!attrs.isRegularFile || attrs.size() > CheckpointRules.MAX_FILE) return FileVisitResult.CONTINUE
                    val path = root.relativize(file).joinToString("/")
                    val modified = attrs.lastModifiedTime().toMillis()
                    // Known by its size and time only when it last changed well before that checkpoint, and its content is still kept.
                    val known = before?.files?.get(path)?.takeIf {
                        it.size == attrs.size() && it.modified == modified && modified < before.taken - RECENT && (!keep || File(blobs, it.hash).isFile)
                    }
                    val hash = known?.hash ?: runCatching { hash(file.toFile(), blobs.takeIf { keep }) }.getOrNull() ?: return FileVisitResult.CONTINUE
                    files[path] = CheckpointFile(hash, attrs.size(), modified, Files.isExecutable(file))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            },
        )
        return Checkpoint(taken, files)
    }

    /** [file]'s SHA-256; with [blobs], its content kept there under it, once. */
    private fun hash(file: File, blobs: File?): String {
        val digest = DigestAlgorithm.Sha256.newDigest()
        val copy = blobs?.let { File.createTempFile(".blob-", ".tmp", it) }
        try {
            file.inputStream().use { input ->
                copy?.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val read = input.read(buffer).takeIf { it >= 0 } ?: break
                        digest.update(buffer, 0, read)
                        output?.write(buffer, 0, read)
                    }
                }
            }
            val hash = digest.digest().toHex()
            if (copy != null) {
                val blob = File(blobs, hash)
                if (blob.isFile) copy.delete() else Files.move(copy.toPath(), blob.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            return hash
        } finally {
            copy?.takeIf { it.exists() }?.delete()
        }
    }

    /** [blob]'s content at [target], replacing what is there in one step, running when [executable]. */
    private fun put(blob: File, target: File, executable: Boolean) {
        if (target.isDirectory || !blob.isFile) throw IOException("Cannot put back ${target.name}")
        target.parentFile?.mkdirs()
        val temporary = File.createTempFile(".undo-", ".tmp", target.parentFile)
        try {
            blob.copyTo(temporary, overwrite = true)
            temporary.setExecutable(executable)
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temporary.delete()
        }
    }

    /** The place's newest checkpoint on disk: what a file unchanged since need not be read for. */
    private fun newest(key: String): Checkpoint? =
        folderOf(key).listFiles { file -> file.name.endsWith(LIST) }?.maxByOrNull { it.lastModified() }?.let { runCatching { decode(it) }.getOrNull() }

    private fun read(key: String, name: String): Checkpoint? = listFile(key, name).takeIf { it.isFile }?.let { runCatching { decode(it) }.getOrNull() }

    private fun write(file: File, checkpoint: Checkpoint) {
        val files = JSONObject()
        checkpoint.files.forEach { (path, kept) ->
            files.put(path, JSONObject().put("hash", kept.hash).put("size", kept.size).put("modified", kept.modified).put("executable", kept.executable))
        }
        val temporary = File.createTempFile(".list-", ".tmp", file.parentFile)
        temporary.writeText(JSONObject().put("taken", checkpoint.taken).put("files", files).toString())
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun decode(file: File): Checkpoint {
        val json = JSONObject(file.readText())
        val files = json.getJSONObject("files")
        return Checkpoint(
            json.getLong("taken"),
            files.keys().asSequence().associateWith { path ->
                files.getJSONObject(path).let { CheckpointFile(it.getString("hash"), it.getLong("size"), it.getLong("modified"), it.getBoolean("executable")) }
            },
        )
    }

    private fun <T> locked(place: String?, block: (key: String) -> T): T {
        val key = keyOf(place)
        return synchronized(synchronized(locks) { locks.getOrPut(key) { Any() } }) { block(key) }
    }

    private fun keyOf(place: String?): String = place?.let(Sha256::hex) ?: HOME

    private fun folderOf(key: String) = File(folder, key)

    private fun blobs(key: String) = File(folderOf(key), BLOBS)

    private fun listFile(key: String, name: String) = File(folderOf(key), name + LIST)

    private companion object {
        const val TAG = "AssistantCheckpoints"
        const val HOME = "home"
        const val BLOBS = "blobs"
        const val LIST = ".json"
        const val AFTER = ".after"
        const val BUFFER = 64 * 1024

        /** A file changed this close to a checkpoint may have changed again within the same instant: it is read again. */
        const val RECENT = 2_000L
    }
}
