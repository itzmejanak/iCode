package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.KeyChip
import dev.icode.mobile.designsystem.LabeledField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.navigation.DemoActions

private val capabilityChips = listOf("completion", "hover", "diagnostics", "formatting")

/** Detail page for the TypeScript language service. */
@Composable
fun LanguageDetailScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "TypeScript Service",
            onBack = actions.back,
            actions = { StatusLabel("Running", IcodeColors.Green) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4 + IcodeSpacing.Micro),
        ) {
            SectionLabel("Configuration")
            LabeledField("Command", "typescript-language-server --stdio")
            LabeledField("Root Markers", ".tsconfig.json, package.json")
            LabeledField("File Patterns", "*.ts, *.tsx, *.js, *.jsx")
            SectionLabel("Initialization Options", Modifier.padding(top = IcodeSpacing.Space1))
            IcodeCard(Modifier.fillMaxWidth(), padding = PaddingValues(IcodeSpacing.None)) {
                CodeSurface {
                    MonoLine(
                        "{\n  \"preferences\": {\n    \"importModuleSpecifier\": \"non-relative\"\n  }\n}",
                        color = IcodeColors.Amber,
                        maxLines = Int.MAX_VALUE,
                    )
                }
            }
            SectionLabel("Status", Modifier.padding(top = IcodeSpacing.Space1))
            IcodeCard(Modifier.fillMaxWidth()) {
                DetailRow("Process ID", "24018")
                DetailRow("Uptime", "1h 45m")
                Text("Capabilities", style = IcodeType.Caption, modifier = Modifier.padding(top = IcodeSpacing.Space1 + IcodeSpacing.Micro))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1 + IcodeSpacing.Micro),
                ) {
                    capabilityChips.forEach { KeyChip(it) }
                }
            }
            SecondaryButton("Restart Server", {}, Modifier.fillMaxWidth())
            DangerButton("Remove Service", {}, Modifier.fillMaxWidth())
        }
    }
}
