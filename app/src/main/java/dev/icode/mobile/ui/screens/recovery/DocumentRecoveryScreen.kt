package dev.icode.mobile.ui.screens.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmDialog
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.RecoverableDraft
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/**
 * Unsaved work the last session left behind.
 *
 * Restoring opens the file — in its own workspace if that is another one —
 * where the editor offers the draft against the version on disk: the one
 * restore path, so both ask the same question.
 */
@Composable
fun DocumentRecoveryScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    val drafts = state.recoverableDrafts
    val root = state.activeWorkspaceLocation
    val inWorkspace = drafts.filter { root != null && it.location.startsWith("$root/") }
    var discarding by remember { mutableStateOf<List<RecoverableDraft>>(emptyList()) }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Recover Unsaved Work", subtitle = "Unexpected shutdown recovery center", onBack = actions.back)
        if (drafts.isEmpty()) {
            EmptyState(
                title = "Nothing to recover",
                description = "Every document was saved before iCode last closed.",
                modifier = Modifier.padding(IcodeSpacing.Space6),
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f).padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            item {
                NoticeCard(
                    tone = IcodeTone.Warning,
                    title = "Unsaved changes detected",
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "iCode was closed or interrupted unexpectedly. The following documents have local auto-recovery caches.",
                        style = IcodeType.Body,
                    )
                }
            }
            item {
                SectionLabel(
                    "Recoverable Documents",
                    Modifier.padding(top = IcodeSpacing.Space3, bottom = IcodeSpacing.Space1),
                )
            }
            items(drafts, key = { it.location }) { draft ->
                IcodeListRow(
                    card = true,
                    content = {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(IcodeColors.Amber)
                                Text(
                                    draft.name,
                                    modifier = Modifier.weight(1f).padding(start = IcodeSpacing.Space2),
                                    style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                                )
                                MonoLine(ByteSize.format(draft.sizeBytes), color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                            }
                            MonoLine(
                                text = if (draft in inWorkspace) draft.location.removePrefix("$root/") else draft.location,
                                modifier = Modifier.padding(top = IcodeSpacing.Space2),
                                style = IcodeType.MonoSmall,
                            )
                            Text("Auto-saved: ${RelativeTime.since(draft.savedAtEpochMillis)}", style = IcodeType.Caption)
                            TwoColumnActions(
                                left = {
                                    SecondaryButton(
                                        "Restore Cache",
                                        { gateway.dispatch(WorkbenchIntent.OpenRecoverableDraft(draft.location)) },
                                        Modifier.fillMaxWidth(),
                                        color = IcodeColors.Accent,
                                    )
                                },
                                right = {
                                    SecondaryButton(
                                        "Discard",
                                        { discarding = listOf(draft) },
                                        Modifier.fillMaxWidth(),
                                        color = IcodeColors.Error,
                                    )
                                },
                                modifier = Modifier.padding(top = IcodeSpacing.Space2),
                            )
                        }
                    },
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            if (inWorkspace.isNotEmpty()) {
                PrimaryButton(
                    "Restore All Workspace Docs",
                    { inWorkspace.forEach { gateway.dispatch(WorkbenchIntent.OpenRecoverableDraft(it.location)) } },
                    Modifier.fillMaxWidth(),
                )
            }
            SecondaryButton(
                "Discard All Recovery Files",
                { discarding = drafts },
                Modifier.fillMaxWidth(),
                color = IcodeColors.TextMuted,
            )
        }
    }

    if (discarding.isNotEmpty()) {
        IcodeConfirmDialog(
            title = if (discarding.size == 1) "Discard ${discarding.single().name}?" else "Discard ${discarding.size} drafts?",
            message = "The unsaved text is deleted. The files on disk stay as they are.",
            confirmLabel = "Discard",
            onConfirm = {
                discarding.forEach { gateway.dispatch(WorkbenchIntent.DiscardDraft(it.location)) }
                discarding = emptyList()
            },
            onDismiss = { discarding = emptyList() },
        )
    }
}
