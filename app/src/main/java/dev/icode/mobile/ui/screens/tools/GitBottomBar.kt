package dev.icode.mobile.ui.screens.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Source
import androidx.compose.runtime.Composable
import dev.icode.mobile.designsystem.BottomBarEntry
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route

/**
 * The bottom bar of Source Control and of its sync page, the same on both (workspace-destinations-
 * plan.md D7), as VS Code's status bar: on the left the repository — when the workspace holds more
 * than one — and the branch, each an accent button that says which it is and opens its sheet;
 * on the right ↑↓, what is to push and to pull, opening the sync page and lit while it is shown.
 */
@Composable
internal fun GitBottomBar(state: WorkbenchSnapshot, repository: GitRepositoryState, actions: NavigationActions, onSyncPage: Boolean) {
    val name = repositoryLabel(repository.workTreePath, state.activeWorkspaceLocation)
    val branch = repository.branch.ifBlank { state.activeBranch }.ifBlank { "No branch" }
    val sync = syncLabel(repository)
    IcodeBottomBar(
        items = listOfNotNull(
            BottomBarItem(
                name,
                onClick = { actions.navigate(Route.RepositorySelector) },
                description = "Repository $name, choose another",
                choice = true,
                icon = Icons.Outlined.Source,
            ).takeIf { state.gitRepositories.size > 1 },
            BottomBarItem(
                branch,
                onClick = { actions.navigate(Route.BranchSelector) },
                description = "Branch $branch, switch branch",
                choice = true,
                icon = Icons.AutoMirrored.Outlined.CallSplit,
            ),
        ),
        trailing = {
            BottomBarEntry(
                BottomBarItem(sync, onClick = { actions.navigate(Route.CommitPush) }, active = onSyncPage, description = "Sync with the remote: $sync"),
            )
        },
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
