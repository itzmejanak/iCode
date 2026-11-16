package dev.icode.mobile.data.phone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import dev.icode.mobile.data.phone.shizuku.ShizukuShell
import dev.icode.mobile.domain.phone.PhoneAct
import dev.icode.mobile.domain.phone.PhoneDirection
import dev.icode.mobile.domain.phone.PhoneException
import dev.icode.mobile.domain.phone.PhoneKey
import dev.icode.mobile.domain.phone.PhonePlaces
import dev.icode.mobile.domain.phone.PhoneRefusals
import dev.icode.mobile.domain.phone.PhoneScreen
import dev.icode.mobile.domain.phone.PhoneShot
import dev.icode.mobile.domain.phone.PhoneSwipes
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The phone through iCode's accessibility service (docs/voice-and-phone-control.md): node actions
 * first — no coordinates, no gesture to animate — a gesture where a node has none, and Shizuku's real
 * input where Android refuses both. After an act the screen is given time to settle: until a
 * window changes, then until they are quiet, never a fixed wait.
 */
internal class AndroidPhoneControl(
    private val launcher: PhoneLauncher,
    private val shizuku: ShizukuShell,
    /** iCode's own package: its windows are never acted on. */
    private val own: String,
) : PhoneControl {
    private val lock = Mutex()
    /** The latest screen as read, with its elements' nodes. */
    @Volatile private var lastRead: PhoneTree.Read? = null
    private var reads = 0

    override val on: Boolean get() = PhoneService.current.value != null

    override val latest: PhoneScreen? get() = lastRead?.screen

    override fun label(packageName: String): String = launcher.label(packageName)

    override fun appNamed(name: String): Pair<String, String>? =
        (PhonePlaces.app(name, launcher.apps()) as? PhonePlaces.Found.One)?.value?.let { it.label to it.packageName }

    private fun service(): PhoneService = PhoneService.current.value ?: throw PhoneException(OFF)

    override suspend fun screen(): PhoneScreen = lock.withLock {
        settle(since = 0L)
        withContext(Dispatchers.Default) { PhoneTree.read(service(), ++reads) }.also { lastRead = it }.screen
    }

    override suspend fun act(number: Int, act: PhoneAct): String {
        val read = lastRead ?: throw PhoneException("Read the screen first, with phone_screen")
        val element = read.screen.elements.firstOrNull { it.number == number }
            ?: throw PhoneException("Screen ${read.screen.id} has no element $number: read the screen again")
        PhoneRefusals.refusal(read.screen, element, own)?.let { throw PhoneException(it) }
        val node = read.nodes[element.path]?.takeIf { it.refresh() }
            ?: throw PhoneException("Element $number is no longer on the screen: read it again")
        val started = SystemClock.uptimeMillis()
        val done = when (act) {
            is PhoneAct.Tap -> tap(node, element.bounds.centerX, element.bounds.centerY, act.hold)
            is PhoneAct.Type -> type(node, element.bounds.centerX, element.bounds.centerY, act)
            is PhoneAct.Scroll -> scroll(node, act.direction)
        }
        settle(started)
        return done
    }

    override suspend fun tapAt(x: Int, y: Int, hold: Boolean): String {
        val started = SystemClock.uptimeMillis()
        if (!gesture(x, y, x, y, if (hold) HOLD_MILLIS else TAP_MILLIS) && !shizuku.input(if (hold) "swipe $x $y $x $y $HOLD_MILLIS" else "tap $x $y")) {
            throw PhoneException(REFUSED)
        }
        settle(started)
        return if (hold) "Held ($x, $y)" else "Tapped ($x, $y)"
    }

    override suspend fun swipe(direction: PhoneDirection): String {
        val metrics = service().resources.displayMetrics
        val (x1, y1, x2, y2) = PhoneSwipes.across(metrics.widthPixels, metrics.heightPixels, direction)
        val started = SystemClock.uptimeMillis()
        if (!gesture(x1, y1, x2, y2, SWIPE_MILLIS) && !shizuku.input("swipe $x1 $y1 $x2 $y2 $SWIPE_MILLIS")) throw PhoneException(REFUSED)
        settle(started)
        return "Scrolled the screen ${direction.name.lowercase()}"
    }

    override suspend fun key(key: PhoneKey): String {
        val started = SystemClock.uptimeMillis()
        val action = when (key) {
            PhoneKey.Back -> AccessibilityService.GLOBAL_ACTION_BACK
            PhoneKey.Home -> AccessibilityService.GLOBAL_ACTION_HOME
            PhoneKey.Recents -> AccessibilityService.GLOBAL_ACTION_RECENTS
            PhoneKey.Notifications -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            PhoneKey.QuickSettings -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        }
        if (!service().performGlobalAction(action)) throw PhoneException("Android refused ${key.word}")
        settle(started)
        return "Pressed ${key.word}"
    }

    override suspend fun openApp(name: String): String = opened { launcher.openApp(name) }

    override suspend fun openLink(link: String): String = opened { launcher.openLink(link) }

    override suspend fun openSettings(name: String, app: String?): String = opened { launcher.openSettings(name, app) }

    override suspend fun picture(): PhoneShot? = PhoneCapture.shot(service()) ?: shizuku.screenshot()

    private suspend fun opened(open: () -> String): String {
        service()
        val started = SystemClock.uptimeMillis()
        return withContext(Dispatchers.Default) { open() }.also { settle(started) }
    }

    private suspend fun tap(node: AccessibilityNodeInfo, x: Int, y: Int, hold: Boolean): String {
        val action = if (hold) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        val word = if (hold) "Held" else "Tapped"
        if (node.performAction(action)) return word
        // No action on the node: a gesture where it is, then a real touch (a view Android withholds).
        if (gesture(x, y, x, y, if (hold) HOLD_MILLIS else TAP_MILLIS)) return "$word (by touch)"
        if (shizuku.input(if (hold) "swipe $x $y $x $y $HOLD_MILLIS" else "tap $x $y")) return "$word (by Shizuku)"
        throw PhoneException(REFUSED)
    }

    private suspend fun type(node: AccessibilityNodeInfo, x: Int, y: Int, act: PhoneAct.Type): String {
        val before = node.text?.toString()?.takeUnless { node.isShowingHintText }.orEmpty()
        val text = if (act.replace) act.text else before + act.text
        val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        var typed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        // Refused: typed as keys through Shizuku, into the field once it has the cursor. `input` types ASCII only.
        if (!typed && act.text.all { it.code in PRINTABLE }) {
            tap(node, x, y, hold = false)
            typed = shizuku.input("text ${PhoneSwipes.inputText(act.text)}")
        }
        if (!typed) throw PhoneException("This field refused the text")
        if (!act.submit) return "Typed"
        delay(SUBMIT_GAP_MILLIS)
        val submitted = node.performAction(AccessibilityAction.ACTION_IME_ENTER.id) || shizuku.input("keyevent $KEYCODE_ENTER")
        return if (submitted) "Typed and submitted" else "Typed; the keyboard has no action to submit with: tap the button that sends"
    }

    private suspend fun scroll(node: AccessibilityNodeInfo, direction: PhoneDirection): String {
        val exact = when (direction) {
            PhoneDirection.Up -> AccessibilityAction.ACTION_SCROLL_UP
            PhoneDirection.Down -> AccessibilityAction.ACTION_SCROLL_DOWN
            PhoneDirection.Left -> AccessibilityAction.ACTION_SCROLL_LEFT
            PhoneDirection.Right -> AccessibilityAction.ACTION_SCROLL_RIGHT
        }
        val along = if (direction == PhoneDirection.Down || direction == PhoneDirection.Right) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val moved = (node.actionList.any { it.id == exact.id } && node.performAction(exact.id)) || node.performAction(along)
        if (!moved) throw PhoneException("It did not scroll ${direction.name.lowercase()}: it may be at its end")
        return "Scrolled ${direction.name.lowercase()}"
    }

    /** A stroke from (x1, y1) to (x2, y2) over [millis]: true once Android ran it whole. */
    private suspend fun gesture(x1: Int, y1: Int, x2: Int, y2: Int, millis: Long): Boolean {
        val service = PhoneService.current.value ?: return false
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, millis)
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { waiting ->
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (waiting.isActive) waiting.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (waiting.isActive) waiting.resume(false)
                    }
                }
                if (!service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), callback, null)) waiting.resume(false)
            }
        }
    }

    /**
     * The screen settled: a window changed after [since] — or none did within [START_MILLIS], the
     * act changing nothing — then [QUIET_MILLIS] passed with no change; at most [CAP_MILLIS] in all.
     */
    private suspend fun settle(since: Long) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            val service = PhoneService.current.value ?: return
            val now = SystemClock.uptimeMillis()
            val changed = service.lastEvent > since
            val quiet = now - service.lastEvent
            when {
                now - start >= CAP_MILLIS -> return
                !changed && now - start >= START_MILLIS -> return
                changed && quiet >= QUIET_MILLIS -> return
            }
            delay(STEP_MILLIS)
        }
    }

    private companion object {
        const val OFF = "Phone control is off: the user turns it on in iCode's Settings › Assistant › Phone control"
        const val REFUSED = "Android refused the touch: the view may be withheld from accessibility. Hand it over with phone_hand_over, or turn Shizuku on"
        const val TAP_MILLIS = 50L
        const val HOLD_MILLIS = 600L
        const val SWIPE_MILLIS = 300L
        const val SUBMIT_GAP_MILLIS = 100L
        const val START_MILLIS = 500L
        const val QUIET_MILLIS = 300L
        const val CAP_MILLIS = 3_000L
        const val STEP_MILLIS = 50L
        const val KEYCODE_ENTER = 66
        val PRINTABLE = 0x20..0x7e
    }
}
