package dev.icode.mobile.ui.screens.tools

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
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.AssistantQueued
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.ui.LocalICodeGateway

/**
 * The chat's waiting messages (docs/assistant-and-mcp.md), in a box as the approval's, above the
 * composer, oldest first: a tap takes one back into the composer to edit, Send now sends it at once,
 * × removes it. Many scroll within the box.
 */
@Composable
internal fun AssistantQueueCard(queued: List<AssistantQueued>, modifier: Modifier, onEdit: (AssistantQueued) -> Unit) {
    val gateway = LocalICodeGateway.current
    IcodeCard(modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.Space1)) {
        Column(Modifier.heightIn(max = IcodeSizes.TouchTarget * VISIBLE_ROWS).verticalScroll(rememberScrollState())) {
            queued.forEach { message ->
                Row(
                    Modifier.fillMaxWidth().clip(IcodeShapes.Small).clickable(onClickLabel = "Edit") { onEdit(message) }.padding(start = IcodeSpacing.Space1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = "Waiting", tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
                    Text(
                        message.text.ifBlank { message.files.joinToString { it.name } },
                        style = IcodeType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (message.text.isNotBlank() && message.files.isNotEmpty()) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
                        Text(message.files.size.toString(), style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
                    }
                    SecondaryButton("Send now", { gateway.dispatch(ICodeIntent.SendQueuedAssistantMessage(message.id)) }, compact = true)
                    IconButton(onClick = { gateway.dispatch(ICodeIntent.RemoveQueuedAssistantMessage(message.id)) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconMd))
                    }
                }
            }
        }
    }
}

/** The rows the box shows before it scrolls: a half row past them says there are more. */
private const val VISIBLE_ROWS = 3.5f
