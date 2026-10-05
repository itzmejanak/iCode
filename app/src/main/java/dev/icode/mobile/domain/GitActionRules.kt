package dev.icode.mobile.domain

/** A Git action that talks to the remote. */
enum class GitSyncAction { Fetch, Pull, Push }

/**
 * Whether a Git action can start now, and if not, why — one rule for every
 * place that offers one (the command palette, Commit & Push, the automatic
 * fetch) and for the coordinator that runs it, so a control is enabled only
 * when the action could succeed, and the reason reads the same everywhere.
 */
object GitActionRules {
    /** Why no Git action can run in this workspace now, or null. */
    fun repositoryBlocked(snapshot: ICodeSnapshot): String? = when {
        snapshot.activeWorkspaceLocation == null -> "Open a workspace first"
        snapshot.gitAvailable == null -> "Checking Git availability"
        snapshot.gitAvailable == false -> "Git is not installed"
        snapshot.gitRepository == null -> "Initialize Git first"
        snapshot.gitOperation is GitOperationState.Running -> "A Git operation is running"
        else -> null
    }

    /** Why [action] cannot start now, or null. */
    fun syncBlocked(action: GitSyncAction, snapshot: ICodeSnapshot): String? =
        repositoryBlocked(snapshot) ?: snapshot.gitRepository?.let { syncBlocked(action, it) }

    /**
     * What the repository itself allows: a remote to talk to; a branch to pull
     * into or push from; commits to push; an upstream to pull from, since
     * `git pull` without one can only fail.
     */
    fun syncBlocked(action: GitSyncAction, repository: GitRepositoryState): String? = when {
        !repository.hasRemote -> "Add a Git remote first"
        action == GitSyncAction.Fetch -> null
        repository.detached -> "Check out a branch first"
        action == GitSyncAction.Push && !repository.hasCommits -> "Commit changes first"
        action == GitSyncAction.Pull && repository.upstream == null -> "Push this branch first to set its upstream"
        else -> null
    }
}
