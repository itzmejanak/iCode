package dev.icode.mobile.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.PhoneIntent
import dev.icode.mobile.domain.phone.PhoneSetup
import dev.icode.mobile.domain.phone.PhoneSetupStep
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › Phone control (docs/phone-agent-plan.md §5.9, P7): off by default; what is
 * read and where it goes; the steps that turn it on, or one tap with Shizuku; the bubble; turning off.
 */
@Composable
fun PhoneControlScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val access = LocalICodeSnapshot.current.phone
    val context = LocalContext.current
    RefreshOnResume { gateway.dispatch(PhoneIntent.Refresh) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Phone control", subtitle = PhoneSetup.status(access), onBack = actions.back)
        IcodePageColumn(Modifier.weight(1f)) {
            IcodeSection("What it does") {
                Text(
                    "While it is on, the assistant in a phone chat can read the screen of the app in front and tap, type and " +
                        "scroll in it. It asks before it acts in each app, and hands passwords, payments and Android's " +
                        "permission dialogs to you. What it reads — the screen's text, and a picture of the screen when text " +
                        "is not enough — is sent to the provider of the chat's model.",
                    style = IcodeType.Body.copy(color = IcodeColors.TextSecondary),
                )
            }
            if (access.service) {
                IcodeSection("On") {
                    IcodeListRow(
                        title = "Show the bubble",
                        subtitle = "Over other apps, with the edge glow while the assistant acts",
                        trailing = { IcodeToggle(access.bubble) { gateway.dispatch(PhoneIntent.ShowBubble(it)) } },
                    )
                    SecondaryButton("Turn off", { gateway.dispatch(PhoneIntent.TurnOff) }, Modifier.fillMaxWidth(), color = IcodeColors.Error)
                }
            } else {
                IcodeSection("Turn on") {
                    if (PhoneSetup.oneTap(access)) {
                        Text("Shizuku is ready: iCode can allow restricted settings and turn its own service on.", style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
                        PrimaryButton("Turn on with Shizuku", { gateway.dispatch(PhoneIntent.TurnOnWithShizuku) }, Modifier.fillMaxWidth())
                    }
                    PhoneSetup.lines(access, Build.VERSION.SDK_INT).forEachIndexed { index, line ->
                        IcodeListRow(
                            card = true,
                            selected = line.current,
                            title = "${index + 1}. ${line.step.title}",
                            subtitle = line.step.detail,
                            subtitleStyle = IcodeType.Caption,
                            onClick = { context.openSetupPage(line.step) },
                            trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary) },
                        )
                    }
                }
            }
            IcodeSection("Shizuku") {
                IcodeListRow(
                    card = true,
                    onClick = { actions.navigate(Route.ShizukuSetup) },
                    content = {
                        Column(Modifier.weight(1f)) {
                            Text("Shizuku", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                            Text("Optional: one-tap setup, and real touches where accessibility is refused", style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
                        }
                    },
                    trailing = {
                        StatusLabel(access.shizuku.label, if (access.shizukuReady) IcodeColors.Green else IcodeColors.TextSecondary)
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                    },
                )
            }
        }
    }
}

/** Android's page for [step]: iCode's App info for restricted settings, Accessibility for the service. */
private fun Context.openSetupPage(step: PhoneSetupStep) {
    val page = when (step) {
        PhoneSetupStep.Restricted ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        PhoneSetupStep.Service -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
    openFirst(page, Intent(Settings.ACTION_SETTINGS))
}

/** Starts the first of [pages] this phone has. */
internal fun Context.openFirst(vararg pages: Intent) {
    for (page in pages) {
        try {
            startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // This phone lacks the page; try the next.
        }
    }
}

/** [refresh] each time the page comes back to the front: from Android's settings, or Shizuku. */
@Composable
internal fun RefreshOnResume(refresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
