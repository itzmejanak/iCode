package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/** Runtime installation failure with checksum diagnostics and recovery actions. */
@Composable
fun RuntimeErrorScreen(actions: DemoActions) {
    Column(Modifier.fillMaxSize().padding(IcodeSpacing.Space6)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            IcodeCard(borderColor = IcodeColors.Error, padding = PaddingValues(IcodeSpacing.Space2)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = IcodeColors.Error, modifier = Modifier.size(IcodeSizes.IconMd))
            }
            Text("Installation Failed", style = IcodeType.PageTitle)
        }
        Text(
            "Checksum verification failed. The download may be corrupted or interrupted.",
            modifier = Modifier.padding(top = IcodeSpacing.Space2 + IcodeSpacing.Micro),
            style = IcodeType.Body,
            color = IcodeColors.TextSecondary,
        )
        IcodeCard(
            modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space6),
            padding = PaddingValues(IcodeSpacing.None),
        ) {
            Column {
                SectionLabel("Technical Details", Modifier.padding(IcodeSpacing.Space3))
                Divider()
                Column(
                    Modifier.padding(IcodeSpacing.Space3),
                    verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
                ) {
                    MonoLine("ERR_HASH_MISMATCH: rootfs.tar.gz", color = IcodeColors.Error, style = IcodeType.MonoSmall)
                    MonoLine("Expected: 8e5fd22b7a...", color = IcodeColors.TextMuted, style = IcodeType.MonoSmall)
                    MonoLine("Received: 3fa9120de4...", color = IcodeColors.TextMuted, style = IcodeType.MonoSmall)
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = IcodeSpacing.Space2 + IcodeSpacing.Micro),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconMd))
            Text("Storage stable: 45.2 GB free space.", style = IcodeType.Caption)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Retry Installation", { actions.navigate(DemoScreen.RuntimeInstalling) }, Modifier.fillMaxWidth())
        SecondaryButton("View Diagnostics", { actions.navigate(DemoScreen.About) }, Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space3))
    }
}
