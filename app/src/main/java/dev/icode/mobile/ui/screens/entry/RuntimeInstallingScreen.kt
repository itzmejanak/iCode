package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen
import kotlinx.coroutines.delay

/** Runtime installation progress; tapping the log card shows the failure frame. */
@Composable
fun RuntimeInstallingScreen(actions: DemoActions) {
    LaunchedEffect(actions.staticDemo) {
        if (!actions.staticDemo) {
            delay(1_800)
            actions.navigate(DemoScreen.PermissionExplanation)
        }
    }
    Column(Modifier.fillMaxSize().padding(IcodeSpacing.Space6)) {
        PageHeading(
            title = "Installing Runtime",
            description = "Preparing the isolated Linux container subsystem for building and running code.",
        )
        Spacer(Modifier.weight(1f))
        ProgressTrack(0.705f, Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoLine("70.5% COMPLETE", color = IcodeColors.Accent, style = IcodeType.MonoSmall)
            MonoLine("847 MB / 1.2 GB", style = IcodeType.MonoSmall)
        }
        IcodeCard(
            modifier = Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space4 + IcodeSpacing.Micro),
            onClick = { actions.navigate(DemoScreen.RuntimeError) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
                MonoLine("Extracting Ubuntu ARM64 rootfs...")
            }
        }
        Spacer(Modifier.weight(1.35f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            Icon(Icons.Outlined.Storage, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconMd))
            Text("Allocated: 2.4 GB | Available storage: 45.2 GB", style = IcodeType.Caption)
        }
    }
}
