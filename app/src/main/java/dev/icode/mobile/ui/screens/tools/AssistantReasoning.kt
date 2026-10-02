package dev.icode.mobile.ui.screens.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LocalCodeTextScale
import dev.icode.mobile.designsystem.scaledBy
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.AssistantRules

/**
 * What the model thought before it answered, as its provider sent it: one quiet line —
 * "Reasoning · 1.2k words" — that opens on the thinking itself, smaller and muted beside a rule,
 * so the answer stays what the page leads with. Open while it is still being written; folded once
 * the reply is recorded.
 */
@Composable
internal fun AssistantReasoningRow(entry: AssistantEntry.Reasoning) {
    val scale = LocalCodeTextScale.current
    var folded by rememberSaveable(entry.key) { mutableStateOf(true) }
    val open = entry.live || !folded
    val words = remember(entry.text) { entry.text.split(WHITESPACE).count { it.isNotEmpty() } }
    Column {
        Row(
            Modifier.minimumInteractiveComponentSize().clip(IcodeShapes.Small)
                .clickable(enabled = !entry.live, onClickLabel = if (open) "Hide the reasoning" else "Show the reasoning") { folded = !folded }
                .padding(end = IcodeSpacing.Space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
        ) {
            Icon(Icons.Outlined.Psychology, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
            Text("Reasoning", style = IcodeType.Caption.scaledBy(scale).copy(color = IcodeColors.TextSecondary))
            Text(
                if (words == 1) "· 1 word" else "· ${AssistantRules.tokenCount(words.toLong())} words",
                style = IcodeType.Caption.scaledBy(scale).copy(color = IcodeColors.TextMuted),
            )
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = IcodeColors.TextMuted,
                modifier = Modifier.size(IcodeSizes.IconSm),
            )
        }
        AnimatedVisibility(open) {
            Row(Modifier.height(IntrinsicSize.Min).padding(bottom = IcodeSpacing.Space1)) {
                Box(Modifier.width(IcodeSpacing.Micro).fillMaxHeight().background(IcodeColors.Outline, IcodeShapes.Track))
                Text(
                    entry.text,
                    modifier = Modifier.padding(start = IcodeSpacing.Space3),
                    style = IcodeType.Caption.scaledBy(scale).copy(color = IcodeColors.TextSecondary),
                )
            }
        }
    }
}

private val WHITESPACE = Regex("\\s+")
