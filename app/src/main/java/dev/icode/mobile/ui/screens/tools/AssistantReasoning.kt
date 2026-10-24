package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LocalCodeTextScale
import dev.icode.mobile.designsystem.scaledBy
import dev.icode.mobile.domain.AssistantEntry
import dev.icode.mobile.domain.AssistantRules

/**
 * What the model thought before it answered, as its provider sent it: one quiet line —
 * "Reasoning · 1.2k words" — so the answer stays what the page leads with. Once the reply is
 * recorded the line [onOpen]s the thinking in a sheet (docs/assistant-sheets-plan.md §2.3); while
 * it is still being written it shows under the line, smaller and muted beside a rule, to be watched.
 */
@Composable
internal fun AssistantReasoningRow(entry: AssistantEntry.Reasoning, onOpen: () -> Unit) {
    val scale = LocalCodeTextScale.current
    val words = remember(entry.text) { wordCount(entry.text) }
    Column {
        AssistantOpenRow(
            icon = Icons.Outlined.Psychology,
            tint = IcodeColors.Accent,
            label = "Reasoning",
            status = null,
            onOpen = onOpen.takeIf { !entry.live },
            detail = "· $words",
        )
        if (entry.live) {
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

/** "1 word", "1.2k words": how long a thought is. */
internal fun wordCount(text: String): String {
    val words = text.split(WHITESPACE).count { it.isNotEmpty() }
    return if (words == 1) "1 word" else "${AssistantRules.tokenCount(words.toLong())} words"
}

private val WHITESPACE = Regex("\\s+")
