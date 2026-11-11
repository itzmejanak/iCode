package dev.icode.mobile.ui.phone

import android.content.Context
import android.view.View
import android.view.WindowManager
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone service's windows, when Android takes the accessibility token back under them. */
class OverlayWindowsTest {
    private val calls = mutableListOf<String>()
    private var revokedTimes = 0

    /** A window manager that adds until [tokenGone], then throws what Android throws. */
    private var tokenGone = false
    private val manager: WindowManager = Proxy.newProxyInstance(
        WindowManager::class.java.classLoader,
        arrayOf(WindowManager::class.java),
        InvocationHandler { _, method, _ ->
            calls += method.name
            if (tokenGone && method.name == "addView") throw WindowManager.BadTokenException("token null is not valid; is your activity running?")
            if (method.name == "removeViewImmediate" && calls.count { it == "removeViewImmediate" } > 1 && tokenGone) throw IllegalArgumentException("not attached")
            null
        },
    ) as WindowManager

    private val windows = OverlayWindows(manager) { revokedTimes++ }
    private val view = View::class.java.getConstructor(Context::class.java).newInstance(null as Context?)
    private val params = WindowManager.LayoutParams()

    @Test fun `a window is added while the token holds`() {
        assertTrue(windows.add(view, params))
        assertTrue(windows.usable)
        assertEquals(listOf("addView"), calls)
        assertEquals(0, revokedTimes)
    }

    @Test fun `a token taken back is told once, never thrown, and nothing is added after`() {
        tokenGone = true

        assertFalse(windows.add(view, params))
        assertFalse(windows.usable)
        assertEquals(1, revokedTimes)

        // Every later call is a no-op: the overlays are being put away, not retried.
        assertFalse(windows.add(view, params))
        windows.update(view, params)
        assertEquals(1, revokedTimes)
        assertEquals(listOf("addView"), calls)
    }

    @Test fun `putting away a window that was never added, or is gone, is not an error`() {
        tokenGone = true
        windows.remove(view)
        windows.remove(view)
        assertEquals(listOf("removeViewImmediate", "removeViewImmediate"), calls)
    }
}
