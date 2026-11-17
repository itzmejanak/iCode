package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.LocalICodeGateway

/**
 * How full the model's context is — "84k / 200k" — as of the chat's last request, so it moves with
 * every step of a turn; always shown, its ring amber where compaction begins. Compact is a tap away.
 */
@Composable
internal fun ContextMeter(state: AssistantState) {
    val gateway = LocalICodeGateway.current
    val window = state.model?.contextWindow
    val label = AssistantRules.contextLabel(state.contextTokens, window)
    val share = AssistantRules.contextShare(state.contextTokens, window) ?: 0f
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.minimumInteractiveComponentSize().clip(IcodeShapes.Small).clickable(onClickLabel = "Context") { open = true }
                .padding(horizontal = IcodeSpacing.Space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        ) {
            // A share of a window only where the window is known; the count alone otherwise.
            if (window != null) {
                CircularProgressIndicator(
                    progress = { share },
                    modifier = Modifier.size(IcodeSizes.IconSm),
                    color = if (share >= AssistantRules.COMPACT_AT) IcodeColors.Amber else IcodeColors.Accent,
                    trackColor = IcodeColors.SurfaceTrack,
                    strokeWidth = IcodeSpacing.Micro,
                )
            }
            Text(
                label,
                style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
                maxLines = 1,
            )
        }
        IcodeActionMenu(
            expanded = open,
            onDismiss = { open = false },
            actions = listOf(
                IcodeMenuAction(
                    "Compact Chat", Icons.Outlined.Compress, { gateway.dispatch(ICodeIntent.CompactAssistantChat) },
                    enabled = state.messages.isNotEmpty() && !state.running,
                ),
            ),
        )
    }
}
