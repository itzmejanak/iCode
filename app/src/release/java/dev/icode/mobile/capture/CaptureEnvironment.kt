package dev.icode.mobile.capture

import android.content.Intent
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.CaptureState

/** A release build ignores the capture tool: no route from outside, no fixtures (the debug twin has them). */
object CaptureEnvironment {
    @Suppress("UNUSED_PARAMETER")
    fun requestedRoute(intent: Intent): Route? = null

    @Suppress("UNUSED_PARAMETER")
    fun state(intent: Intent): CaptureState? = null
}
