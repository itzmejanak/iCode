package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.PageHeading
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** What one step of the permission screen says. */
private class PermissionStep(
    val title: String,
    val description: String,
    val reasons: List<String>,
    val allowLabel: String,
)

private fun stepOf(permission: SystemPermission): PermissionStep = when (permission) {
    SystemPermission.Notifications -> PermissionStep(
        title = "Notifications",
        description = "Terminal sessions keep running after you leave iCode. Android asks whether iCode may show them in the notification drawer.",
        reasons = listOf(
            "Each running session has its own notification: its name, its workspace and what it is running.",
            "Exit a session, or keep the phone awake for it, right from its notification.",
            "Tapping a notification opens that exact terminal.",
            "Sessions run either way. Without this, Android lists them only in its Task Manager.",
        ),
        allowLabel = "Allow Notifications",
    )
    SystemPermission.Battery -> PermissionStep(
        title = "Battery Optimization",
        description = "While the phone is idle, battery optimization cuts apps off from the network and lets the processor sleep. Exempting iCode lets long commands such as installs, builds and servers keep going.",
        reasons = listOf(
            "Running sessions keep their network access while the phone is idle.",
            "A session you keep awake from its notification can hold the processor awake.",
            "Some phone makers pause background apps on their own; this setting does not change that.",
            "You remain in control. Change it at any time in Terminal Settings.",
        ),
        allowLabel = "Allow Background Use",
    )
}

private val stepsSaver = listSaver<List<SystemPermission>, String>(
    save = { steps -> steps.map { it.name } },
    restore = { names -> names.map(SystemPermission::valueOf) },
)

/**
 * The permissions onboarding asks for, one at a time on this one screen. Both are optional:
 * workspaces live in iCode's own storage and need none.
 *
 * The steps are fixed when the screen opens, so answering one does not
 * reshuffle the rest, and the position survives the trip to Android's own
 * prompt. A step moves on when that prompt closes, whatever the answer, or on
 * "Not Now".
 */
@Composable
fun PermissionScreen(actions: NavigationActions) {
    val gateway = LocalWorkbenchGateway.current
    val access = LocalWorkbenchSnapshot.current.systemAccess
    val steps = rememberSaveable(saver = stepsSaver) { access?.onboardingSteps.orEmpty() }
    var position by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(gateway) {
        gateway.effects.collect { effect ->
            if (effect is WorkbenchEffect.SystemPermissionResolved && effect.permission == steps.getOrNull(position)) position++
        }
    }

    val current = steps.getOrNull(position)
    if (current == null) {
        LaunchedEffect(Unit) { actions.replace(Route.LauncherHome) }
        return
    }
    val step = stepOf(current)
    Column(Modifier.fillMaxSize().padding(IcodeSpacing.Space6)) {
        if (steps.size > 1) {
            Text(
                "Step ${position + 1} of ${steps.size}",
                modifier = Modifier.padding(bottom = IcodeSpacing.Space2),
                style = IcodeType.MonoSmall,
            )
        }
        PageHeading(title = step.title, description = step.description)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            step.reasons.forEachIndexed { index, text ->
                Row(
                    Modifier.padding(vertical = IcodeSpacing.Space2),
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4),
                ) {
                    IconBadge(
                        text = "${index + 1}",
                        tint = IcodeColors.Accent,
                        size = IcodeSizes.IconXl,
                        background = IcodeColors.SurfaceElevated,
                        style = IcodeType.MonoSmall,
                    )
                    Text(text, modifier = Modifier.weight(1f), style = IcodeType.Body)
                }
            }
        }
        PrimaryButton(
            step.allowLabel,
            { gateway.dispatch(WorkbenchIntent.RequestSystemPermission(current)) },
            Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            "Not Now",
            {
                gateway.dispatch(WorkbenchIntent.DeclineSystemPermission(current))
                position++
            },
            Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space3),
        )
    }
}
