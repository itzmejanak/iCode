package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeLoadingLine
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
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.isBusy

/** Shown when the runtime has no `git` binary yet. */
@Composable
private fun GitUnavailableGate(
    operation: GitOperationState,
    /** apt's step while it installs Git: "Downloading 3774 kB", "Setting up 3 of 9". */
    installStep: String?,
    onInstall: () -> Unit,
) {
    GatePage {
        when (operation) {
            is GitOperationState.Running -> {
                EmptyState(title = operation.label, description = "This runs once, then Git is ready.")
                IcodeLoadingLine(installStep ?: "Starting…")
            }
            is GitOperationState.Failed -> {
                EmptyState(title = "Git could not be installed", description = operation.reason)
                PrimaryButton("Retry", onInstall, Modifier.fillMaxWidth())
            }
            else -> {
                EmptyState(
                    title = "Git is not installed in the runtime",
                    description = "Install it to see changes, diffs, branches and commits for this workspace.",
                    icon = Icons.Outlined.AccountTree,
                )
                PrimaryButton("Install Git", onInstall, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * What Source Control shows before a repository Git can read: Git missing (a repository may well
 * exist on disk, so offering "Initialize Git" would be wrong), a repository Git cannot read
 * (neither "not a repository" nor "no changes"), or no repository at all.
 */
@Composable
internal fun SourceControlGate(state: ICodeSnapshot, dispatch: (ICodeIntent) -> Unit) {
    val failure = state.gitFailure
    when {
        state.gitAvailable == false -> GitUnavailableGate(
            state.gitOperation,
            state.runtimeTools.installing(RuntimeTools.Git)?.progress?.step,
        ) { dispatch(ICodeIntent.ProvisionGit) }
        failure != null -> GitUnreadableGate(failure, state.isBusy(BusyAction.RefreshGit)) { dispatch(ICodeIntent.RefreshGit) }
        else -> GitInitializationGate(state.isBusy(BusyAction.InitializeGit)) { dispatch(ICodeIntent.InitializeGit) }
    }
}

/** Git ran and could not read the repository: its words, and a way to ask again once it is fixed. */
@Composable
private fun GitUnreadableGate(failure: String, retrying: Boolean, onRetry: () -> Unit) {
    GatePage {
        // What Git said follows its introduction.
        IcodeSection {
            EmptyState(
                title = "Git cannot read this repository",
                description = "The files are untouched. Git said:",
                icon = Icons.Outlined.AccountTree,
            )
            MonoLine(failure, color = IcodeColors.Error, style = IcodeType.MonoSmall, maxLines = 6)
        }
        PrimaryButton("Check again", onRetry, Modifier.fillMaxWidth(), busy = retrying)
    }
}

@Composable
internal fun GitInitializationGate(initializing: Boolean, onInitialize: () -> Unit) {
    GatePage {
        EmptyState(
            title = "This folder is not a Git repository",
            description = "Initialize Git to track changes, create commits, and manage branches in this workspace.",
            icon = Icons.Outlined.AccountTree,
        )
        IcodeSection {
            IcodeCard(Modifier.fillMaxWidth()) {
                MonoLine(
                    "Creates a local .git folder only. Your files stay where they are.",
                    color = IcodeColors.TextMuted,
                    style = IcodeType.MonoSmall,
                )
            }
            PrimaryButton("Initialize Git", onInitialize, Modifier.fillMaxWidth(), busy = initializing)
        }
    }
}

/** A gate's page: Source Control's bar, then what stands in the way and what to do about it. */
@Composable
private fun GatePage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Source Control")
        IcodePageColumn(Modifier.weight(1f), content = content)
    }
}

/**
 * States that Git is mid-operation, and offers the way out.
 *
 * Without this the user sees conflicted files and a commit button and has no
 * idea a merge is open, or that committing is what finishes it.
 */
@Composable
internal fun InProgressBanner(inProgress: GitInProgress, conflicts: Int, onAbort: () -> Unit, aborting: Boolean) {
    val name = when (inProgress) {
        GitInProgress.Merge -> "merge"
        GitInProgress.Rebase -> "rebase"
        GitInProgress.CherryPick -> "cherry-pick"
        GitInProgress.Revert -> "revert"
        GitInProgress.Bisect -> "bisect"
        GitInProgress.None -> return
    }
    NoticeCard(tone = IcodeTone.Warning, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            StatusLabel("A $name is in progress.", IcodeColors.Amber)
            MonoLine(
                if (conflicts > 0) {
                    "${countOf(conflicts, "file")} still conflicted. Resolve them, then commit to finish."
                } else {
                    "Nothing is conflicted. Commit to finish the $name."
                },
                color = IcodeColors.TextSecondary,
                style = IcodeType.MonoSmall,
            )
            SecondaryButton("Abort $name", onAbort, Modifier.fillMaxWidth(), busy = aborting)
        }
    }
}
