package dev.icode.mobile.data.runtime

import java.io.File

/**
 * The packages dpkg has installed, read from its status file on the app's side — no process,
 * no PRoot (docs/runtime-tools-plan.md §3.2). Read again only when the file has changed, so
 * a package installed from the terminal is seen at the next look.
 */
internal class DpkgStatus(rootfs: File) {
    private val file = File(rootfs, STATUS_RELATIVE)
    private var readAt: Pair<Long, Long>? = null
    private var packages: Set<String> = emptySet()

    @Synchronized
    fun installed(): Set<String> {
        val stamp = file.lastModified() to file.length()
        if (stamp != readAt) {
            packages = if (file.isFile) parse(file.readText()) else emptySet()
            readAt = stamp
        }
        return packages
    }

    companion object {
        const val STATUS_RELATIVE = "var/lib/dpkg/status"

        /**
         * Each stanza names its package, then `Status: <wanted> <flag> <state>`. The state says what
         * is on disk: `hold ok installed` and `deinstall ok installed` are there; `deinstall ok
         * config-files` and `install ok half-installed` are not.
         */
        fun parse(status: String): Set<String> = buildSet {
            var name: String? = null
            status.lineSequence().forEach { line ->
                when {
                    line.isEmpty() -> name = null
                    line.startsWith("Package: ") -> name = line.removePrefix("Package: ").trim()
                    line.startsWith("Status: ") && line.removePrefix("Status: ").trim().split(' ').getOrNull(2) == "installed" ->
                        name?.let(::add)
                }
            }
        }
    }
}
