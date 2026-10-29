package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.OnboardingRoute
import dev.icode.mobile.ui.LocalICodeSnapshot
import kotlinx.coroutines.delay

/** Splash frame. */
@Composable
fun SplashScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    // Onboarding's order lives in OnboardingRoute, so this screen and the
    // runtime installer cannot disagree about it. Each step replaces this one
    // rather than stacking on it, so Back from the launcher leaves the app
    // instead of walking back through onboarding.
    LaunchedEffect(actions.captureMode, state.runtime, state.systemAccess, state.runtimeTools.offerPending, state.web.consentPending) {
        if (actions.captureMode) return@LaunchedEffect
        val destination = OnboardingRoute.next(state.runtime, state.systemAccess, state.runtimeTools.offerPending, state.web.consentPending)
            ?: return@LaunchedEffect
        delay(SPLASH_MINIMUM_MILLIS)
        actions.replace(destination)
    }

    val bottomInset = IcodeSpacing.Space6 + IcodeSpacing.Space5 // 44 dp per the design
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
                modifier = Modifier.padding(IcodeSpacing.Micro),
            ) {
                IcodeBrandMark(Modifier.size(IcodeSizes.Tile))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("iCode", style = IcodeType.Display)
                Text("MOBILE IDE", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent))
            }
        }
        Text("Code anywhere.", style = IcodeType.Body.copy(color = IcodeColors.TextMuted))
    }
}

/** Long enough for the wordmark to register, short enough not to be a gate. */
private const val SPLASH_MINIMUM_MILLIS = 700L
