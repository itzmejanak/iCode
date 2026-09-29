package dev.icode.mobile.data.storage

import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.LogicalWorkspace
import dev.icode.mobile.domain.StorageClass
import dev.icode.mobile.domain.WorkspaceMount
import dev.icode.mobile.domain.WorkspaceMounts
import java.io.File

/**
 * Each workspace's private storage (docs/workspace-storage-plan.md §6): its root,
 * `files/workspaces/<SHA-256 of the user's folder>` — the key its settings already use — and the
 * list of its mounts kept there, one `CLASS path` a line. The runtime, and later the Explorer and
 * the document provider, all read a workspace through [of], so none sees a different tree.
 */
internal class WorkspaceStorage(filesDir: File) {
    private val root = File(filesDir, "workspaces")

    fun privateRoot(userRoot: String): File = File(root, Sha256.hex(userRoot))

    /**
     * The logical workspace of [userRoot], with each mount's private folder made ready — a runtime
     * binds only folders that exist. A line that is not a valid mount is skipped and logged: one bad
     * line must not keep the workspace's terminal from starting.
     */
    fun of(userRoot: String): LogicalWorkspace {
        val privateRoot = privateRoot(userRoot)
        val mounts = File(privateRoot, MOUNTS).takeIf { it.isFile }?.readLines().orEmpty().mapNotNull(::parse)
            .distinctBy { it.path }
        val workspace = LogicalWorkspace(userRoot, privateRoot.path, mounts)
        workspace.mounts.forEach { File(workspace.privatePath(it)).mkdirs() }
        return workspace
    }

    /** Replaces [userRoot]'s mounts. */
    fun setMounts(userRoot: String, mounts: List<WorkspaceMount>) {
        LogicalWorkspace(userRoot, privateRoot(userRoot).path, mounts)
        val file = File(privateRoot(userRoot), MOUNTS)
        file.parentFile?.mkdirs()
        file.writeText(mounts.joinToString("") { "${it.storage.name} ${it.path}\n" })
    }

    private fun parse(line: String): WorkspaceMount? {
        if (line.isBlank()) return null
        val storage = StorageClass.entries.firstOrNull { it.name == line.substringBefore(' ') }
        val mount = storage?.let { WorkspaceMount(line.substringAfter(' ').trim(), it) }
        val problem = if (mount == null) "unknown storage class" else WorkspaceMounts.problem(mount)
        if (problem != null) {
            BackendLog.w("storage", "Skipped workspace mount \"$line\": $problem")
            return null
        }
        return mount
    }

    private companion object {
        const val MOUNTS = "mounts"
    }
}
