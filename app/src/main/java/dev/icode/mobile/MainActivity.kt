package dev.icode.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.icode.mobile.capture.CaptureEnvironment
import dev.icode.mobile.data.runtime.TerminalSessionManager
import dev.icode.mobile.domain.BackgroundPlace
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.Arrival
import dev.icode.mobile.ui.IcodeApp

class MainActivity : ComponentActivity() {
    /**
     * Whether the app knows which screen to open.
     *
     * Until it does, the system splash is held on screen. That replaces the
     * second, in-app splash: one brand moment, ending exactly when there is
     * something real to show instead of after a timer.
     */
    private var readyToRender by mutableStateOf(false)

    /** What a notification or App info asked to show; cleared once shown. */
    private var arrival by mutableStateOf<Arrival?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !readyToRender }
        enableEdgeToEdge()
        // Only a debug build answers the capture tool's extras; a release build always starts at the splash.
        val requestedScreen = CaptureEnvironment.requestedRoute(intent) ?: Route.Splash
        val capture = CaptureEnvironment.state(intent)
        // A recreated activity has already acted on the intent it started with.
        if (savedInstanceState == null) arrival = arrivalIn(intent)
        setContent {
            IcodeApp(
                initialScreen = requestedScreen,
                capture = capture,
                onReadyToRender = { readyToRender = true },
                arrival = arrival,
                onArrived = { arrival = null },
            )
        }
    }

    // Whether the user can see a session decides whether it may alert them.
    override fun onStart() {
        super.onStart()
        TerminalSessionManager.get(this).setScreenVisible(true)
    }

    override fun onStop() {
        TerminalSessionManager.get(this).setScreenVisible(false)
        super.onStop()
    }

    /** A notification tapped while this screen already exists. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        arrivalIn(intent)?.let { arrival = it }
    }

    private fun arrivalIn(intent: Intent?): Arrival? {
        val asked = intent ?: return null
        return when (asked.action) {
            ACTION_SHOW_TERMINAL_SESSION -> asked.getStringExtra(EXTRA_TERMINAL_SESSION_ID)?.let(Arrival::TerminalSession)
            ACTION_SHOW_WORK -> BackgroundPlace.decode(asked.getStringExtra(EXTRA_WORK_PLACE))?.let(Arrival::Work)
            else -> null
        }
    }

    companion object {
        /** Opens iCode on one terminal session: its workspace, the Terminal panel, that tab. */
        const val ACTION_SHOW_TERMINAL_SESSION = "dev.icode.mobile.action.SHOW_TERMINAL_SESSION"
        const val EXTRA_TERMINAL_SESSION_ID = "terminal_session"

        /** Opens iCode on a piece of background work, from its notification: [EXTRA_WORK_PLACE] says where. */
        const val ACTION_SHOW_WORK = "dev.icode.mobile.action.SHOW_WORK"
        const val EXTRA_WORK_PLACE = "work_place"
    }
}
