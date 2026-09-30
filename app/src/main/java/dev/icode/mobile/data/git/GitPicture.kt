package dev.icode.mobile.data.git

import dev.icode.mobile.domain.WorkbenchSnapshot

/** What Git says about one workspace. */
internal data class GitPicture(
    val available: Boolean,
    val state: GitWorkspaceSnapshot,
    /** Git's branch, else `.git/HEAD`'s, so a repository names its branch before Git is installed. */
    val branch: String,
    /** Git could not read the repository: its words. The workspace still opens; Source Control says this. */
    val failure: String? = null,
    /** The repositories in the workspace; Git works in the chosen one, whose picture this is. */
    val repositories: List<String> = emptyList(),
)

/** What Git says, onto the workbench: one copy, for a workspace opened and for one refreshed. */
internal fun WorkbenchSnapshot.withGit(picture: GitPicture): WorkbenchSnapshot = copy(
    gitChanges = picture.state.changes,
    gitRepository = picture.state.repository,
    gitRepositories = picture.repositories,
    gitFailure = picture.failure,
    gitAvailable = picture.available,
    activeBranch = picture.branch,
)
