package dev.icode.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.IcodeDemoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val requestedScreen = intent.getStringExtra(EXTRA_DEMO_SCREEN)
            ?.let { name -> runCatching { DemoScreen.valueOf(name) }.getOrNull() }
            ?: DemoScreen.Splash
        val staticDemo = intent.getBooleanExtra(EXTRA_STATIC_DEMO, false)
        setContent {
            IcodeDemoApp(
                initialScreen = requestedScreen,
                staticDemo = staticDemo,
            )
        }
    }

    private companion object {
        const val EXTRA_DEMO_SCREEN = "demo_screen"
        const val EXTRA_STATIC_DEMO = "demo_static"
    }
}
