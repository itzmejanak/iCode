package dev.icode.mobile.data.runtime

import android.annotation.SuppressLint
import android.os.Build
import dev.icode.mobile.data.io.measureTree
import dev.icode.mobile.domain.RuntimeDetails
import dev.icode.mobile.domain.StorageMeasurement
import java.io.File

/**
 * Reads the facts the Runtime Environment screen shows.
 *
 * All of it comes from the filesystem and the build, so no guest process has to
 * start: opening a settings page should not cost a PRoot launch.
 */
// availableBytes is shown, not decided on: what is free right now, as the Storage screen
// shows it. The install's decision uses RuntimeLayout.allocatableBytes.
@SuppressLint("UsableSpace")
internal class RuntimeInspector(
    private val layout: RuntimeLayout,
    private val prootVersion: String?,
) {
    /** Everything but the size: quick to read, so a screen shows it at once. */
    fun facts(): RuntimeDetails = RuntimeDetails(
        inspected = true,
        release = layout.installedRelease(),
        architecture = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        rootfsPath = layout.rootfs.absolutePath,
        installedPackages = countPackages(),
        availableBytes = layout.runtimeRoot.parentFile?.usableSpace ?: 0L,
        prootVersion = prootVersion,
    )

    /** Seconds on a full runtime; a partial walk has no size rather than a wrong one. */
    fun rootfsSize(): StorageMeasurement = measureTree(layout.rootfs, MAX_ENTRIES_WALKED)

    /**
     * One `.list` file per installed package is how dpkg records them, so this
     * is a count of what is installed rather than of what was ever downloaded.
     */
    private fun countPackages(): Int? = runCatching {
        File(layout.rootfs, DPKG_INFO_RELATIVE)
            .listFiles { file -> file.name.endsWith(PACKAGE_LIST_SUFFIX) }
            ?.size
    }.getOrNull()

    private companion object {
        const val DPKG_INFO_RELATIVE = "var/lib/dpkg/info"
        const val PACKAGE_LIST_SUFFIX = ".list"

        /** A base system holds tens of thousands of files; this is generous. */
        const val MAX_ENTRIES_WALKED = 400_000
    }
}
