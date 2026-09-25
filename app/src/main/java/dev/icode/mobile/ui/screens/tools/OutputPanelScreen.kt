package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** The actual output of the latest Git operation; no reconstructed logs or timestamps. */
@Composable
fun OutputPanelContent(modifier: Modifier = Modifier) {
    val operation = LocalWorkbenchSnapshot.current.gitOperation
    Column(modifier.fillMaxSize().background(IcodeColors.BackgroundDeep)) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            when (operation) {
                GitOperationState.Idle -> EmptyState(
                    title = "No Git output yet",
                    description = "Output from the next fetch, pull, push or clone will appear here. Terminal commands stay in their terminal session.",
                    modifier = Modifier.padding(IcodeSpacing.Space6),
                )
                else -> {
                    val failure = operation as? GitOperationState.Failed
                    NoticeCard(
                        tone = when (operation) {
                            is GitOperationState.Failed -> IcodeTone.Error
                            is GitOperationState.Succeeded -> IcodeTone.Success
                            else -> IcodeTone.Info
                        },
                        title = operationLabel(operation),
                        modifier = Modifier.fillMaxWidth().padding(IcodeSpacing.Space3),
                    ) {
                        if (failure != null) {
                            Text(failure.reason, style = IcodeType.Body)
                        } else if (operation is GitOperationState.Running) {
                            Text("Git is still running. New output appears as it arrives.", style = IcodeType.Caption)
                        } else {
                            Text("Git finished. The lines below are its own output.", style = IcodeType.Caption)
                        }
                    }
                    if (operation.transcript.isEmpty()) {
                        EmptyState(
                            title = "Git has not written output",
                            description = "Some operations finish without printing a transcript.",
                            modifier = Modifier.padding(IcodeSpacing.Space6),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).padding(horizontal = IcodeSpacing.Space4),
                            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                        ) {
                            items(operation.transcript) { line ->
                                MonoLine(
                                    line,
                                    color = outputLineColor(line),
                                    style = IcodeType.MonoSmall,
                                    maxLines = 8,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Which output and its state: the bottom bar, as the editor's (owner's design).
        IcodeBottomBar(
            items = listOf(BottomBarItem("Git operation output")),
            trailing = { StatusLabel(outputStatus(operation), outputStatusColor(operation)) },
        )
    }
}

internal fun operationLabel(operation: GitOperationState): String = when (operation) {
    is GitOperationState.Running -> operation.label
    is GitOperationState.Succeeded -> operation.label
    is GitOperationState.Failed -> operation.label
    GitOperationState.Idle -> "No operation"
}

private fun outputStatus(operation: GitOperationState): String = when (operation) {
    is GitOperationState.Running -> "RUNNING"
    is GitOperationState.Succeeded -> "COMPLETE"
    is GitOperationState.Failed -> "FAILED"
    GitOperationState.Idle -> "IDLE"
}

@Composable
private fun outputStatusColor(operation: GitOperationState) = when (operation) {
    is GitOperationState.Running -> IcodeColors.Blue
    is GitOperationState.Succeeded -> IcodeColors.Green
    is GitOperationState.Failed -> IcodeColors.Error
    GitOperationState.Idle -> IcodeColors.TextMuted
}

@Composable
private fun outputLineColor(line: String) = when {
    line.startsWith("fatal:") || line.startsWith("error:") -> IcodeColors.Error
    else -> IcodeColors.TextSecondary
}
