package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen
import kotlinx.coroutines.delay

private val demoPullOutput = listOf(
    "$ git pull origin main",
    "From github.com:icode/icode-subsystem",
    "* branch          main      -> FETCH_HEAD",
    "Updating 2fd9b31..a8d29b0",
    "Fast-forward",
)

/** Pull/fetch progress with streaming Git output; auto-advances to commit state. */
@Composable
fun PullProgressScreen(actions: DemoActions) {
    LaunchedEffect(actions.staticDemo) {
        if (!actions.staticDemo) {
            delay(2_200)
            actions.navigate(DemoScreen.CommitPush)
        }
    }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Syncing Repository")
        Column(Modifier.padding(IcodeSpacing.Space5)) {
            Text(
                "Pulling from origin main...",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = IcodeType.Title,
            )
            ProgressTrack(0.52f, Modifier.fillMaxWidth().padding(vertical = IcodeSpacing.Space4))
            IcodeCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    demoPullOutput.forEach { line -> MonoLine(line, style = IcodeType.MonoSmall) }
                    MonoLine("src/utils/token-utils.ts | 12 ++++++++----", color = IcodeColors.Green, style = IcodeType.MonoSmall)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        SecondaryButton("Cancel Operation", actions.back, Modifier.fillMaxWidth().padding(IcodeSpacing.Space5))
    }
}
