package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeIconButton
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeSpinner
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.GitChangeTree
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.busySubjects
import dev.icode.mobile.domain.isBusy
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
    // The message the model writes from the staged changes (docs/commit-message-generator-plan.md).
    val written = state.assistant.commitMessage?.takeIf { it.workspace == state.activeWorkspaceLocation }
    LaunchedEffect(written?.message) {
        // Into the field once; a failure leaves what was typed as it was.
        commitMessage = written?.message ?: return@LaunchedEffect
        gateway.dispatch(ICodeIntent.CommitMessageTaken)
    }
    // Folders closed in each section's tree, as the user left them.
    var closedStaged by rememberSaveable(repository.workTreePath) { mutableStateOf(emptySet<String>()) }
    var closedChanges by rememberSaveable(repository.workTreePath) { mutableStateOf(emptySet<String>()) }
    val stagedRows = remember(staged, closedStaged) { GitChangeTree.rows(staged, closedStaged) }
    val changeRows = remember(changes, closedChanges) { GitChangeTree.rows(changes, closedChanges) }
    // The row whose sheet is shown, by its key; gone with the row (staged, committed, discarded).
    var held by rememberSaveable(repository.workTreePath) { mutableStateOf<String?>(null) }
    val sheetFor = held?.let { key ->
        stagedRows.firstOrNull { changeRowKey(true, it) == key }?.let { it to true }
            ?: changeRows.firstOrNull { changeRowKey(false, it) == key }?.let { it to false }
    }
    val hasIdentity = !repository.authorName.isNullOrBlank() && !repository.authorEmail.isNullOrBlank()
    val staging = state.busySubjects(BusyAction.Stage)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(sheetFor != null)) {
            IcodeTopBar(
                title = "Source Control",
                actions = {
                    IcodeIconButton(Icons.Outlined.Refresh, "Refresh", { gateway.dispatch(ICodeIntent.RefreshGit) }, busy = state.isBusy(BusyAction.RefreshGit))
                },
            )
            // The tree's rows sit together, as the Explorer's; the parts above keep their space.
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(IcodeSpacing.Gutter)) {
                if (repository.inProgress != GitInProgress.None) {
                    item {
                        Box(Modifier.padding(bottom = IcodeSpacing.Space3)) {
                            InProgressBanner(
                                inProgress = repository.inProgress,
                                conflicts = state.gitChanges.count { it.status == GitStatusCodes.CONFLICTED },
                                onAbort = { gateway.dispatch(ICodeIntent.AbortInProgress) },
                                aborting = state.isBusy(BusyAction.Abort),
                            )
                        }
                    }
                }
                item {
                    IcodeCard(Modifier.fillMaxWidth()) {
                        IcodeTextField(
                            value = commitMessage,
                            onValueChange = {
                                commitMessage = it
                                if (written?.failure != null) gateway.dispatch(ICodeIntent.CommitMessageTaken)
                            },
                            placeholder = "Commit message",
                            modifier = Modifier.fillMaxWidth(),
                            // A written message may have a body under its subject.
                            singleLine = false,
                            trailing = {
                                WriteMessageButton(writing = written?.writing == true, enabled = staged.isNotEmpty()) {
                                    gateway.dispatch(ICodeIntent.WriteCommitMessage)
                                }
                            },
                        )
                        written?.failure?.let { failure ->
                            Text(
                                failure,
                                modifier = Modifier.padding(top = IcodeSpacing.Space2),
                                style = IcodeType.Caption.copy(color = IcodeColors.Error),
                            )
                        }
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
                            val committing = state.isBusy(BusyAction.Commit)
                            val committable = staged.isNotEmpty() && commitMessage.isNotBlank() && !committing
                            PrimaryButton(
                                "Commit",
                                { send(ICodeIntent.CommitStaged(commitMessage)) },
                                Modifier.fillMaxWidth(),
                                enabled = committable,
                                busy = committing,
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
                }, { held = changeRowKey(true, it) }, gateway, actions, staging)
                gitChangeSection("Changes", changes, staged = false, changeRows, repository.workTreePath, { path ->
                    closedChanges = closedChanges.toggled(path)
                }, { held = changeRowKey(false, it) }, gateway, actions, staging)
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
        sheetFor?.let { (row, inStaged) ->
            val close = { held = null }
            IcodeSheetOverlay(onClose = close) {
                GitChangeSheet(row, inStaged, if (inStaged) staged else changes, repository.workTreePath, close, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** ✨ in the commit field: the staged changes described by the assistant's model; a spinner while it writes. */
@Composable
private fun WriteMessageButton(writing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (writing) {
        IcodeSpinner(Modifier.padding(IcodeSpacing.Space2).semantics { contentDescription = "Writing a commit message" })
    } else {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(IcodeSizes.BadgeSm)) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = "Write a commit message",
                tint = if (enabled) IcodeColors.Accent else IcodeColors.TextMuted,
                modifier = Modifier.size(IcodeSizes.IconMd),
            )
        }
    }
}

/** [path] closed if it was open, open if it was closed. */
private fun Set<String>.toggled(path: String): Set<String> = if (path in this) this - path else this + path
