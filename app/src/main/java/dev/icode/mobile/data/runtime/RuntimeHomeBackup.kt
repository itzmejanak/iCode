package dev.icode.mobile.data.runtime

import java.io.File
import java.io.IOException

/** Keeps a repair's home intact across failed extraction, process death and retry. */
internal class RuntimeHomeBackup(rootfs: File, runtimeRoot: File) {
    private val home = File(rootfs, HOME_RELATIVE)
    val backup = File(runtimeRoot, BACKUP_RELATIVE)
    private val repairMarker = File(runtimeRoot, "repair-in-progress")
    val hasPreservedHome: Boolean get() = backup.exists() || repairMarker.isFile

    fun prepareForRepair(): File? {
        if (!repairMarker.isFile) repairMarker.writeText("preserve-home")
        if (backup.exists()) {
            if (!backup.isDirectory) throw IOException("The runtime home backup is not a directory")
            return backup // A retry must never replace the original backup with an extracted default home.
        }
        if (!home.exists()) return null
        if (!home.isDirectory) throw IOException("The runtime home is not a directory")
        if (!home.renameTo(backup)) throw IOException("Unable to preserve the runtime home")
        return backup
    }

    fun completeRepair() {
        if (repairMarker.exists() && !repairMarker.delete()) {
            throw IOException("Unable to finish the runtime repair")
        }
    }

    fun restore(saved: File) {
        if (saved.canonicalFile != backup.canonicalFile || !saved.isDirectory) {
            throw IOException("The preserved runtime home is unavailable")
        }
        deleteRuntimeTree(home)
        if (!saved.renameTo(home)) throw IOException("Unable to restore the preserved runtime home")
    }

    /** Reset explicitly discards an older repair backup, but only after the new runtime verifies. */
    fun discardAfterReset() {
        deleteRuntimeTree(backup)
        if (repairMarker.exists() && !repairMarker.delete()) {
            throw IOException("The previous repair marker could not be removed")
        }
    }

    companion object {
        const val HOME_RELATIVE = "root"
        const val BACKUP_RELATIVE = "home-backup"
    }
}
