package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Commit summary: branch state, last commit and remote synchronization. */
@Composable
fun CommitPushScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val repository = state.gitRepository
    val lastCommit = repository?.lastCommit
    val canSync = repository?.hasRemote == true
    // Each remote action starts the real command, then opens the progress
    // surface that reports what Git itself is doing.
    val sync: (WorkbenchIntent) -> Unit = { intent ->
        gateway.dispatch(intent)
        actions.navigate(Route.PullFetchProgress)
    }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Source Control",
            onBack = actions.back,
            actions = {
                StatusLabel(
                    when {
                        repository == null -> "NO REPOSITORY"
                        !canSync -> "LOCAL ONLY"
                        else -> "REMOTE READY"
                    },
                    when {
                        repository == null -> IcodeColors.Error
                        !canSync -> IcodeColors.Amber
                        else -> IcodeColors.Green
                    },
                )
            },
        )
        Column(
            modifier = Modifier.padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4),
        ) {
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = IcodeColors.Green)
                    Column(Modifier.weight(1f).padding(start = IcodeSpacing.Space2 + IcodeSpacing.Micro)) {
                        Text(
                            repository?.branch ?: state.activeBranch.ifBlank { "No branch" },
                            style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                        )
                        MonoLine(
                            branchSummary(repository),
                            color = if (repository?.upstream != null) IcodeColors.Green else IcodeColors.TextSecondary,
                            style = IcodeType.MonoSmall,
                        )
                    }
                    PrimaryButton(
                        pushLabel(repository),
                        { sync(WorkbenchIntent.PushRemote) },
                        enabled = canSync,
                    )
                }
            }
            when {
                repository == null -> NoticeCard(tone = IcodeTone.Warning, modifier = Modifier.fillMaxWidth()) {
                    StatusLabel("This workspace is not a Git repository.", IcodeColors.Amber)
                }
                !canSync -> NoticeCard(tone = IcodeTone.Info, modifier = Modifier.fillMaxWidth()) {
                    StatusLabel("No remote configured. Add one to fetch, pull and push.", IcodeColors.TextSecondary)
                }
                state.gitChanges.isEmpty() -> NoticeCard(tone = IcodeTone.Success, modifier = Modifier.fillMaxWidth()) {
                    StatusLabel("Working tree clean. No unstaged changes.", IcodeColors.Green)
                }
                else -> NoticeCard(tone = IcodeTone.Info, modifier = Modifier.fillMaxWidth()) {
                    StatusLabel("${state.gitChanges.size} change(s) in the working tree.", IcodeColors.TextSecondary)
                }
            }
            IcodeCard(Modifier.fillMaxWidth(), elevated = true) {
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro)) {
                    SectionLabel("Last Commit")
                    if (lastCommit == null) {
                        MonoLine("No commits yet", color = IcodeColors.TextMuted)
                    } else {
                        MonoLine("commit ${lastCommit.id}", color = IcodeColors.Accent)
                        Text(lastCommit.message, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                        DetailRow("Author", lastCommit.author, valueStyle = IcodeType.MonoSmall)
                        DetailRow(
                            "Time",
                            RelativeTime.since(lastCommit.authoredAtEpochMillis),
                            valueStyle = IcodeType.MonoSmall,
                            valueColor = IcodeColors.TextSecondary,
                        )
                    }
                }
            }
            TwoColumnActions(
                left = {
                    SecondaryButton(
                        "FETCH",
                        { sync(WorkbenchIntent.FetchRemote) },
                        Modifier.fillMaxWidth(),
                        enabled = canSync,
                    )
                },
                right = {
                    SecondaryButton(
                        "PULL",
                        { sync(WorkbenchIntent.PullRemote) },
                        Modifier.fillMaxWidth(),
                        enabled = canSync,
                    )
                },
            )
            SecondaryButton(
                "Open Branch Selector",
                { actions.navigate(Route.BranchSelector) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun pushLabel(repository: GitRepositoryState?): String =
    if (repository?.upstream != null && repository.ahead > 0) "Push (${repository.ahead})" else "Push"

private fun branchSummary(repository: GitRepositoryState?): String = when {
    repository == null -> "Initialize Git to enable source control"
    !repository.hasCommits -> "No commits yet — stage changes and commit"
    repository.upstream != null -> "${repository.ahead} commit(s) ahead, ${repository.behind} behind"
    repository.hasRemote -> "Remote \"${repository.remoteName}\" · no upstream for this branch"
    else -> "Local repository · ${repository.branches.size} branch(es)"
}
