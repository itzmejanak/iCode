package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

private val runtimeDetails = listOf(
    "Architecture" to "aarch64",
    "Runtime Home" to ".../files/usr/runtime",
    "Installed Packages" to "247 packages",
    "Disk Usage" to "1.8 GB of 16 GB",
    "Uptime" to "3h 42m",
    "PRoot Version" to "5.3.0",
)

/** Runtime environment health, technical details and repair actions. */
@Composable
fun RuntimeSettingsScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Runtime Environment", onBack = actions.back)
        Column(
            modifier = Modifier.fillMaxSize().padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4 + IcodeSpacing.Micro),
        ) {
            NoticeCard(
                tone = IcodeTone.Success,
                title = "Runtime Healthy",
                icon = Icons.Outlined.Check,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ubuntu 22.04 ARM64 via PRoot", style = IcodeType.Caption)
            }
            SectionLabel("Technical Details")
            IcodeCard(Modifier.fillMaxWidth()) {
                runtimeDetails.forEach { (label, value) -> DetailRow(label, value) }
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("Repair Runtime", {}, Modifier.fillMaxWidth())
            Text(
                "Reinstall runtime files. Preserves home directory and installed tools.",
                style = IcodeType.Caption,
            )
            DangerButton("Reset Runtime", { actions.navigate(DemoScreen.DeleteConfirmation) }, Modifier.fillMaxWidth())
            Text(
                "Remove everything including installed tools and home directory.",
                style = IcodeType.Caption,
            )
        }
    }
}
