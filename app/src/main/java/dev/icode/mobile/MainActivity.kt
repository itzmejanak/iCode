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
import dev.icode.mobile.data.notify.NotificationLink
import dev.icode.mobile.data.runtime.TerminalSessionManager
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

    /** What a notification's tap ([NotificationLink]) or a sign-in asks to show. */
    private fun arrivalIn(intent: Intent?): Arrival? {
        if (intent?.action == ACTION_SIGNED_IN) return intent.getStringExtra(EXTRA_SIGN_IN)?.let(Arrival::SignedIn)
        return when (val link = NotificationLink.read(intent)) {
            is NotificationLink.Session -> Arrival.TerminalSession(link.id)
            is NotificationLink.Work -> Arrival.Work(link.place)
            is NotificationLink.Notice -> Arrival.Notice(link.id, link.act)
            NotificationLink.App, null -> null
        }
    }

    companion object {
        /** A remote MCP server's sign-in came back ([SignInReturnActivity]): [EXTRA_SIGN_IN] is the browser's answer. */
        const val ACTION_SIGNED_IN = "dev.icode.mobile.action.SIGNED_IN"
        const val EXTRA_SIGN_IN = "sign_in"
    }
}
