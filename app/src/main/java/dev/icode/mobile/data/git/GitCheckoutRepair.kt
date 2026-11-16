package dev.icode.mobile.data.git

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * An imported repository's checkout brought back to what Git recorded
 * (docs/workspaces-and-files.md). Git on phone storage recorded `core.fileMode=false` and
 * `core.symlinks=false`, and wrote
 * executables without `+x` and each link as a file holding its target. [SCRIPT] asks Git what it
 * recorded, [Reader] reads the answer as it streams, [restore] puts back `+x` and the links — a
 * link only where the file is as Git wrote it, so nothing the user changed is touched — and
 * [SETTINGS_SCRIPT] then tells Git this filesystem holds both. Both halves of the protocol live here.
 */
internal object GitCheckoutRepair {

    /**
     * Run at the repository's top. NUL-terminated records: the files whose content differs from
     * the index (Git's own diff, blind to modes and links), one empty record, then every index
     * entry as `<mode> <object> <stage>\t<path>`. Git forbids NUL in a path, so no path can move
     * the boundary.
     */
    const val SCRIPT =
        "git -c core.fileMode=false -c core.symlinks=false diff --name-only -z && printf '\\0' && git ls-files --stage -z"

    /**
     * Run once the checkout is restored: from here on Git compares modes and makes links itself.
     * Every value goes — a configuration can hold a key twice, and then a plain set is refused.
     */
    const val SETTINGS_SCRIPT = "git config --replace-all core.fileMode true && git config --replace-all core.symlinks true"

    data class Recorded(val modified: Set<String>, val executables: List<String>, val links: List<String>)

    data class Restored(val executables: Int, val links: Int)

    /** Reads [SCRIPT]'s records as they arrive; only what the repair uses is kept. */
    class Reader {
        private val modified = HashSet<String>()
        private val executables = ArrayList<String>()
        private val links = ArrayList<String>()
        private var inIndex = false

        fun accept(record: ByteArray) {
            val text = String(record, StandardCharsets.UTF_8)
            if (!inIndex) {
                if (text.isEmpty()) inIndex = true else modified += text
                return
            }
            val tab = text.indexOf('\t')
            if (tab < 0) return
            val fields = text.substring(0, tab).split(' ')
            // A conflicted path (stages 1–3) is the user's to settle, as it was.
            if (fields.size != 3 || fields[2] != "0") return
            val path = text.substring(tab + 1)
            when (fields[0]) {
                EXECUTABLE -> executables += path
                LINK -> links += path
            }
        }

        fun recorded() = Recorded(modified, executables, links)
    }

    /**
     * `+x` on each executable Git records; each link Git records made a link again, where its file
     * still holds exactly the target Git wrote. Nothing outside [top] is touched, and a path that
     * leads through a link is left alone.
     */
    fun restore(top: File, recorded: Recorded): Restored {
        val root = top.canonicalFile
        var executables = 0
        var links = 0
        recorded.executables.forEach { path ->
            val file = fileIn(root, path) ?: return@forEach
            if (Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && !file.canExecute() && file.setExecutable(true, false)) {
                executables++
            }
        }
        recorded.links.forEach { path ->
            if (path in recorded.modified) return@forEach
            val file = fileIn(root, path) ?: return@forEach
            if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) || file.length() > MAX_TARGET_BYTES) return@forEach
            val bytes = file.readBytes()
            val target = String(bytes, StandardCharsets.UTF_8)
            // A target that is not plain UTF-8 would not survive the round trip as it was written.
            if (target.isEmpty() || '\u0000' in target || !target.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes)) return@forEach
            // Made beside it under a name nothing has: nothing of the user's is replaced but the file.
            val link = generateSequence(0) { it + 1 }
                .map { File(file.parentFile, ".icode-link-$it") }
                .first { !Files.exists(it.toPath(), LinkOption.NOFOLLOW_LINKS) }
            Files.createSymbolicLink(link.toPath(), Paths.get(target))
            // The link replaces the file in one rename: never neither.
            Files.move(link.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            links++
        }
        return Restored(executables, links)
    }

    /** [path] under [root] with no link on the way to it; null when it leads anywhere else. */
    private fun fileIn(root: File, path: String): File? {
        val parts = path.split('/')
        if (path.startsWith('/') || parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        val file = File(root, path)
        return file.takeIf { it.parentFile?.canonicalFile == it.parentFile }
    }

    private const val EXECUTABLE = "100755"
    private const val LINK = "120000"
    /** Linux's PATH_MAX: a longer file is not a link Git wrote. */
    private const val MAX_TARGET_BYTES = 4096L
}
