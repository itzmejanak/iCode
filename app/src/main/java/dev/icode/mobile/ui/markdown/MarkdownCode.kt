package dev.icode.mobile.ui.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.markdown.MarkdownBlock
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.copyText
import dev.icode.mobile.ui.syntax.rememberSyntax

/**
 * A code block (docs/markdown-rendering-plan.md §3.3): its language and Copy above it, its lines never
 * wrapped and scrolling sideways. Coloured through [rememberSyntax] as the editor colours the fence's
 * language as a file type ([FileTypes.ofLanguage]); plain until its tokens are made, and for a language
 * with no grammar (`mermaid`, `text`).
 */
@Composable
internal fun MarkdownCodeBlock(block: MarkdownBlock.Code, type: MarkdownType, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    val code = rememberSyntax(block.language?.let { FileTypes.ofLanguage(it).language }, block.text)
    IcodeCard(modifier.fillMaxWidth(), elevated = true, padding = PaddingValues(IcodeSpacing.None)) {
        Row(Modifier.fillMaxWidth().padding(start = IcodeSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
            Text(block.language.orEmpty(), style = type.small, modifier = Modifier.weight(1f))
            IconButton(onClick = { copyText(context, "Code", block.text, "Code copied", gateway::dispatch) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy the code", tint = type.colors.muted, modifier = Modifier.size(IcodeSizes.IconSm))
            }
        }
        Text(
            code,
            style = type.code,
            softWrap = false,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(start = IcodeSpacing.Space2, end = IcodeSpacing.Space2, bottom = IcodeSpacing.Space2),
        )
    }
}
