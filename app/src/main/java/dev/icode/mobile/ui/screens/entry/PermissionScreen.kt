package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.fillMaxSize
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
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

private val stepsSaver = listSaver<List<SystemPermission>, String>(
    save = { steps -> steps.map { it.name } },
    restore = { names -> names.map(SystemPermission::valueOf) },
)

/**
 * The permissions onboarding asks for, one at a time on this one screen. Both are optional:
 * workspaces live in iCode's own storage and need none; All Files Access is asked where the file
 * explorer's Shared needs it, in the same words ([PermissionExplanation]).
 *
 * The steps are fixed when the screen opens, so answering one does not
 * reshuffle the rest, and the position survives the trip to Android's own
 * prompt. A step moves on when that prompt closes, whatever the answer, or on
 * "Not Now".
 */
@Composable
fun PermissionScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val access = LocalICodeSnapshot.current.systemAccess
    val steps = rememberSaveable(saver = stepsSaver) { access?.onboardingSteps.orEmpty() }
    var position by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(gateway) {
        gateway.effects.collect { effect ->
            if (effect is ICodeEffect.SystemPermissionResolved && effect.permission == steps.getOrNull(position)) position++
        }
    }

    val current = steps.getOrNull(position)
    if (current == null) {
        LaunchedEffect(Unit) { actions.replace(Route.LauncherHome) }
        return
    }
    PermissionExplanation(
        permission = current,
        onAllow = { gateway.dispatch(ICodeIntent.RequestSystemPermission(current)) },
        onNotNow = {
            gateway.dispatch(ICodeIntent.DeclineSystemPermission(current))
            position++
        },
        modifier = Modifier.fillMaxSize().padding(IcodeSpacing.Space6),
        header = {
            if (steps.size > 1) {
                Text(
                    "Step ${position + 1} of ${steps.size}",
                    modifier = Modifier.padding(bottom = IcodeSpacing.Space2),
                    style = IcodeType.MonoSmall,
                )
            }
        },
    )
}
