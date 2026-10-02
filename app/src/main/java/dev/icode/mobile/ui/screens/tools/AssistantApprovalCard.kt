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
import dev.icode.mobile.domain.AssistantToolKind

/**
 * A change or a command waiting for the user, in a box as the composer's, above it and aligned with
 * it — its text where the composer's is: what it asks, the command (or the file) on one line, and
 * Deny, Allow All and Allow. A tap on the question opens it: all it will do, scrolling within a few
 * lines, and what Allow All means. Each new question starts closed.
 */
@Composable
internal fun AssistantApprovalCard(approval: AssistantApproval, modifier: Modifier, onAnswer: (AssistantAnswer) -> Unit) {
    val command = approval.call.kind == AssistantToolKind.Command
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
                    if (command) Icons.Outlined.Terminal else Icons.Outlined.EditNote,
                    contentDescription = null,
                    tint = IcodeColors.Amber,
                    modifier = Modifier.size(IcodeSizes.IconSm),
                )
                Text(
                    if (command) "Run this command?" else "Make this change?",
                    style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = IcodeColors.TextMuted,
                    modifier = Modifier.size(IcodeSizes.IconMd),
                )
            }
            if (open) {
                CodeSurface(Modifier.fillMaxWidth().clip(IcodeShapes.Small).heightIn(max = IcodeSizes.ApprovalDetail)) {
                    Text(approval.detail, style = IcodeType.MonoSmall, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
                Text("Allow All: this chat's changes and commands stop asking.", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
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
                SecondaryButton("Allow All", { onAnswer(AssistantAnswer.AllowAll) }, compact = true)
                PrimaryButton("Allow", { onAnswer(AssistantAnswer.Allow) }, compact = true)
            }
        }
    }
}
