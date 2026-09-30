package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeOverflowMenu
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionHeader
import dev.icode.mobile.designsystem.gitStatusTone
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.RuntimeComponents
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Source Control: repository state, staging groups and commit entry. */
@Composable
fun SourceControlScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val repository = state.gitRepository
    if (state.gitAvailable == false) {
        // A repository may well exist on disk; Git simply cannot run yet, so
        // offering "Initialize Git" here would be wrong.
        val installStep = state.components.installing(RuntimeComponents.Git)?.progress?.step
        GitUnavailableGate(state.gitOperation, installStep) { gateway.dispatch(WorkbenchIntent.ProvisionGit) }
        return
    }
    if (repository == null) {
        GitInitializationGate { gateway.dispatch(WorkbenchIntent.InitializeGit) }
        return
    }

    var commitMessage by rememberSaveable(repository.workTreePath) { mutableStateOf("") }

    // Returning from the editor or the terminal must not show stale changes.
    LaunchedEffect(repository.workTreePath) { gateway.dispatch(WorkbenchIntent.RefreshGit) }
    // A message that stays after its commit is created gets reused by accident
    // on the next one.
    LaunchedEffect(Unit) {
        gateway.effects.collect { effect ->
            if (effect is WorkbenchEffect.GitCommitted) commitMessage = ""
        }
    }

    val staged = state.gitChanges.filter { it.staged }
    val changes = state.gitChanges.filterNot { it.staged }
    val hasIdentity = !repository.authorName.isNullOrBlank() && !repository.authorEmail.isNullOrBlank()
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = repository.branch.ifBlank { state.activeBranch },
            actions = {
                Text(
                    syncLabel(repository.upstream, repository.ahead, repository.behind, repository.remoteName),
                    style = IcodeType.MonoSmall,
                    modifier = Modifier.clickable { actions.navigate(Route.BranchSelector) },
                )
                IconButton(onClick = { gateway.dispatch(WorkbenchIntent.RefreshGit) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = IcodeColors.TextPrimary)
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            if (repository.inProgress != GitInProgress.None) {
                item {
                    InProgressBanner(
                        inProgress = repository.inProgress,
                        conflicts = state.gitChanges.count { it.status == GitStatusCodes.CONFLICTED },
                        onAbort = { gateway.dispatch(WorkbenchIntent.AbortInProgress) },
                    )
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
                        PrimaryButton(
                            "Commit",
                            { gateway.dispatch(WorkbenchIntent.CommitStaged(commitMessage)) },
                            Modifier.fillMaxWidth(),
                            enabled = staged.isNotEmpty() && commitMessage.isNotBlank(),
                        )
                        Text(
                            "Author: ${repository.authorName} <${repository.authorEmail}> · Edit",
                            modifier = Modifier.clickable { actions.navigate(Route.GitAuth) }
                                .padding(top = IcodeSpacing.Space2),
                            style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary),
                        )
                        SecondaryButton(
                            "Commit & Push",
                            { actions.navigate(Route.CommitPush) },
                            Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
                        )
                    }
                }
            }
            item {
                SectionHeader(
                    "Staged changes (${staged.size})",
                    actionLabel = if (staged.isEmpty()) null else "UNSTAGE ALL",
                    onAction = { gateway.dispatch(WorkbenchIntent.UnstageAllFiles) },
                )
            }
            items(staged, key = { "staged:${it.path}" }) { change ->
                GitChangeRow(
                    change = change,
                    onOpen = { openChange(gateway, actions, change) },
                    onToggleStage = { gateway.dispatch(WorkbenchIntent.UnstageFile(change.path)) },
                    onDiscard = { requestDiscard(gateway, actions, change) },
                )
            }
            item {
                SectionHeader(
                    "Changes (${changes.size})",
                    actionLabel = if (changes.isEmpty()) null else "STAGE ALL",
                    onAction = { gateway.dispatch(WorkbenchIntent.StageAllFiles) },
                )
            }
            items(changes, key = { "unstaged:${it.path}" }) { change ->
                GitChangeRow(
                    change = change,
                    onOpen = { openChange(gateway, actions, change) },
                    onToggleStage = { gateway.dispatch(WorkbenchIntent.StageFile(change.path)) },
                    onDiscard = { requestDiscard(gateway, actions, change) },
                )
            }
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
    }
}

/** Holds the change for confirmation; nothing is discarded until it is confirmed. */
private fun requestDiscard(gateway: WorkbenchGateway, actions: NavigationActions, change: GitChange) {
    gateway.dispatch(WorkbenchIntent.RequestDiscardChanges(change.path))
    actions.navigate(Route.DeleteConfirmation)
}

/**
 * Selecting a change opens the surface that explains it: the conflict workflow,
 * the confirmation that restores a deleted file, or the diff. Staging stays on
 * the trailing action so a review tap can never stage by accident. The diff is
 * requested before navigating so the diff surface renders Git's own patch.
 */
private fun openChange(gateway: WorkbenchGateway, actions: NavigationActions, change: GitChange) {
    when {
        change.status == GitStatusCodes.CONFLICTED -> {
            gateway.dispatch(WorkbenchIntent.OpenConflict(change.path))
            actions.navigate(Route.ConflictResolution)
        }
        change.status == GitStatusCodes.DELETED && change.discardable -> requestDiscard(gateway, actions, change)
        else -> {
            gateway.dispatch(WorkbenchIntent.OpenDiff(change.path, change.staged))
            actions.navigate(Route.DiffView)
        }
    }
}

/** `↑ahead ↓behind` once an upstream exists; otherwise the honest remote state. */
private fun syncLabel(upstream: String?, ahead: Int, behind: Int, remoteName: String?): String = when {
    upstream != null -> "↑$ahead ↓$behind"
    remoteName != null -> remoteName
    else -> "No remote"
}

@Composable
private fun GitChangeRow(
    change: GitChange,
    onOpen: () -> Unit,
    onToggleStage: () -> Unit,
    onDiscard: () -> Unit,
) {
    IcodeListRow(
        card = true,
        onClick = onOpen,
        leading = {
            MonoLine(
                change.status,
                color = gitStatusTone(change.status).tint(),
                style = IcodeType.Mono.copy(fontWeight = FontWeight.Bold),
            )
        },
        content = {
            Column(Modifier.weight(1f).padding(start = IcodeSpacing.Space2)) {
                Text(
                    change.name,
                    style = IcodeType.Body.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (change.status == GitStatusCodes.DELETED) TextDecoration.LineThrough else null,
                    ),
                )
                MonoLine(change.path, color = IcodeColors.TextMuted, style = IcodeType.MonoSmall)
            }
        },
        trailing = {
            Text(
                if (change.staged) "UNSTAGE" else "STAGE",
                style = IcodeType.Label.copy(color = IcodeColors.Accent),
                modifier = Modifier
                    .clickable(onClick = onToggleStage)
                    .padding(IcodeSpacing.Space2),
            )
            // Discard is destructive, so it lives behind a visible menu and is
            // confirmed, never one tap away from Stage.
            IcodeOverflowMenu(
                contentDescription = "Actions for ${change.name}",
                actions = listOfNotNull(
                    IcodeMenuAction(
                        label = if (change.staged) "Unstage" else "Stage",
                        icon = Icons.Outlined.Add,
                        onClick = onToggleStage,
                    ),
                    // Staged work is unstaged first; a conflict is resolved, never discarded.
                    IcodeMenuAction(
                        label = "Discard changes",
                        icon = Icons.Outlined.DeleteOutline,
                        destructive = true,
                        onClick = onDiscard,
                    ).takeIf { change.discardable },
                ),
            )
        },
    )
}
