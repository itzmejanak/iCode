package dev.icode.mobile.ui.screens.tools

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.CodeSurface
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
import dev.icode.mobile.domain.AssistantReach
import dev.icode.mobile.domain.AssistantToolKind

/**
 * A call waiting for the user (docs/assistant-permissions-plan.md §4.3), in a box as the composer's,
 * above it and aligned with it — its text where the composer's is: what it asks, the command (or the
 * file) on one line, what it reaches outside [workspace] when it does, and Deny, Allow in, Allow. A tap
 * on the question opens it: all it will do, scrolling within a few lines, and what "Allow in" opens.
 * Each new question starts closed.
 */
@Composable
internal fun AssistantApprovalCard(approval: AssistantApproval, workspace: String, modifier: Modifier, onAnswer: (AssistantAnswer) -> Unit) {
    val command = approval.call.kind == AssistantToolKind.Command
    val reach = approval.reach
    var open by remember(approval.call.id) { mutableStateOf(false) }
    IcodeCard(modifier.fillMaxWidth().animateContentSize(), padding = PaddingValues(IcodeSpacing.Space2)) {
        Column(Modifier.padding(horizontal = IcodeSpacing.Space1), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            Row(
                Modifier.fillMaxWidth().clip(IcodeShapes.Small)
                    .clickable(onClickLabel = if (open) "Hide what it will do" else "Show what it will do") { open = !open }
                    .padding(vertical = IcodeSpacing.Space1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
            ) {
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
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = IcodeColors.TextMuted,
                    modifier = Modifier.size(IcodeSizes.IconMd),
                )
            }
            reach?.let {
                Text(
                    "Reaches ${it.paths.joinToString()}",
                    style = IcodeType.MonoSmall.copy(color = IcodeColors.TextSecondary),
                    maxLines = if (open) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (open) {
                CodeSurface(Modifier.fillMaxWidth().clip(IcodeShapes.Small).heightIn(max = IcodeSizes.ApprovalDetail)) {
                    Text(approval.detail, style = IcodeType.MonoSmall, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
                allowInMeaning(reach, workspace)?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
            } else {
                Text(
                    approval.detail.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim(),
                    style = IcodeType.MonoSmall.copy(color = IcodeColors.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                SecondaryButton("Deny", { onAnswer(AssistantAnswer.Deny) }, compact = true)
                if (reach == null || reach.roots.isNotEmpty()) {
                    SecondaryButton(if (reach == null) "Allow in $workspace" else "Allow in Chat", { onAnswer(AssistantAnswer.AllowInChat) }, compact = true)
                }
                PrimaryButton(if (reach == null) "Allow" else "Allow Once", { onAnswer(AssistantAnswer.Allow) }, compact = true)
            }
        }
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
private fun allowInMeaning(reach: AssistantReach?, workspace: String): String? = when {
    reach == null -> "Allow in $workspace: this chat's changes and commands in $workspace stop asking, until iCode closes."
    reach.roots.isEmpty() -> null
    else -> "Allow in Chat: this chat may reach ${reach.roots.joinToString()} without asking, until iCode closes."
}
