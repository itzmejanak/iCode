package dev.icode.mobile.data.phone

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.Display
import dev.icode.mobile.domain.phone.PhoneShot
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A picture of the screen through accessibility (`takeScreenshot`, Android 11;
 * docs/voice-and-phone-control.md), made small for the model: JPEG, its longest side at most
 * [PhoneShot.MAX_SIDE]. Android allows one every 333 ms and none of a secure window; iCode's own
 * overlays are hidden for it below Android 14, where a capture cannot be taken beneath them.
 */
internal object PhoneCapture {
    @Volatile private var last = 0L

    suspend fun shot(service: PhoneService): PhoneShot? {
        val wait = INTERVAL_MILLIS - (SystemClock.uptimeMillis() - last)
        if (wait > 0) delay(wait)
        val hide = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        if (hide) service.overlays?.hideForCapture()
        try {
            val result = take(service) ?: return null
            last = SystemClock.uptimeMillis()
            val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
            result.hardwareBuffer.close()
            val full = hardware?.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            return shrink(full)
        } finally {
            if (hide) service.overlays?.showAfterCapture()
        }
    }

    /** [full] at the model's size, as JPEG. */
    fun shrink(full: Bitmap): PhoneShot {
        val scale = PhoneShot.scaleFor(full.width, full.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(full, (full.width * scale).toInt(), (full.height * scale).toInt(), true) else full
        val jpeg = ByteArrayOutputStream().use { out ->
            small.compress(Bitmap.CompressFormat.JPEG, PhoneShot.QUALITY, out)
            out.toByteArray()
        }
        return PhoneShot(jpeg, small.width, small.height, scale)
    }

    private suspend fun take(service: AccessibilityService): AccessibilityService.ScreenshotResult? =
        suspendCancellableCoroutine { waiting ->
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        if (waiting.isActive) waiting.resume(screenshot) else screenshot.hardwareBuffer.close()
                    }

                    // A secure window, or too soon: no picture, and the caller says so.
                    override fun onFailure(errorCode: Int) {
                        if (waiting.isActive) waiting.resume(null)
                    }
                },
            )
        }

    /** `AccessibilityService.ACCESSIBILITY_TAKE_SCREENSHOT_REQUEST_INTERVAL_TIMES_MS` (hidden): one capture per this. */
    private const val INTERVAL_MILLIS = 333L
}
