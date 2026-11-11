package dev.icode.mobile.domain.notices

import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.isAtOrUnder
import java.io.File

/**
 * Where a notice is about, read from the snapshot when the work begins (docs/notices-redesign-plan.md
 * D2), so that it still says where after another workspace is opened.
 */
object NoticePlaces {
    /** Phone storage, and iCode's own, as the file explorer names them. */
    const val PHONE_STORAGE = "Phone storage"
    const val ICODE_STORAGE = "iCode storage"
    const val RUNTIME = "Linux runtime"

    /** The open workspace, with [subject] when there is one; null with none open. */
    fun workspace(snapshot: ICodeSnapshot, subject: String? = null): NoticePlace? =
        snapshot.activeWorkspaceLocation?.let { NoticePlace(snapshot.activeWorkspace, it, subject = subject) }

    /**
     * Where [location] is: in a workspace, that workspace and the folder it is in (or itself, a folder,
     * when [inside] is false); outside them, phone storage or iCode's own and the folder.
     */
    fun of(snapshot: ICodeSnapshot, location: String, inside: Boolean = true): NoticePlace {
        val folder = if (inside) location.substringBeforeLast('/', location) else location
        val workspace = workspaceOf(snapshot, location)
        if (workspace != null) {
            val relative = folder.removePrefix(workspace.second).trimStart('/').takeIf { it.isNotEmpty() }
            return NoticePlace(workspace.first, workspace.second, subject = relative)
        }
        val shared = snapshot.filesBrowser?.sharedRoot?.takeIf { it.isNotEmpty() && location.isAtOrUnder(it) }
        return if (shared != null) {
            NoticePlace(PHONE_STORAGE, subject = folder.removePrefix(shared).trimStart('/').takeIf { it.isNotEmpty() })
        } else {
            NoticePlace(ICODE_STORAGE, subject = File(folder).name.takeIf { it.isNotEmpty() })
        }
    }

    /**
     * Git's place: the open workspace, its repository when it holds more than one, the branch and
     * where it goes (its upstream, else its remote).
     */
    fun git(snapshot: ICodeSnapshot): NoticePlace? {
        val base = workspace(snapshot) ?: return null
        val repository = snapshot.gitRepository ?: return base
        val several = snapshot.gitRepositories.size > 1
        val root = base.workspaceLocation.orEmpty()
        return base.copy(
            repository = repository.workTreePath.takeIf { several }?.let { it.removePrefix(root).trimStart('/').ifEmpty { File(it).name } },
            branch = repository.branch.takeUnless { repository.detached },
            remote = repository.upstream ?: repository.remoteName,
        )
    }

    /** A workspace by the name it has in a runtime path such as `/workspace/phone`, as a ledger kept it. */
    fun named(path: String): NoticePlace = NoticePlace(File(path).name.ifEmpty { path })

    private fun workspaceOf(snapshot: ICodeSnapshot, location: String): Pair<String, String>? {
        snapshot.activeWorkspaceLocation?.takeIf { location.isAtOrUnder(it) }?.let { return snapshot.activeWorkspace to it }
        return snapshot.workspaces.filter { location.isAtOrUnder(it.location) }.maxByOrNull { it.location.length }?.let { it.name to it.location }
    }
}
