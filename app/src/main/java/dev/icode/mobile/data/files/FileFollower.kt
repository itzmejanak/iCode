package dev.icode.mobile.data.files

import dev.icode.mobile.data.workspace.WorkspaceLifecycleCoordinator.Reopen

/**
 * What follows each change the operations make, in iCode's own state (docs/explorer-gaps-plan.md §4)
 * — tabs, the tree, recent files, a whole workspace closed, renamed or retired: the
 * FileChangeFollower's, called under the mutation lock as each change is made.
 */
internal interface FileFollower {
    /** [location] is a whole workspace. */
    fun isWorkspace(location: String): Boolean

    /** Before [location] moves or goes; what to open again, or null. */
    suspend fun leaving(location: String): Reopen?

    /** [location] stayed where it was: its move failed. */
    suspend fun stayed(location: String, reopen: Reopen?)

    suspend fun moved(from: String, to: String, reopen: Reopen?)

    /** [location] went: to Trash, or an empty folder removed. */
    suspend fun removed(location: String)

    /** Once a change is done, for what it [touched]. */
    suspend fun refresh(touched: Collection<String>)
}
