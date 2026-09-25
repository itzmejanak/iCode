package dev.icode.mobile.capture

import android.content.Intent
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.CaptureState

/**
 * How `tools/capture-figma-screens.sh` starts iCode: `--es capture_route <Route>`
 * opens that screen; with `--ez capture true` it is drawn from [CaptureFixtures]
 * instead of the device. Debug builds only; the release twin answers null.
 */
object CaptureEnvironment {
    fun requestedRoute(intent: Intent): Route? =
        intent.getStringExtra(EXTRA_ROUTE)?.let { name -> Route.entries.firstOrNull { it.name == name } }

    fun state(intent: Intent): CaptureState? =
        if (intent.getBooleanExtra(EXTRA_CAPTURE, false)) CaptureState(CaptureGateway(), CapturePreferences()) else null

    private const val EXTRA_ROUTE = "capture_route"
    private const val EXTRA_CAPTURE = "capture"
}
