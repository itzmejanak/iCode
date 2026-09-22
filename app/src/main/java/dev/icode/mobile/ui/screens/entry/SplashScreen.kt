package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LocalTitleAction
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen
import kotlinx.coroutines.delay

/** Splash frame; the logomark is the demo entry point to the screen catalog. */
@Composable
fun SplashScreen(actions: DemoActions) {
    val openCatalog = LocalTitleAction.current
    LaunchedEffect(actions.staticDemo) {
        if (!actions.staticDemo) {
            delay(1_100)
            actions.navigate(DemoScreen.RuntimeInstalling)
        }
    }
    val bottomInset = IcodeSpacing.Space6 + IcodeSpacing.Space5 // 44 dp per Figma
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(IcodeSpacing.Space6))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4 + IcodeSpacing.Micro),
        ) {
            Box(
                modifier = Modifier
                    .clickable(onClick = openCatalog)
                    .padding(IcodeSpacing.Micro),
            ) {
                IcodeCard(borderColor = IcodeColors.Accent, padding = PaddingValues(IcodeSpacing.Space3)) {
                    Text("[ i ]", style = IcodeType.Logomark)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("iCode", style = IcodeType.Display)
                Text("MOBILE IDE", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent))
            }
        }
        Text("Code anywhere.", style = IcodeType.Body.copy(color = IcodeColors.TextMuted))
    }
}
