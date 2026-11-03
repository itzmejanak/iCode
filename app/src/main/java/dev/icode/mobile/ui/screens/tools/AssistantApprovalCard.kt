package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantReach
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.LocalICodeGateway

/**
 * A call waiting for the user (docs/assistant-permissions-plan.md §4.3), in a box as the composer's,
 * above it and aligned with it — its text where the composer's is: what it asks, the command (or the
 * file) on one line, what it reaches outside [workspace] when it does, and its answers. A tap on what
 * it asks [onDetails]: its step's sheet, with all it will do (docs/assistant-sheets-plan.md §2.3).
 */
@Composable
internal fun AssistantApprovalCard(approval: AssistantApproval, workspace: String, modifier: Modifier, onDetails: (() -> Unit)?) {
    val command = approval.call.kind == AssistantToolKind.Command
    val reach = approval.reach
    IcodeCard(modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space2)) {
        Column(Modifier.padding(horizontal = IcodeSpacing.Space1), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Column(
                Modifier.fillMaxWidth().clip(IcodeShapes.Small)
                    .clickable(enabled = onDetails != null, onClickLabel = "Show what it will do") { onDetails?.invoke() }
                    .padding(vertical = IcodeSpacing.Space1),
                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    Icon(
                        when {
                            reach != null -> Icons.Outlined.FolderOpen
                            command -> Icons.Outlined.Terminal
                            else -> Icons.Outlined.EditNote
                        },
                        contentDescription = null,
                        tint = IcodeColors.Amber,
                        modifier = Modifier.size(IcodeSizes.IconSm),
                    )
                    Text(question(approval.call.kind, reach != null, workspace), style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    if (onDetails != null) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconMd))
                    }
                }
                reach?.let {
                    Text(
                        "Reaches ${it.paths.joinToString()}",
                        style = IcodeType.MonoSmall.copy(color = IcodeColors.TextSecondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    approval.detail.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim(),
                    style = IcodeType.MonoSmall.copy(color = IcodeColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ApprovalAnswers(approval, workspace, Modifier.fillMaxWidth())
        }
    }
}

/**
 * A turn waiting between its steps (docs/phone-agent-plan.md L5): its question, why, and Stop or go on —
 * Continue past the step limit, Done after a step the user took.
 */
@Composable
internal fun AssistantPauseCard(pause: AssistantPause, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    IcodeCard(modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space2)) {
        Column(Modifier.padding(horizontal = IcodeSpacing.Space1), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Text(pause.kind.question, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
            Text(pause.detail, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
                SecondaryButton("Stop", { gateway.dispatch(ICodeIntent.StopAssistant) }, compact = true)
                PrimaryButton(pause.kind.proceed, { gateway.dispatch(ICodeIntent.ContinueAssistant) }, compact = true)
            }
        }
    }
}

/** Deny, Allow in, Allow, at the end of [modifier]'s width: the card's answers, and its step's sheet's. */
@Composable
internal fun ApprovalAnswers(approval: AssistantApproval, workspace: String, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val reach = approval.reach
    val answer = { it: AssistantAnswer -> gateway.dispatch(ICodeIntent.AnswerAssistant(approval.call.id, it)) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
        SecondaryButton("Deny", { answer(AssistantAnswer.Deny) }, compact = true)
        if (reach == null || reach.roots.isNotEmpty()) {
            SecondaryButton(if (reach == null) "Allow in $workspace" else "Allow in Chat", { answer(AssistantAnswer.AllowInChat) }, compact = true)
        }
        PrimaryButton(if (reach == null) "Allow" else "Allow Once", { answer(AssistantAnswer.Allow) }, compact = true)
    }
}

/** What the card asks: a change, a command, or either — or a read — reaching outside [workspace]. */
private fun question(kind: AssistantToolKind, outside: Boolean, workspace: String): String = when {
    !outside && kind == AssistantToolKind.Command -> "Run this command?"
    !outside -> "Make this change?"
    kind == AssistantToolKind.Command -> "Run this command outside $workspace?"
    kind == AssistantToolKind.Change -> "Make this change outside $workspace?"
    else -> "Read outside $workspace?"
}

/** What the middle answer opens, for this chat until iCode closes; null when it opens nothing. */
internal fun allowInMeaning(reach: AssistantReach?, workspace: String): String? = when {
    reach == null -> "Allow in $workspace: this chat's changes and commands in $workspace stop asking, until iCode closes."
    reach.roots.isEmpty() -> null
    else -> "Allow in Chat: this chat may reach ${reach.roots.joinToString()} without asking, until iCode closes."
}
