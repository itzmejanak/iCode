package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.navigation.DemoActions

private val buildSpecs = listOf(
    "App Version" to "0.1.0-demo",
    "Runtime Version" to "Ubuntu 24.04 ARM64",
    "Editor Engine" to "Sora (integration planned)",
    "Min Android API" to "30 (Android 11)",
)

private val diagnosticActions = listOf("Check for Updates", "Export Diagnostics", "View Licenses", "View Source")

private val resources = listOf(
    "Documentation" to "Guides, tutorials and CLI references",
    "Release Notes" to "What's new in this demo",
    "Report Issue" to "Open a ticket on the project tracker",
)

/** About page with build specifications, diagnostics and resources. */
@Composable
fun AboutScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("About iCode", subtitle = "Application specs & diagnostic details", onBack = actions.back)
        LazyColumn(
            modifier = Modifier.padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = IcodeSpacing.Space4 + IcodeSpacing.Micro),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IcodeCard(borderColor = IcodeColors.Accent, padding = PaddingValues(IcodeSpacing.Space3)) {
                        MonoLine("[ i ]", color = IcodeColors.Accent, style = IcodeType.Mark)
                    }
                    Text(
                        "iCode Mobile IDE",
                        modifier = Modifier.padding(top = IcodeSpacing.Space2 + IcodeSpacing.Micro),
                        style = IcodeType.Heading,
                    )
                    Text("NATIVE ANDROID WORKBENCH", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent))
                }
            }
            item { SectionLabel("Build Specifications") }
            item {
                IcodeCard(Modifier.fillMaxWidth()) {
                    buildSpecs.forEach { (label, value) -> DetailRow(label, value) }
                }
            }
            item { SectionLabel("Diagnostics", Modifier.padding(top = IcodeSpacing.Space1 + IcodeSpacing.Micro)) }
            items(diagnosticActions) { action ->
                IcodeListRow(
                    card = true,
                    title = action,
                    onClick = {},
                    trailing = {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                    },
                )
            }
            item { SectionLabel("Resources", Modifier.padding(top = IcodeSpacing.Space1 + IcodeSpacing.Micro)) }
            items(resources) { (title, subtitle) ->
                IcodeListRow(
                    card = true,
                    title = title,
                    subtitle = subtitle,
                    titleStyle = IcodeType.Body,
                    titleColor = IcodeColors.Accent,
                    subtitleStyle = IcodeType.Caption,
                )
            }
        }
    }
}
