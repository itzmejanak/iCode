package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.designsystem.BottomBarEntry
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route

/**
 * The bottom bar of Source Control and of its sync page, the same on both (workspace-destinations-
 * plan.md D7), as VS Code's status bar: on the left the repository — when the workspace holds more
 * than one — and the branch, each a button that says which it is and opens its sheet, its name cut
 * short to fit; on the right the arrows up and down with what is to push and to pull, opening the
 * sync page and lit there.
 */
@Composable
internal fun GitBottomBar(state: ICodeSnapshot, repository: GitRepositoryState, actions: NavigationActions, onSyncPage: Boolean) {
    val name = repositoryLabel(repository.workTreePath, state.activeWorkspaceLocation)
    val branch = repository.branch.ifBlank { state.activeBranch }.ifBlank { "No branch" }
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
            val item = BottomBarItem(syncLabel(repository), onClick = { actions.navigate(Route.CommitPush) }, active = onSyncPage, description = syncDescription(repository))
            if (repository.upstream == null) {
                BottomBarEntry(item)
            } else {
                BottomBarEntry(item) { color ->
                    SyncCount(Icons.Outlined.ArrowUpward, repository.ahead, color)
                    SyncCount(Icons.Outlined.ArrowDownward, repository.behind, color)
                }
            }
        },
    )
}

/** One of the sync counts, against its arrow so the two read as a pair: up to push, down to pull. */
@Composable
private fun SyncCount(icon: ImageVector, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(IcodeSizes.IconSm))
        Text(count.toString(), style = IcodeType.Body.copy(color = color))
    }
}

/** A repository by its folder in the workspace; the workspace's own by the workspace's name. */
internal fun repositoryLabel(location: String, workspace: String?): String =
    if (location == workspace || workspace == null) location.substringAfterLast('/') else location.removePrefix("$workspace/")

/** Without an upstream, the bar names the remote, or says there is none. */
private fun syncLabel(repository: GitRepositoryState): String = repository.remoteName ?: "No remote"

/** What the sync entry says aloud: the counts once the branch has an upstream. */
private fun syncDescription(repository: GitRepositoryState): String =
    if (repository.upstream != null) "Sync with the remote: ${repository.ahead} to push, ${repository.behind} to pull"
    else "Sync with the remote: ${syncLabel(repository)}"
