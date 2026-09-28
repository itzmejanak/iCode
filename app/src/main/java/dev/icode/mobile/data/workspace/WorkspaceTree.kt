package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitWorkspaceSnapshot
import dev.icode.mobile.domain.FileEntry
import dev.icode.mobile.domain.GitTreeStatus
import java.io.File

/**
 * The Explorer's tree of the open workspace (S3): folders read so far, which
 * are expanded, and the rows that makes — each marked with its Git status.
 */
internal class WorkspaceTree(
    private val workbench: WorkbenchStateHolder,
    private val source: DirectFileWorkspaceSource,
) {
    private val childrenByParent = mutableMapOf<String, List<DocumentNode>>()
    private val expanded = mutableSetOf<String>()

    /** A newly opened workspace: its top level only, nothing expanded. */
    fun reset(root: String, children: List<DocumentNode>) {
        childrenByParent.clear()
        childrenByParent[root] = children
        expanded.clear()
    }

    /** The visible rows, Git status included. */
    fun rows(root: String, gitState: GitWorkspaceSnapshot): List<FileEntry> =
        decorate(projectVisibleFiles(root, childrenByParent, expanded), gitState)

    suspend fun toggle(root: String, location: String) {
        if (!expanded.remove(location)) {
            runCatching {
                if (location !in childrenByParent) childrenByParent[location] = source.listChildren(location)
                expanded += location
            }.onFailure { error ->
                workbench.report("workspace", "Unable to read folder", error)
            }
        }
        workbench.update { snapshot -> snapshot.copy(
            files = rows(root, GitWorkspaceSnapshot(workbench.current.gitRepository, workbench.current.gitChanges)),
        ) }
    }

    /** Reads again the top level and every expanded folder. Throws when one cannot be read. */
    fun reload(root: String) {
        childrenByParent[root] = source.listChildren(root)
        expanded.toList().forEach { location ->
            childrenByParent[location] = source.listChildren(location)
        }
    }

    /** Reads [parent] again after something was created in it, and shows it expanded. */
    fun reloadFolder(root: String, parent: String) {
        childrenByParent[parent] = source.listChildren(parent)
        if (parent != root) expanded += parent
    }

    private fun decorate(files: List<FileEntry>, gitState: GitWorkspaceSnapshot) =
        gitState.repository?.let { repository ->
            val statuses = gitState.changes.associate { change ->
                File(repository.workTreePath, change.path).canonicalPath to change.status
            }
            GitTreeStatus.decorate(files, statuses)
        } ?: files
}
