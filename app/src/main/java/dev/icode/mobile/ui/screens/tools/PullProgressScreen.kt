package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitTransferLabel
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Live progress for fetch, pull and push, and Commit & Push's commit before its push.
 *
 * Everything shown is Git's own reporting: the bar only carries a percentage
 * once Git prints one, and the transcript is its stderr. A finished operation
 * stays on screen until the user leaves, so the summary is readable rather
 * than flashing past (PR-GIT-12).
 */
@Composable
fun PullProgressScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val operation = state.gitOperation
    val running = operation is GitOperationState.Running
    // A failure has a message worth reading, so only success leaves by itself.
    LaunchedEffect(operation) {
        if (operation is GitOperationState.Succeeded) actions.back()
    }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Syncing Repository", onBack = actions.back)
        Column(Modifier.padding(IcodeSpacing.Space5)) {
            Text(
                headline(operation, state.gitRepository?.remoteName, state.activeBranch),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = IcodeType.Title,
            )
            val measured = (operation as? GitOperationState.Running)?.progress
            val trackModifier = Modifier.fillMaxWidth().padding(vertical = IcodeSpacing.Space4)
            if (running && measured == null) {
                IndeterminateProgressTrack(trackModifier)
            } else {
                ProgressTrack(fraction(operation), trackModifier)
            }
            MonoLine(
                measured?.phase ?: waitingLabel(operation),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = if (measured == null) IcodeColors.TextMuted else IcodeColors.Accent,
                style = IcodeType.MonoSmall,
            )
            val transcript = operation.transcript
            if (transcript.isNotEmpty()) {
                IcodeCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                        transcript.forEach { line ->
                            MonoLine(line, color = lineColor(line), style = IcodeType.MonoSmall)
                        }
                    }
                }
            }
            (operation as? GitOperationState.Failed)?.let { failure ->
                NoticeCard(
                    tone = IcodeTone.Error,
                    modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space3),
                ) {
                    StatusLabel(failure.reason, IcodeColors.Error)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        SecondaryButton(
            if (running) "Run in Background" else "Close",
            actions.back,
            Modifier.fillMaxWidth().padding(IcodeSpacing.Space5),
        )
    }
}

private fun headline(operation: GitOperationState, remote: String?, branch: String): String {
    val target = listOfNotNull(remote, branch.ifBlank { null }).joinToString(" ")
    return when (operation) {
        // Committing is local: it names no remote.
        is GitOperationState.Running -> if (operation.label == GitTransferLabel.COMMITTING) operation.label else "${operation.label} $target".trim()
        is GitOperationState.Succeeded -> "${operation.label} complete"
        is GitOperationState.Failed -> "${operation.label} failed"
        is GitOperationState.Cancelled -> "${operation.label} cancelled"
        GitOperationState.Idle -> "Nothing in progress"
    }
}

/** What is happening before Git has printed anything of its own. */
private fun waitingLabel(operation: GitOperationState): String = when (operation) {
    is GitOperationState.Running -> if (operation.label == GitTransferLabel.COMMITTING) "Writing the commit…" else "Contacting the remote…"
    is GitOperationState.Succeeded -> "Done"
    is GitOperationState.Failed, is GitOperationState.Cancelled -> "Stopped"
    GitOperationState.Idle -> ""
}

/** Only Git's measured fraction; a running phase it has not measured shows none. */
private fun fraction(operation: GitOperationState): Float = when (operation) {
    is GitOperationState.Running -> operation.progress?.fraction ?: 0f
    is GitOperationState.Succeeded -> 1f
    else -> 0f
}

@Composable
private fun lineColor(line: String): Color = when {
    line.startsWith("fatal:") || line.startsWith("error:") -> IcodeColors.Error
    line.contains("done.") -> IcodeColors.Green
    else -> IcodeColors.TextSecondary
}
