package dev.icode.mobile.data.runtime

import android.os.Build
import dev.icode.mobile.domain.RuntimeDetails
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import org.json.JSONObject

/**
 * Reads the facts the Runtime Environment screen shows.
 *
 * All of it comes from the filesystem and the build, so no guest process has to
 * start: opening a settings page should not cost a PRoot launch.
 */
internal class RuntimeInspector(
    private val layout: RuntimeLayout,
    private val prootVersion: String?,
) {
    fun inspect(): RuntimeDetails = RuntimeDetails(
        release = installedRelease(),
        architecture = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        rootfsPath = layout.rootfs.absolutePath,
        installedPackages = countPackages(),
        rootfsBytes = measure(layout.rootfs),
        availableBytes = layout.runtimeRoot.parentFile?.usableSpace ?: 0L,
        prootVersion = prootVersion,
    )

    private fun installedRelease(): String? = runCatching {
        JSONObject(layout.installMarker.readText()).getString("base")
    }.getOrNull()

    /**
     * One `.list` file per installed package is how dpkg records them, so this
     * is a count of what is installed rather than of what was ever downloaded.
     */
    private fun countPackages(): Int? = runCatching {
        File(layout.rootfs, DPKG_INFO_RELATIVE)
            .listFiles { file -> file.name.endsWith(PACKAGE_LIST_SUFFIX) }
            ?.size
    }.getOrNull()

    /**
     * Adds up the tree without following symbolic links.
     *
     * A base system is full of them — `/bin` points into `/usr/bin`, and so on
     * — so following links walks the same files repeatedly and can cycle
     * forever. `walkFileTree` does not follow links unless asked, which is both
     * correct and what makes the count mean "space this uses".
     *
     * Bounded all the same, and returns null rather than a partial figure if
     * the bound is reached: a number that is quietly wrong is worse than none.
     */
    private fun measure(root: File): Long? {
        if (!root.isDirectory) return null
        var total = 0L
        var visited = 0L
        val exceeded = booleanArrayOf(false)
        runCatching {
            Files.walkFileTree(
                root.toPath(),
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                        if (++visited > MAX_ENTRIES_WALKED) {
                            exceeded[0] = true
                            return FileVisitResult.TERMINATE
                        }
                        if (attributes.isRegularFile) total += attributes.size()
                        return FileVisitResult.CONTINUE
                    }

                    /** An unreadable entry is skipped, not a reason to give up. */
                    override fun visitFileFailed(file: Path, error: IOException): FileVisitResult =
                        FileVisitResult.CONTINUE
                },
            )
        }.onFailure { return null }
        return if (exceeded[0]) null else total
    }

    private companion object {
        const val DPKG_INFO_RELATIVE = "var/lib/dpkg/info"
        const val PACKAGE_LIST_SUFFIX = ".list"

        /** A base system holds tens of thousands of files; this is generous. */
        const val MAX_ENTRIES_WALKED = 400_000L
    }
}
