package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** Clone progress with streaming Git output; auto-advances into the explorer. */
@Composable
fun CloneProgressScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val operation = state.gitOperation
    // The clone finishes when Git exits, not after a fixed delay. A failure
    // returns to the form so the address or the name can be corrected. Success
    // takes this screen's place: stacked under the new workspace, Back would
    // show it again, still succeeded, and it would open the workspace again.
    LaunchedEffect(actions.captureMode, operation) {
        if (actions.captureMode) return@LaunchedEffect
        when (operation) {
            is GitOperationState.Succeeded -> actions.replace(Route.WorkspaceExplorer)
            is GitOperationState.Failed, is GitOperationState.Cancelled -> actions.back()
            else -> Unit
        }
    }
    var cancelling by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(IcodeSpacing.Gutter)) {
        Text("GIT CLONE IN PROGRESS", style = IcodeType.Label.copy(color = IcodeColors.Accent))
        Text(
            state.cloneName,
            style = IcodeType.PageTitle,
            modifier = Modifier.padding(top = IcodeSpacing.Space2 + IcodeSpacing.Micro),
        )
        MonoLine(
            text = "Dest: ${WorkspaceRuntimePath.of(state.cloneName)}",
            modifier = Modifier.padding(top = IcodeSpacing.Space1 + IcodeSpacing.Micro),
            style = IcodeType.MonoSmall,
        )
        Divider()
        // Git's stderr is tailed while the clone runs, so both the bar and the
        // transcript below are Git's own reporting, not an animation.
        val running = operation as? GitOperationState.Running
        val trackModifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space5)
        if (running != null && running.progress == null) {
            IndeterminateProgressTrack(trackModifier)
        } else {
            ProgressTrack(
                running?.progress?.fraction ?: if (operation is GitOperationState.Succeeded) 1f else 0f,
                trackModifier,
            )
        }
        running?.progress?.let { progress ->
            MonoLine(
                progress.phase,
                modifier = Modifier.padding(top = IcodeSpacing.Space2),
                color = IcodeColors.Accent,
                style = IcodeType.MonoSmall,
            )
        }
        IcodeCard(Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space4)) {
            Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                val transcript = operation.transcript
                if (transcript.isEmpty()) {
                    MonoLine("Starting git clone…", color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall)
                } else {
                    transcript.forEach { line ->
                        MonoLine(
                            line,
                            color = if (line.contains("done.")) IcodeColors.Green else IcodeColors.TextSecondary,
                            style = IcodeType.MonoSmall,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // Cancelling stops Git; the screen leaves once it has actually stopped.
        SecondaryButton(
            if (cancelling) "Cancelling…" else "Cancel Clone",
            {
                if (operation is GitOperationState.Running) {
                    cancelling = true
                    gateway.dispatch(ICodeIntent.CancelGitTransfer)
                } else {
                    actions.back()
                }
            },
            Modifier.fillMaxWidth(),
            enabled = !cancelling,
        )
    }
}
