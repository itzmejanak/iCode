package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionHeader
import dev.icode.mobile.designsystem.gitStatusTone
import dev.icode.mobile.designsystem.tint
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Source control: commit box, staged and unstaged change groups. */
@Composable
fun SourceControlScreen(actions: DemoActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val staged = state.gitChanges.filter { it.staged }
    val changes = state.gitChanges.filterNot { it.staged }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            title = state.activeBranch,
            actions = {
                Text(
                    "↑2 ↓1",
                    style = IcodeType.MonoSmall,
                    modifier = Modifier.clickable { actions.navigate(DemoScreen.BranchSelector) },
                )
                IconButton(onClick = { actions.navigate(DemoScreen.BranchSelector) }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Branches", tint = IcodeColors.TextPrimary)
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            item {
                IcodeCard(Modifier.fillMaxWidth()) {
                    MonoLine(
                        "feat: implement on-device secure auth workflow",
                        color = IcodeColors.TextMuted,
                        style = IcodeType.MonoSmall,
                    )
                    PrimaryButton(
                        "Commit & Push",
                        { actions.navigate(DemoScreen.CommitPush) },
                        Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
                    )
                }
            }
            item { SectionHeader("Staged changes (${staged.size})", actionLabel = "UNSTAGE ALL") }
            items(staged) { change ->
                GitChangeRow(change) { gateway.dispatch(WorkbenchIntent.UnstageFile(change.path)) }
            }
            item { SectionHeader("Changes (${changes.size})", actionLabel = "STAGE ALL") }
            items(changes) { change ->
                GitChangeRow(change) {
                    when (change.status) {
                        "M" -> actions.navigate(DemoScreen.DiffView)
                        "D" -> actions.navigate(DemoScreen.DeleteConfirmation)
                        else -> gateway.dispatch(WorkbenchIntent.StageFile(change.path))
                    }
                }
            }
            item {
                SecondaryButton(
                    "Resolve Sample Conflict",
                    { actions.navigate(DemoScreen.ConflictResolution) },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GitChangeRow(change: GitChange, onClick: () -> Unit) {
    IcodeListRow(
        card = true,
        onClick = onClick,
        leading = {
            MonoLine(change.status, color = gitStatusTone(change.status).tint(), style = IcodeType.MonoSmall)
        },
        content = {
            Column(Modifier.weight(1f)) {
                Text(
                    change.name,
                    style = IcodeType.Body.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (change.status == "D") TextDecoration.LineThrough else TextDecoration.None,
                    ),
                )
                MonoLine(change.path, style = IcodeType.MonoSmall)
            }
        },
        trailing = {
            Text(
                if (change.staged) "⊟" else "⊞",
                style = IcodeType.Body.copy(color = IcodeColors.TextSecondary),
            )
        },
    )
}
