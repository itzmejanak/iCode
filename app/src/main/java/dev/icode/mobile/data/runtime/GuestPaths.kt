package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The runtime's paths, followed as PRoot follows them (docs/runtime-contract-plan.md §4.2): which file
 * on the phone a path in the runtime is, and back. Every launch sees the same tree
 * ([RuntimeLayout.prootCommand]): the workspaces at `/workspace`, the phone's own [HOST_PATHS] and
 * phone storage ([shared]) where they are, the root filesystem everywhere else.
 *
 * A link made in the runtime names a runtime path, and is followed inside the runtime. One PRoot made
 * for a hard link (`--link2symlink`) names the phone's path of its file, and is followed as it is.
 */
internal class GuestPaths(
    rootfs: File,
    val workspaces: WorkspacePaths,
    /**
     * Phone storage's top, `/storage/emulated/0`, bound where it is (docs/phone-agent-plan.md L8), with
     * `/sdcard` naming it as on Android; null where there is none.
     */
    val shared: File? = null,
) {
    private val rootfs = rootfs.absoluteFile

    /** The phone's trees bound where they are: [HOST_PATHS], and phone storage. */
    private val bound = HOST_PATHS + listOfNotNull(shared?.path)

    /** The root filesystem as PRoot is given it, and as it is behind Android's links: a stand-in may name either. */
    private val rootfsPaths = listOf(this.rootfs.path, this.rootfs.canonicalPath).distinct()

    /** [location], a file on the phone, as its path in the runtime; null where the runtime does not hold it. */
    fun guestOf(location: String): String? {
        workspaces.guestOf(location)?.let { return it }
        bound.firstOrNull { WorkspacePaths.relative(location, it) != null }?.let { return location.trimEnd('/') }
        return rootfsPaths.firstNotNullOfOrNull { WorkspacePaths.relative(location, it) }?.let { "/$it" }
    }

    /**
     * The file on the phone [guestPath] names, every link on the way followed as in the runtime, the
     * last one included; it need not exist. Throws past [MAX_LINKS] links, as Linux does (`ELOOP`).
     */
    fun hostOf(guestPath: String): File {
        require(guestPath.startsWith('/')) { "$guestPath is not a path in the runtime" }
        val pending = ArrayDeque(segments(guestPath))
        val resolved = mutableListOf<String>()
        var links = 0
        while (pending.isNotEmpty()) {
            when (val name = pending.removeFirst()) {
                ".." -> resolved.removeLastOrNull()
                else -> {
                    val file = mounted(resolved + name)
                    if (!Files.isSymbolicLink(file.toPath())) {
                        resolved += name
                        continue
                    }
                    if (++links > MAX_LINKS) throw IOException("$guestPath: too many levels of symbolic links")
                    val target = Files.readSymbolicLink(file.toPath()).toString()
                    if (target.startsWith('/')) {
                        resolved.clear()
                        segments(guestOf(target) ?: target).asReversed().forEach(pending::addFirst)
                    } else {
                        segments(target).asReversed().forEach(pending::addFirst)
                    }
                }
            }
        }
        return mounted(resolved)
    }

    /** [location] with its links followed as in the runtime; a file the runtime does not hold, as the phone follows them. */
    fun follow(location: String): File = guestOf(location)?.let(::hostOf) ?: File(location).canonicalFile

    /** [location]'s path from [root], both followed ([follow]); null when it is not at or under [root], or cannot be followed. */
    fun within(root: String, location: String): Path? = runCatching {
        val top = follow(root).toPath()
        follow(location).toPath().takeIf { it.startsWith(top) }?.let(top::relativize)
    }.getOrNull()

    /** [file], on the phone, is phone storage or in it. */
    fun inShared(file: File): Boolean = shared != null && WorkspacePaths.relative(file.path, shared.path) != null

    /** The file on the phone at [segments] of a runtime path, no link followed. */
    private fun mounted(segments: List<String>): File {
        val path = "/" + segments.joinToString("/")
        workspaces.locationOf(path)?.let { return File(it) }
        if (bound.any { WorkspacePaths.relative(path, it) != null }) return File(path)
        return if (segments.isEmpty()) rootfs else File(rootfs, path)
    }

    private fun segments(path: String): List<String> = path.split('/').filter { it.isNotEmpty() && it != "." }

    companion object {
        /** `/sdcard`, as Android names phone storage: a link in the root filesystem to [shared]. */
        const val SDCARD = "/sdcard"

        /** The phone's own trees every launch binds where they are: the kernel's, and those the bionic linker reads. */
        val HOST_PATHS = listOf("/dev", "/proc", "/sys", "/system", "/apex", "/vendor", "/product")

        /** Links followed before a path is a loop: Linux's own limit. */
        private const val MAX_LINKS = 40
    }
}
