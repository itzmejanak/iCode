package dev.icode.mobile.data.runtime

import java.io.File
import java.io.FileOutputStream

/**
 * Each managed process's output as it came — its colours too — in a file of the runtime's `/tmp`,
 * which every terminal sees (docs/runtime-and-terminal.md): what its tab in the terminal
 * follows. Past [limit] a log moves to `<id>.log.1`, the one there before dropped, and a new one
 * starts; `tail -F` follows the name. A log goes when its process leaves the list, and all of them
 * when iCode starts, whose IDs start again at `p1`.
 *
 * A log is a view: one that cannot be written never stops its process.
 */
internal class ProcessLogs(private val directory: File, private val limit: Long = LIMIT) {
    /** Where [id]'s log is, in the runtime. */
    fun guestPath(id: String): String = "$GUEST_DIRECTORY/${fileName(id)}"

    fun append(id: String, text: String) {
        runCatching {
            directory.mkdirs()
            val log = File(directory, fileName(id))
            if (log.length() >= limit) log.renameTo(File(directory, "${fileName(id)}.1"))
            FileOutputStream(log, true).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    fun remove(id: String) {
        File(directory, fileName(id)).delete()
        File(directory, "${fileName(id)}.1").delete()
    }

    /** Every log, from processes an earlier iCode ran. Creates nothing: the runtime may not be installed. */
    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun fileName(id: String): String = "$id.log"

    companion object {
        const val GUEST_DIRECTORY = "/tmp/icode/processes"
        private const val LIMIT = 2L * 1024 * 1024

        fun of(rootfs: File): ProcessLogs = ProcessLogs(File(rootfs, GUEST_DIRECTORY.removePrefix("/")))
    }
}
