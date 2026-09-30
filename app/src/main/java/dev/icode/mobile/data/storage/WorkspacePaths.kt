package dev.icode.mobile.data.storage

import android.content.Context
import dev.icode.mobile.domain.WorkspaceRuntimePath
import java.io.File

/**
 * How a workspace is reached (docs/workspace-storage-plan.md §4.2). Every workspace is a folder in
 * [root], `files/workspaces`, and every runtime launch binds that folder whole at [GUEST_ROOT]. A
 * location — the host path every store keys by — and a guest path are one path under two roots;
 * this is the only code that exchanges them.
 */
internal class WorkspacePaths(val root: File) {
    private val rootPath = root.path.trimEnd('/')

    /** [location]'s path in the runtime, or null when it is not in the workspaces. */
    fun guestOf(location: String): String? = relative(location, rootPath)?.let { join(GUEST_ROOT, it) }

    /** The location of [guestPath], or null when it is not under [GUEST_ROOT]. */
    fun locationOf(guestPath: String): String? = relative(guestPath, GUEST_ROOT)?.let { join(rootPath, it) }

    /** The workspace [location] lies in: the folder right under the root; null for the root itself or outside it. */
    fun workspaceOf(location: String): String? =
        relative(location, rootPath)?.takeIf { it.isNotEmpty() }?.let { join(rootPath, it.substringBefore('/')) }

    /** [path] from [base] ("" for [base] itself), or null when it is not at or under [base]. */
    private fun relative(path: String, base: String): String? {
        val trimmed = path.trimEnd('/')
        return when {
            trimmed == base -> ""
            trimmed.startsWith("$base/") -> trimmed.substring(base.length + 1)
            else -> null
        }
    }

    private fun join(base: String, relative: String) = if (relative.isEmpty()) base else "$base/$relative"

    companion object {
        /** Where the workspaces are in the runtime. */
        const val GUEST_ROOT = WorkspaceRuntimePath.ROOT

        /**
         * The app's own: `files/workspaces`. Canonical, as every location is — Android's `files`
         * path may lead through a link (`/data/user/0` → `/data/data`).
         */
        fun of(context: Context) = WorkspacePaths(File(context.filesDir, "workspaces").canonicalFile)
    }
}
