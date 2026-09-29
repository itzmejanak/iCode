package dev.icode.mobile.domain

/**
 * Where a path of a workspace physically lives (docs/workspace-storage-plan.md §6). USER is the
 * user's own folder on shared storage — the one copy of their source, and every path's default;
 * the others are iCode's private storage, invisible execution infrastructure.
 */
enum class StorageClass(val directory: String) {
    USER("user"),

    /** Needs Linux semantics (links, executable bits, case) or heavy I/O; kept. */
    LINUX("linux"),

    /** Regenerable; may be cleared. */
    CACHE("cache"),

    /** Per session; cleared when it ends. */
    TEMP("temp"),
}

/** A relative path of the workspace kept outside the user's folder, in [storage]. */
data class WorkspaceMount(val path: String, val storage: StorageClass)

/** A path of the logical workspace, resolved: where its bytes are. */
data class PhysicalLocation(val storage: StorageClass, val path: String)

/** One `host:guest` binding of the projection, for the runtime. */
data class ProjectionBind(val host: String, val guest: String)

/**
 * One project as the user sees it, over several storage backends: [userRoot] on shared storage,
 * canonical and never copied, and [mounts] in [privateRoot]. Every consumer — the runtime, the
 * Explorer, the document provider — asks this one resolver, so none sees a different tree. No
 * toolchain is known here; profiles only declare mounts.
 */
class LogicalWorkspace(
    val userRoot: String,
    val privateRoot: String,
    mounts: List<WorkspaceMount>,
) {
    /** Checked and ordered: a mount's parent comes before it, so a runtime binds it first. */
    val mounts: List<WorkspaceMount> = mounts.onEach { mount ->
        WorkspaceMounts.problem(mount)?.let { throw IllegalArgumentException("${mount.path}: $it") }
    }.also { list ->
        val duplicate = list.groupBy { it.path }.filterValues { it.size > 1 }.keys.firstOrNull()
        require(duplicate == null) { "$duplicate is mounted twice" }
    }.sortedBy { it.path.count { char -> char == '/' } }

    /** The mount holding [relativePath]: the longest one that is the path itself or contains it. */
    fun mountOf(relativePath: String): WorkspaceMount? =
        mounts.filter { relativePath == it.path || relativePath.startsWith(it.path + "/") }.maxByOrNull { it.path.length }

    /** Where [relativePath] (from the workspace root; "" is the root) physically lives. */
    fun resolve(relativePath: String): PhysicalLocation {
        val path = relativePath.trim('/')
        val mount = mountOf(path) ?: return PhysicalLocation(StorageClass.USER, join(userRoot, path))
        return PhysicalLocation(mount.storage, join(privatePath(mount), path.removePrefix(mount.path).trimStart('/')))
    }

    /** The private folder behind [mount]: `<private root>/<class>/<path>`. */
    fun privatePath(mount: WorkspaceMount): String = join(join(privateRoot, mount.storage.directory), mount.path)

    /** The whole tree at [guestRoot] in the runtime: the user's folder, then each mount over it. */
    fun binds(guestRoot: String): List<ProjectionBind> =
        listOf(ProjectionBind(userRoot, guestRoot)) + mounts.map { ProjectionBind(privatePath(it), join(guestRoot, it.path)) }

    private fun join(base: String, relative: String) = if (relative.isEmpty()) base else "${base.trimEnd('/')}/$relative"
}

object WorkspaceMounts {
    /** Why [mount] cannot be one, or null: a clean relative path, not Git's, and not the user's own class. */
    fun problem(mount: WorkspaceMount): String? {
        val path = mount.path
        val segments = path.split('/')
        return when {
            mount.storage == StorageClass.USER -> "the user's files are the default, not a mount"
            path.isEmpty() -> "the workspace itself cannot be mounted"
            path.startsWith('/') || path.endsWith('/') -> "must be a relative path"
            segments.any { it.isEmpty() || it == "." || it == ".." } -> "must be a plain relative path"
            segments.first() == ".git" -> ".git belongs to Git, in the user's folder"
            else -> null
        }
    }
}
