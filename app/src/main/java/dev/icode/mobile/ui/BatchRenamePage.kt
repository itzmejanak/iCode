package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeCheckbox
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.files.BatchRename
import dev.icode.mobile.domain.files.RenamePreview
import dev.icode.mobile.domain.files.RenameRule
import dev.icode.mobile.domain.files.RenameSource

/** The rules batch rename offers (docs/workspaces-and-files.md), as its segmented control names them. */
private enum class RuleKind(val label: String) { Replace("Replace"), Add("Add"), Number("Number"), Extension("Extension") }

/**
 * Rename… for many: one rule, chosen by the segmented control, and its fields; then each old
 * name over its new one, with why a new name cannot be. Rename is offered once something changes and
 * nothing is refused; it is one step to undo.
 */
@Composable
internal fun BatchRenamePage(targets: List<ActionTarget>, folder: SheetFolder, onBack: (() -> Unit)?, onClose: () -> Unit, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val key = targets.map { it.location }
    var kind by rememberSaveable(key) { mutableStateOf(RuleKind.Replace) }
    var find by rememberSaveable(key) { mutableStateOf("") }
    var replacement by rememberSaveable(key) { mutableStateOf("") }
    var matchCase by rememberSaveable(key) { mutableStateOf(false) }
    var added by rememberSaveable(key) { mutableStateOf("") }
    var atStart by rememberSaveable(key) { mutableStateOf(false) }
    var name by rememberSaveable(key) { mutableStateOf("") }
    var start by rememberSaveable(key) { mutableStateOf("1") }
    var extension by rememberSaveable(key) { mutableStateOf("") }
    val rule = when (kind) {
        RuleKind.Replace -> RenameRule.Replace(find, replacement, matchCase)
        RuleKind.Add -> RenameRule.Add(added, atStart)
        RuleKind.Number -> RenameRule.Number(name, start.toIntOrNull() ?: 1)
        RuleKind.Extension -> RenameRule.Extension(extension)
    }
    val sources = targets.map { RenameSource(it.location, it.name, it.folder) }
    val previews = BatchRename.preview(sources, rule, folder.names - sources.map { it.name }.toSet(), folder.ignoreCase)
    IcodeBottomSheet(
        title = "Rename ${countOf(targets.size, "item")}",
        onClose = onClose,
        modifier = modifier,
        subtitle = folder.runtimePath,
        onBack = onBack,
        footer = {
            PrimaryButton(
                "Rename",
                {
                    gateway.dispatch(FilesIntent.RenameMany(key, rule))
                    onClose()
                },
                Modifier.fillMaxWidth(),
                enabled = BatchRename.ready(previews),
            )
        },
    ) {
        SegmentedControl(RuleKind.entries.map { it.label }, kind.label) { label -> kind = RuleKind.entries.first { it.label == label } }
        when (kind) {
            RuleKind.Replace -> {
                LabeledTextField("Find", find, { find = it }, literal = true)
                LabeledTextField("Replace with", replacement, { replacement = it }, literal = true)
                IcodeListRow(title = "Match case", onClick = { matchCase = !matchCase }, leading = { IcodeCheckbox(matchCase, onCheckedChange = null) })
            }
            RuleKind.Add -> {
                LabeledTextField("Text", added, { added = it }, literal = true)
                SegmentedControl(listOf(BEFORE, AFTER), if (atStart) BEFORE else AFTER) { atStart = it == BEFORE }
            }
            RuleKind.Number -> {
                LabeledTextField("Name", name, { name = it }, placeholder = "Photo")
                LabeledTextField("Start at", start, { start = it.filter(Char::isDigit) })
            }
            RuleKind.Extension -> LabeledTextField("New extension", extension, { extension = it }, placeholder = "Left blank, it goes", literal = true)
        }
        SectionLabel("Preview")
        previews.forEach { PreviewLines(it) }
    }
}

/** One item: its name now, the one it would take, and why it cannot, when it cannot. */
@Composable
private fun PreviewLines(preview: RenamePreview) {
    Column {
        MonoLine(preview.from, color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall)
        MonoLine(
            "→ ${preview.to}",
            color = when {
                preview.problem != null -> IcodeColors.Error
                preview.changes -> IcodeColors.TextPrimary
                else -> IcodeColors.TextMuted
            },
        )
        preview.problem?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
    }
}

private const val BEFORE = "Before the name"
private const val AFTER = "After the name"
