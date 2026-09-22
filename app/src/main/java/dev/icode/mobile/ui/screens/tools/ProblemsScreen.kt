package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.ChoiceChip
import dev.icode.mobile.designsystem.DiagnosticRow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Problems panel; severity counts are derived from the diagnostics snapshot. */
@Composable
fun ProblemsScreen(actions: DemoActions) {
    val diagnostics = LocalWorkbenchSnapshot.current.diagnostics
    var filter by rememberSaveable { mutableStateOf(Severity.Error) }
    val errorCount = diagnostics.count { it.severity == Severity.Error }
    val warningCount = diagnostics.count { it.severity == Severity.Warning }
    val infoCount = diagnostics.count { it.severity == Severity.Info }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Problems",
            onBack = actions.back,
            actions = { Icon(Icons.Outlined.FilterAlt, contentDescription = "Filter", tint = IcodeColors.TextPrimary) },
        )
        Row(
            modifier = Modifier.fillMaxWidth().background(IcodeColors.Surface).padding(IcodeSpacing.Space3),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            StatusLabel(countLabel(errorCount, "Error"), IcodeColors.Error)
            StatusLabel(countLabel(warningCount, "Warning"), IcodeColors.Amber)
            StatusLabel(countLabel(infoCount, "Info", "Info"), IcodeColors.Blue)
        }
        Row(
            modifier = Modifier.padding(IcodeSpacing.Space3),
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            Severity.entries.forEach { severity ->
                ChoiceChip(severityLabel(severity), active = filter == severity) { filter = severity }
            }
        }
        LazyColumn(
            modifier = Modifier.padding(horizontal = IcodeSpacing.Space3),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            diagnostics.groupBy { it.file }.forEach { (file, items) ->
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
    }
}

private fun countLabel(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"

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
