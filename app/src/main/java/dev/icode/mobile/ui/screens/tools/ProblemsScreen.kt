package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.DiagnosticRow
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeBottomBar
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.ui.LocalICodeSnapshot

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
                item {
                    Text(
                        "⌄  $file",
                        style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(top = IcodeSpacing.Space2),
                    )
                }
                items(items) { diagnostic ->
                    DiagnosticRow(
                        tone = severityTone(diagnostic.severity),
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

private fun severityTone(severity: Severity): IcodeTone = when (severity) {
    Severity.Error -> IcodeTone.Error
    Severity.Warning -> IcodeTone.Warning
    Severity.Info -> IcodeTone.Info
}
