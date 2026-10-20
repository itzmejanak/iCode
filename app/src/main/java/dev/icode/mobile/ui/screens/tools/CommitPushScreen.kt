package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
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
import dev.icode.mobile.domain.GitActionRules
import dev.icode.mobile.domain.GitCommitSignature
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.GitSignatureStatus
import dev.icode.mobile.domain.GitSyncAction
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** Commit summary: branch state, last commit and remote synchronization. */
@Composable
fun CommitPushScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val repository = state.gitRepository
    val lastCommit = repository?.lastCommit
    val hasRemote = repository?.hasRemote == true
    val blocked = { action: GitSyncAction -> GitActionRules.syncBlocked(action, state) }
    // Each remote action starts the real command, then opens the progress
    // surface that reports what Git itself is doing.
    val sync: (ICodeIntent) -> Unit = { intent ->
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
                        !hasRemote -> "LOCAL ONLY"
                        else -> "REMOTE READY"
                    },
                    when {
                        repository == null -> IcodeColors.Error
                        !hasRemote -> IcodeColors.Amber
                        else -> IcodeColors.Green
                    },
                )
            },
        )
        IcodePageColumn(Modifier.weight(1f)) {
            // Where the branch stands, and the working tree.
            IcodeSection {
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
                            { sync(ICodeIntent.PushRemote) },
                            enabled = blocked(GitSyncAction.Push) == null,
                        )
                    }
                }
                when {
                    repository == null -> NoticeCard(tone = IcodeTone.Warning, modifier = Modifier.fillMaxWidth()) {
                        StatusLabel("This workspace is not a Git repository.", IcodeColors.Amber)
                    }
                    !hasRemote -> AddRemoteSection { name, url -> gateway.dispatch(ICodeIntent.AddRemote(name, url)) }
                    state.gitChanges.isEmpty() -> NoticeCard(tone = IcodeTone.Success, modifier = Modifier.fillMaxWidth()) {
                        StatusLabel("Working tree clean. No unstaged changes.", IcodeColors.Green)
                    }
                    else -> NoticeCard(tone = IcodeTone.Info, modifier = Modifier.fillMaxWidth()) {
                        StatusLabel("${countOf(state.gitChanges.size, "change")} in the working tree.", IcodeColors.TextSecondary)
                    }
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
                        lastCommit.signature?.let { signature ->
                            val (verdict, color) = verdictOf(signature)
                            DetailRow(
                                "Signature",
                                verdict,
                                valueStyle = IcodeType.MonoSmall,
                                valueColor = color,
                                valueIcon = Icons.Outlined.Verified.takeIf { signature.status == GitSignatureStatus.Verified },
                            )
                            signature.keyId?.let { DetailRow("Key", it, valueStyle = IcodeType.MonoSmall, valueColor = IcodeColors.TextSecondary) }
                        }
                    }
                }
            }
            TwoColumnActions(
                left = {
                    SecondaryButton(
                        "FETCH",
                        { sync(ICodeIntent.FetchRemote) },
                        Modifier.fillMaxWidth(),
                        enabled = blocked(GitSyncAction.Fetch) == null,
                    )
                },
                right = {
                    SecondaryButton(
                        "PULL",
                        { sync(ICodeIntent.PullRemote) },
                        Modifier.fillMaxWidth(),
                        enabled = blocked(GitSyncAction.Pull) == null,
                    )
                },
            )
        }
        repository?.let { GitBottomBar(state, it, actions, onSyncPage = true) }
    }
}

private fun pushLabel(repository: GitRepositoryState?): String =
    if (repository?.upstream != null && repository.ahead > 0) "Push (${repository.ahead})" else "Push"

private fun branchSummary(repository: GitRepositoryState?): String = when {
    repository == null -> "Initialize Git to enable source control"
    repository.detached -> "No branch checked out — choose one to commit and push"
    !repository.hasCommits -> "No commits yet — stage changes and commit"
    repository.upstream != null -> "${countOf(repository.ahead, "commit")} ahead, ${repository.behind} behind"
    repository.hasRemote -> "Remote \"${repository.remoteName}\" · no upstream for this branch"
    else -> "Local repository · ${countOf(repository.branches.size, "branch", "branches")}"
}

/** Git's verdict on a commit's signature (docs/gpg-signing-plan.md §7), and its colour: Verified by whom, or why not. */
@Composable
private fun verdictOf(signature: GitCommitSignature): Pair<String, Color> = when (signature.status) {
    GitSignatureStatus.Verified -> "Verified" + signature.signer?.let { " · " + it.substringBefore(" <") }.orEmpty() to IcodeColors.Green
    GitSignatureStatus.Expired -> "Verified · expired" to IcodeColors.Amber
    GitSignatureStatus.Revoked -> "Key revoked" to IcodeColors.Error
    GitSignatureStatus.Bad -> "Bad signature" to IcodeColors.Error
    GitSignatureStatus.UnknownKey -> "Key not on this phone" to IcodeColors.Amber
    GitSignatureStatus.Unsigned -> "Not signed" to IcodeColors.TextMuted
}
