package dev.icode.mobile.ui.screens.tools

import androidx.compose.runtime.Composable
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route

/**
 * The bottom bar of Source Control and of its sync page, the same on both (workspace-destinations-
 * plan.md D7), as VS Code's status bar and the editor's bottom section: the repository — when the
 * workspace holds more than one — and the branch, each opening its sheet over the page, then ↑↓,
 * the sync page, lit while it is the page shown.
 */
@Composable
internal fun GitBottomBar(state: WorkbenchSnapshot, repository: GitRepositoryState, actions: NavigationActions, onSyncPage: Boolean) {
    val name = repositoryLabel(repository.workTreePath, state.activeWorkspaceLocation)
    val branch = repository.branch.ifBlank { state.activeBranch }.ifBlank { "No branch" }
    IcodeBottomBar(
        items = listOfNotNull(
            BottomBarItem(name, onClick = { actions.navigate(Route.RepositorySelector) }, description = "Repository $name, choose another")
                .takeIf { state.gitRepositories.size > 1 },
            BottomBarItem(branch, onClick = { actions.navigate(Route.BranchSelector) }, description = "Branch $branch, switch branch"),
            BottomBarItem(
                syncLabel(repository),
                onClick = { actions.navigate(Route.CommitPush) },
                active = onSyncPage,
                description = "Sync with the remote: ${syncLabel(repository)}",
            ),
        ),
    )
}

/** A repository by its folder in the workspace; the workspace's own by the workspace's name. */
internal fun repositoryLabel(location: String, workspace: String?): String =
    if (location == workspace || workspace == null) location.substringAfterLast('/') else location.removePrefix("$workspace/")

/** ↑ to push, ↓ to pull, once the branch has an upstream; else the remote, or none. */
internal fun syncLabel(repository: GitRepositoryState): String = when {
    repository.upstream != null -> "↑${repository.ahead} ↓${repository.behind}"
    repository.remoteName != null -> repository.remoteName
    else -> "No remote"
}
