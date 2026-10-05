package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import dev.icode.mobile.domain.GitChangeTree
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** Source Control: repository state, staging groups and commit entry. */
@Composable
fun SourceControlScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val repository = state.gitRepository
    if (repository == null || state.gitAvailable == false || state.gitFailure != null) {
        SourceControlGate(state, gateway::dispatch)
        return
    }

    var commitMessage by rememberSaveable(repository.workTreePath) { mutableStateOf("") }
    // The message sent to be committed, and the commit it was to follow.
    var sent by rememberSaveable(repository.workTreePath) { mutableStateOf<String?>(null) }
    var sentAfter by rememberSaveable(repository.workTreePath) { mutableStateOf<String?>(null) }
    val send = { then: ICodeIntent ->
        sent = commitMessage
        sentAfter = repository.lastCommit?.id
        gateway.dispatch(then)
    }

    // Returning from the editor or the terminal must not show stale changes.
    LaunchedEffect(repository.workTreePath) { gateway.dispatch(ICodeIntent.RefreshGit) }
    // Once a newer commit is there, the message sent became it: kept, it would be reused by
    // accident — found on return too, when Commit & Push's sync page was over this one.
    LaunchedEffect(repository.lastCommit?.id) {
        if (sent != null && repository.lastCommit?.id != sentAfter) {
            if (commitMessage == sent) commitMessage = ""
            sent = null
        }
    }

    val staged = state.gitChanges.filter { it.staged }
    val changes = state.gitChanges.filterNot { it.staged }
    // Folders closed in each section's tree, as the user left them.
    var closedStaged by rememberSaveable(repository.workTreePath) { mutableStateOf(emptySet<String>()) }
    var closedChanges by rememberSaveable(repository.workTreePath) { mutableStateOf(emptySet<String>()) }
    val stagedRows = remember(staged, closedStaged) { GitChangeTree.rows(staged, closedStaged) }
    val changeRows = remember(changes, closedChanges) { GitChangeTree.rows(changes, closedChanges) }
    val hasIdentity = !repository.authorName.isNullOrBlank() && !repository.authorEmail.isNullOrBlank()
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = "Source Control",
            actions = {
                IconButton(onClick = { gateway.dispatch(ICodeIntent.RefreshGit) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = IcodeColors.TextPrimary)
                }
            },
        )
        // The tree's rows sit together, as the Explorer's; the parts above keep their space.
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(IcodeSpacing.Space4)) {
            if (repository.inProgress != GitInProgress.None) {
                item {
                    Box(Modifier.padding(bottom = IcodeSpacing.Space3)) {
                        InProgressBanner(
                            inProgress = repository.inProgress,
                            conflicts = state.gitChanges.count { it.status == GitStatusCodes.CONFLICTED },
                            onAbort = { gateway.dispatch(ICodeIntent.AbortInProgress) },
                        )
                    }
                }
            }
            item {
                IcodeCard(Modifier.fillMaxWidth()) {
                    IcodeTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        placeholder = "Commit message",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(IcodeSpacing.Space2))
                    if (!hasIdentity) {
                        MonoLine(
                            "Set a Git author name and email before committing.",
                            color = IcodeColors.TextMuted,
                            style = IcodeType.MonoSmall,
                        )
                        PrimaryButton(
                            "Set Git Identity",
                            { actions.navigate(Route.GitAuth) },
                            Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
                        )
                        // Fetch, pull and push need no identity; this is the one way to them here.
                        SecondaryButton(
                            "Sync with Remote",
                            { actions.navigate(Route.CommitPush) },
                            Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
                        )
                    } else {
                        val committable = staged.isNotEmpty() && commitMessage.isNotBlank()
                        PrimaryButton(
                            "Commit",
                            { send(ICodeIntent.CommitStaged(commitMessage)) },
                            Modifier.fillMaxWidth(),
                            enabled = committable,
                        )
                        Text(
                            "Author: ${repository.authorName} <${repository.authorEmail}> · Edit",
                            modifier = Modifier.clickable { actions.navigate(Route.GitAuth) }
                                .padding(top = IcodeSpacing.Space2),
                            style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
                        )
                        // One transfer — commit, then push — which the sync progress follows.
                        SecondaryButton(
                            "Commit & Push",
                            {
                                send(ICodeIntent.CommitAndPush(commitMessage))
                                actions.navigate(Route.PullFetchProgress)
                            },
                            Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
                            enabled = committable,
                        )
                    }
                }
            }
            gitChangeSection("Staged changes", staged, staged = true, stagedRows, repository.workTreePath, { path ->
                closedStaged = closedStaged.toggled(path)
            }, gateway, actions)
            gitChangeSection("Changes", changes, staged = false, changeRows, repository.workTreePath, { path ->
                closedChanges = closedChanges.toggled(path)
            }, gateway, actions)
            if (staged.isEmpty() && changes.isEmpty()) {
                item {
                    EmptyState(
                        title = "Working tree clean",
                        description = "Changes in this folder will appear here.",
                        icon = Icons.Outlined.AccountTree,
                        modifier = Modifier.padding(vertical = IcodeSpacing.Space6),
                    )
                }
            }
        }
        GitBottomBar(state, repository, actions, onSyncPage = false)
    }
}

/** [path] closed if it was open, open if it was closed. */
private fun Set<String>.toggled(path: String): Set<String> = if (path in this) this - path else this + path
