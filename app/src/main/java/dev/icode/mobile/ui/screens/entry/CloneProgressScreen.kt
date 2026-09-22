package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen
import kotlinx.coroutines.delay

/** Clone progress with streaming Git output; auto-advances into the explorer. */
@Composable
fun CloneProgressScreen(actions: DemoActions) {
    LaunchedEffect(actions.staticDemo) {
        if (!actions.staticDemo) {
            delay(2_000)
            actions.navigate(DemoScreen.WorkspaceExplorer)
        }
    }
    Column(Modifier.fillMaxSize().padding(IcodeSpacing.Space6)) {
        Text("GIT CLONE IN PROGRESS", style = IcodeType.Label.copy(color = IcodeColors.Accent))
        Text(
            "compiler-core",
            style = IcodeType.PageTitle,
            modifier = Modifier.padding(top = IcodeSpacing.Space2 + IcodeSpacing.Micro),
        )
        MonoLine(
            text = "Dest: ~/projects/compiler-core",
            modifier = Modifier.padding(top = IcodeSpacing.Space1 + IcodeSpacing.Micro),
            style = IcodeType.MonoSmall,
        )
        Divider()
        ProgressTrack(0.67f, Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space5))
        IcodeCard(Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space4)) {
            Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                MonoLine("$ git clone https://github.com/icode...", color = IcodeColors.TextSecondary)
                MonoLine("> Cloning into 'compiler-core'...", color = IcodeColors.Green)
                MonoLine("> Receiving objects: 67%... 234/350", color = IcodeColors.Accent)
            }
        }
        Spacer(Modifier.weight(1f))
        SecondaryButton("Cancel Clone", actions.back, Modifier.fillMaxWidth())
    }
}
