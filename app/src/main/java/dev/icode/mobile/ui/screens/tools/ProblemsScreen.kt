package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.DiagnosticRow
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.screens.workspace.fileTypeIcon
import dev.icode.mobile.ui.screens.workspace.fileTypeTint

/** Problems view within the tools panel; severity counts include every reported row. */
@Composable
fun ProblemsPanelContent(
    filter: Severity,
    onFilterChange: (Severity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val diagnostics = LocalICodeSnapshot.current.diagnostics
    val errorCount = diagnostics.count { it.severity == Severity.Error }
    val warningCount = diagnostics.count { it.severity == Severity.Warning }
    val infoCount = diagnostics.count { it.severity == Severity.Info }
    val visible = visibleDiagnostics(diagnostics, filter)
    val counts = mapOf(Severity.Error to errorCount, Severity.Warning to warningCount, Severity.Info to infoCount)
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = IcodeSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        title = if (diagnostics.isEmpty()) "No diagnostics reported" else "No ${severityLabel(filter).lowercase()} reported",
                        description = if (diagnostics.isEmpty()) {
                            "Build & Run lists a build's errors here. Language servers' diagnostics are not connected yet."
                        } else {
                            "Other severity filters may still have reported diagnostics."
                        },
                        modifier = Modifier.padding(top = IcodeSpacing.Space4),
                    )
                }
            }
            visible.groupBy { it.file }.forEach { (file, items) ->
                item { FileHeader(file, items.size) }
                items(items) { diagnostic ->
                    DiagnosticRow(
                        tone = severityTone(diagnostic.severity),
                        icon = severityIcon(diagnostic.severity),
                        message = diagnostic.message,
                        location = diagnostic.location,
                        source = diagnostic.source,
                    )
                }
            }
        }
        // One band for the counts and the filter: the bottom bar, as the
        // editor's (owner's design); each count in its severity's colour.
        IcodeBottomBar(
            items = Severity.entries.map { severity ->
                BottomBarItem(
                    severityLabel(severity),
                    onClick = { onFilterChange(severity) },
                    active = filter == severity,
                    count = counts.getValue(severity),
                    countTone = severityTone(severity),
                )
            },
        )
    }
}

internal fun visibleDiagnostics(diagnostics: List<Diagnostic>, severity: Severity): List<Diagnostic> =
    diagnostics.filter { it.severity == severity }


private fun severityLabel(severity: Severity): String = when (severity) {
    Severity.Error -> "Errors"
    Severity.Warning -> "Warnings"
    Severity.Info -> "Info"
}

/** A file's problems under its type icon, its name, its folder and how many there are, as VS Code's Problems view heads them. */
@Composable
private fun FileHeader(file: String, count: Int) {
    val type = FileTypes.of(file.substringAfterLast('/'))
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        Icon(fileTypeIcon(type), contentDescription = null, tint = fileTypeTint(type), modifier = Modifier.size(IcodeSizes.IconSm))
        Text(file.substringAfterLast('/'), style = IcodeType.Mono.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        Text(
            file.substringBeforeLast('/', ""),
            modifier = Modifier.weight(1f),
            style = IcodeType.MonoSmall.copy(color = IcodeColors.TextMuted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(countOf(count, "problem"), style = IcodeType.MonoSmall, maxLines = 1, softWrap = false)
    }
}

/** VS Code's Problems view icons: a circled cross, a triangle, a circled i. */
private fun severityIcon(severity: Severity): ImageVector = when (severity) {
    Severity.Error -> Icons.Outlined.ErrorOutline
    Severity.Warning -> Icons.Outlined.WarningAmber
    Severity.Info -> Icons.Outlined.Info
}

private fun severityTone(severity: Severity): IcodeTone = when (severity) {
    Severity.Error -> IcodeTone.Error
    Severity.Warning -> IcodeTone.Warning
    Severity.Info -> IcodeTone.Info
}
