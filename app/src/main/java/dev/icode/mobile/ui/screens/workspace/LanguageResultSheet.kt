package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeModalSheet
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.domain.DiagnosticRules
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.LanguageFeatureIntent
import dev.icode.mobile.domain.LanguageQuery
import dev.icode.mobile.domain.LanguageResult
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.markdown.MarkdownAnswer

/**
 * A language server's answer over the editor (docs/lsp-plan.md §7): places to open, text to read,
 * code actions to apply, or a rename's new name. A sheet, as every question in iCode is.
 */
@Composable
internal fun LanguageResultSheet(result: LanguageResult) {
    val gateway = LocalICodeGateway.current
    val close = { gateway.dispatch(LanguageFeatureIntent.CloseResult) }
    IcodeModalSheet(onClose = close) {
        IcodeBottomSheet(title = result.title, onClose = close, modifier = Modifier.align(Alignment.BottomCenter)) {
            when (result) {
                is LanguageResult.Asking -> Text("Asking the language server…", style = IcodeType.Body, color = IcodeColors.TextSecondary)
                is LanguageResult.Failed -> Text(result.words, style = IcodeType.Body, color = IcodeColors.TextSecondary)
                is LanguageResult.Text -> MarkdownAnswer(result.text, streaming = false, place = null)
                is LanguageResult.Places -> result.places.forEach { place ->
                    IcodeListRow(
                        title = place.preview.ifBlank { place.file },
                        subtitle = "${place.file}:${DiagnosticRules.locationOf(place.range)}",
                        indent = IcodeSpacing.IndentStep * place.depth,
                        titleColor = if (place.location.isEmpty()) IcodeColors.TextMuted else IcodeColors.TextPrimary,
                        onClick = place.location.takeIf { it.isNotEmpty() }?.let { location ->
                            {
                                close()
                                gateway.dispatch(ICodeIntent.OpenFile(location, place.range.start.line + 1, place.range.start.column))
                            }
                        },
                    )
                }
                is LanguageResult.Actions -> result.actions.forEachIndexed { index, action ->
                    IcodeListRow(
                        title = action.title,
                        subtitle = action.disabled ?: action.kind,
                        titleColor = if (action.disabled != null) IcodeColors.TextMuted else if (action.preferred) IcodeColors.Accent else IcodeColors.TextPrimary,
                        onClick = if (action.disabled == null) ({ gateway.dispatch(LanguageFeatureIntent.RunAction(index)) }) else null,
                    )
                }
                is LanguageResult.Rename -> RenameFields(result)
            }
        }
    }
}

@Composable
private fun RenameFields(result: LanguageResult.Rename) {
    val gateway = LocalICodeGateway.current
    var name by rememberSaveable(result.location, result.position) { mutableStateOf(result.current) }
    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap)) {
        Text("Every use the language server finds is renamed, in every file it changes.", style = IcodeType.Caption, color = IcodeColors.TextSecondary)
        IcodeTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), placeholder = "New name")
        PrimaryButton(
            "Rename",
            onClick = { gateway.dispatch(LanguageFeatureIntent.Ask(LanguageQuery.Rename, result.location, result.position, name.trim())) },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && name.trim() != result.current,
        )
    }
}
