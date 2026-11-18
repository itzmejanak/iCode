package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhoneAndroid
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
import dev.icode.mobile.domain.AssistantElsewhere
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantTurn
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.LocalICodeGateway

/**
 * A call of [turn] waiting for the user (docs/assistant-and-mcp.md), in a box as the composer's,
 * above it and aligned with it — its text where the composer's is: what it asks, the command (or the
 * file) on one line, what it reaches outside [workspace] when it does, and its answers. A tap on what
 * it asks [onDetails]: its step's sheet, with all it will do (docs/assistant-and-mcp.md).
 */
@Composable
internal fun AssistantApprovalCard(turn: AssistantTurn, approval: AssistantApproval, workspace: String?, modifier: Modifier, onDetails: (() -> Unit)?) {
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
                            reach?.place?.kind == AssistantElsewhere.Kind.App -> Icons.Outlined.PhoneAndroid
                            reach?.place?.kind == AssistantElsewhere.Kind.Server -> Icons.Outlined.Extension
                            reach?.place?.kind == AssistantElsewhere.Kind.Terminal -> Icons.Outlined.Terminal
                            reach != null -> Icons.Outlined.FolderOpen
                            command -> Icons.Outlined.Terminal
                            else -> Icons.Outlined.EditNote
                        },
                        contentDescription = null,
                        tint = IcodeColors.Amber,
                        modifier = Modifier.size(IcodeSizes.IconSm),
                    )
                    Text(AssistantPermissions.question(approval, AssistantPlace.name(workspace)), style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    if (onDetails != null) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconMd))
                    }
                }
                reach?.takeIf { it.place == null }?.let {
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
            ApprovalAnswers(turn, approval, workspace, Modifier.fillMaxWidth())
        }
    }
}

/** [turn] waiting for a step the user takes (docs/voice-and-phone-control.md): what to do, and Stop or Done. */
@Composable
internal fun AssistantPauseCard(turn: AssistantTurn, pause: AssistantPause, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    IcodeCard(modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space2)) {
        Column(Modifier.padding(horizontal = IcodeSpacing.Space1), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Text(AssistantPause.QUESTION, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
            Text(pause.detail, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
                SecondaryButton("Stop", { gateway.dispatch(ICodeIntent.StopAssistant(turn.workspace, turn.session)) }, compact = true)
                PrimaryButton(AssistantPause.PROCEED, { gateway.dispatch(ICodeIntent.ContinueAssistant(turn.workspace, turn.session)) }, compact = true)
            }
        }
    }
}

/**
 * Deny, Allow in, Allow, at the end of [modifier]'s width: the card's answers to [turn], and its step's
 * sheet's. An agent's request has its own answers, in its words, refusals first (docs/assistant-and-mcp.md).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApprovalAnswers(turn: AssistantTurn, approval: AssistantApproval, workspace: String?, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val answer = { it: AssistantAnswer -> gateway.dispatch(ICodeIntent.AnswerAssistant(turn.workspace, turn.session, approval.call.id, it)) }
    if (approval.options.isNotEmpty()) {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            approval.options.sortedBy { it.allows }.forEach { option ->
                val choose = {
                    val said = if (option.allows) AssistantAnswer.Allow else AssistantAnswer.Deny
                    gateway.dispatch(ICodeIntent.AnswerAssistant(turn.workspace, turn.session, approval.call.id, said, option.id))
                }
                if (option.allows) PrimaryButton(option.label, choose, compact = true) else SecondaryButton(option.label, choose, compact = true)
            }
        }
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
        SecondaryButton("Deny", { answer(AssistantAnswer.Deny) }, compact = true)
        AssistantPermissions.allowIn(approval, workspace)?.let { SecondaryButton(it, { answer(AssistantAnswer.AllowInChat) }, compact = true) }
        PrimaryButton(AssistantPermissions.allowOnce(approval), { answer(AssistantAnswer.Allow) }, compact = true)
    }
}

/** What the middle answer opens, for this chat until iCode closes; null when it opens nothing. */
internal fun allowInMeaning(approval: AssistantApproval, workspace: String?): String? {
    val reach = approval.reach
    return when {
        approval.always -> null
        reach == null -> workspace?.let { "Allow in $it: this chat's changes and commands in $it stop asking, until iCode closes." }
        reach.place?.kind == AssistantElsewhere.Kind.App -> "Allow in ${reach.place.name}: this chat acts in ${reach.place.name} without asking, until iCode closes."
        reach.place?.kind == AssistantElsewhere.Kind.Server -> "Allow in ${reach.place.name}: this chat uses ${reach.place.name}'s tools without asking, until iCode closes."
        reach.place?.kind == AssistantElsewhere.Kind.Terminal -> "Allow in ${reach.place.name}: this chat runs, reads and types in your ${reach.place.name} without asking, until iCode closes."
        reach.roots.isEmpty() -> null
        else -> "Allow in Chat: this chat may reach ${reach.roots.joinToString()} without asking, until iCode closes."
    }
}
