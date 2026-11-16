package dev.icode.mobile.ui.phone

import android.view.View
import android.view.WindowManager
import dev.icode.mobile.data.log.BackendLog

/**
 * The window manager as the phone service's overlays may use it (docs/voice-and-phone-control.md).
 *
 * Their windows are `TYPE_ACCESSIBILITY_OVERLAY`, which Android allows only through the token of the
 * accessibility connection. That token is the user's to take back: turning phone control off in
 * Settings, or the service turning itself off, revokes it at once, while the service hears of it —
 * `onUnbind`, which detaches the overlays — a moment later. In between, the assistant's state may
 * still change on the main thread, and adding a window then throws `BadTokenException` ("token null is
 * not valid"), which nothing in the overlays can have prevented and which would end the app.
 *
 * So adding a window is a thing that can fail, here and only here: a revoked token is told once, to
 * [revoked], which puts the overlays away; from then on every call is a no-op, until the service
 * connects again and makes new overlays.
 */
internal class OverlayWindows(val manager: WindowManager, private val revoked: () -> Unit) {
    /** False once Android has taken the token back. */
    var usable = true
        private set

    /** [view] in a window of [params]; false when it was not added, because the token is gone. */
    fun add(view: View, params: WindowManager.LayoutParams): Boolean {
        if (!usable) return false
        return try {
            manager.addView(view, params)
            true
        } catch (gone: WindowManager.BadTokenException) {
            revoke(gone)
            false
        } catch (gone: WindowManager.InvalidDisplayException) {
            revoke(gone)
            false
        }
    }

    /** [view]'s window put away, if it is one: never an error, whether or not it was ever added. */
    fun remove(view: View) {
        runCatching { manager.removeViewImmediate(view) }
    }

    /** [view]'s window laid out again as [params], while it is on screen. */
    fun update(view: View, params: WindowManager.LayoutParams) {
        if (!usable || !view.isAttachedToWindow) return
        try {
            manager.updateViewLayout(view, params)
        } catch (gone: WindowManager.BadTokenException) {
            revoke(gone)
        } catch (notThere: IllegalArgumentException) {
            // Put away meanwhile: nothing to lay out.
        }
    }

    private fun revoke(cause: Exception) {
        if (!usable) return
        usable = false
        BackendLog.w(TAG, "Phone control's windows lost their token (${cause.javaClass.simpleName}): the overlays are put away")
        revoked()
    }

    private companion object {
        const val TAG = "PhoneOverlays"
    }
}
