package dev.icode.mobile.data.git

import dev.icode.mobile.data.workspace.ChangedOnDiskException
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceDocument
import dev.icode.mobile.domain.GitConflictChoice
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticePlace
import dev.icode.mobile.domain.notices.NoticePlaces
import dev.icode.mobile.domain.notices.SourceControlNotices
import java.io.File

/**
 * How a conflicted file is read and written: through the editor's own codec,
 * so its encoding, byte-order mark and line endings survive, and never under
 * an open tab's unsaved edits.
 */
internal interface ConflictFiles {
    fun read(location: String): WorkspaceDocument
    /** Throws [ChangedOnDiskException] when the file is no longer the one [document] was read from. */
    fun write(document: WorkspaceDocument, text: String): WorkspaceDocument
    fun hasUnsavedEdits(location: String): Boolean
    /** The file changed: open tabs catch up. */
    suspend fun written(location: String)
}

/**
 * One conflicted file (S5): its marker blocks, each resolved on its own —
 * current, incoming or both — and then marked resolved, which stages it.
 */
internal class GitConflictCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val files: ConflictFiles,
    private val activeRoot: () -> String?,
    /** Stages the file: an ordinary local Git change. */
    private val act: suspend (failed: (NoticePlace?) -> Notice, operation: suspend (String) -> Unit, onSuccess: suspend () -> Unit) -> Unit,
    private val stage: suspend (workspace: String, path: String) -> Unit,
    private val refresh: suspend () -> Unit,
) {
    /** The file as read, so a write can tell whether it changed on disk since. */
    private var document: WorkspaceDocument? = null

    /**
     * Git reports change paths relative to the repository root, which is not
     * the opened folder when a subdirectory of a repository is opened.
     */
    private fun conflictRoot(): String? =
        stateHolder.current.gitRepository?.workTreePath ?: activeRoot()

    /** Loads the conflicted file straight from the worktree, markers and all. */
    suspend fun open(path: String) {
        val location = File(conflictRoot() ?: return, path).path
        runCatching { files.read(location) }
            .onSuccess { read ->
                document = read
                stateHolder.update { it.copy(activeConflict = GitConflictParser.parse(path, read.text)) }
            }
            .onFailure { error ->
                document = null
                stateHolder.update { it.copy(activeConflict = null) }
                stateHolder.tell(SourceControlNotices.conflictUnread(path, placeNow()), "git", error)
            }
    }

    fun close() {
        document = null
        stateHolder.update { it.copy(activeConflict = null) }
    }

    /**
     * Resolves the one marker block that starts at [sectionStart] and writes
     * the file back. The file is rebuilt from the parsed sections rather than
     * through `checkout --ours/--theirs`, because "both" has no Git equivalent
     * and a whole-file checkout would decide every other block too. Marking
     * resolved stays a separate explicit step.
     */
    suspend fun resolve(path: String, sectionStart: Int, choice: GitConflictChoice) {
        val conflict = stateHolder.current.activeConflict?.takeIf { it.path == path } ?: return
        val read = document ?: return
        val section = conflict.sections.firstOrNull { it.startLine == sectionStart } ?: return
        if (files.hasUnsavedEdits(read.location)) {
            stateHolder.tell(SourceControlNotices.conflictUnsavedFirst(read.name, placeNow()))
            return
        }
        runCatching { files.write(read, GitConflictParser.resolve(conflict, section, choice)) }
            .onSuccess {
                open(path)
                files.written(read.location)
                refresh()
            }
            .onFailure { error ->
                if (error is ChangedOnDiskException) {
                    open(path)
                    stateHolder.tell(SourceControlNotices.conflictChangedOnDisk(read.name, placeNow()))
                } else {
                    stateHolder.tell(SourceControlNotices.conflictNotWritten(read.name, placeNow()), "git", error)
                }
            }
    }

    suspend fun markResolved(path: String) = act({ SourceControlNotices.notMarkedResolved(path, it) }, { workspace -> stage(workspace, path) }, {})

    private fun placeNow() = NoticePlaces.git(stateHolder.current)
}
