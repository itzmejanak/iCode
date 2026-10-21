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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.LocalIcodePalette
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.markdown.MarkdownBlock
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.copyText
import dev.icode.mobile.ui.editor.CodeRun
import dev.icode.mobile.ui.editor.TextMateAssets
import dev.icode.mobile.ui.editor.textMateRuns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A code block (docs/markdown-rendering-plan.md §3.3): its language and Copy above it, its lines never
 * wrapped and scrolling sideways. Coloured as the editor colours the fence's language as a file type
 * ([FileTypes.ofLanguage]) once the grammars have loaded; plain until then, and for a language with no
 * grammar (`mermaid`, `text`).
 */
@Composable
internal fun MarkdownCodeBlock(block: MarkdownBlock.Code, type: MarkdownType, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val gateway = LocalICodeGateway.current
    val runs = codeRuns(block.language?.let { FileTypes.ofLanguage(it).grammar }, block.text)
    val code = remember(block.text, runs) {
        buildAnnotatedString {
            append(block.text)
            runs?.forEach { run ->
                addStyle(
                    SpanStyle(
                        color = run.color ?: Color.Unspecified,
                        fontWeight = if (run.bold) FontWeight.Bold else null,
                        fontStyle = if (run.italic) FontStyle.Italic else null,
                    ),
                    run.start,
                    run.end,
                )
            }
        }
    }
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

/** [code]'s colours by grammar [scope], off the main thread, again when the palette changes; null until they are. */
@Composable
private fun codeRuns(scope: String?, code: String): List<CodeRun>? {
    val context = LocalContext.current
    val palette = LocalIcodePalette.current
    var loaded by remember { mutableStateOf(TextMateAssets.grammarsReady) }
    if (!loaded && scope != null) LaunchedEffect(Unit) { TextMateAssets.whenGrammarsLoaded(context) { failure -> loaded = failure == null } }
    val runs by produceState<List<CodeRun>?>(null, scope, code, loaded, palette) {
        value = if (loaded && scope != null) withContext(Dispatchers.Default) { runCatching { textMateRuns(scope, code) }.getOrNull() } else null
    }
    return runs
}
