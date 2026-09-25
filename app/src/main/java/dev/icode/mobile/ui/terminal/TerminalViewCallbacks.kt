package dev.icode.mobile.ui.terminal

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * How the terminal view behaves inside iCode.
 *
 * Input is taken as characters with no suggestions (a password-style field
 * every keyboard leaves alone: no autocorrect rewriting commands, nothing
 * typed learned into the keyboard's dictionary). A tap shows the keyboard; a
 * long press opens the URL under it, or starts selecting text anywhere else.
 */
internal class TerminalViewCallbacks(private val keys: TerminalKeys) : TerminalViewClient {
    var view: TerminalView? = null
    var onScale: (Float) -> Unit = {}
    var onUrl: (String) -> Unit = {}

    /** Where the last long press was, in the view: a selection starts there, and its More… menu opens there. */
    var onLongPressAt: (x: Float, y: Float) -> Unit = { _, _ -> }

    /** Enter on a session whose shell has ended closes its tab, as the screen says. */
    var onEndedEnter: () -> Unit = {}

    /**
     * The view accumulates a pinch and hands the running factor here. It is
     * applied in steps, as Termux does, rather than re-laying out the terminal
     * on every movement; returning 1 starts the next step.
     */
    override fun onScale(scale: Float): Float {
        if (scale in MIN_STEP..MAX_STEP) return scale
        onScale.invoke(scale)
        return 1f
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val target = view ?: return
        target.requestFocus()
        target.context.getSystemService(InputMethodManager::class.java)?.showSoftInput(target, 0)
    }

    override fun shouldBackButtonBeMappedToEscape() = false

    override fun shouldEnforceCharBasedInput() = true

    override fun shouldUseCtrlSpaceWorkaround() = false

    override fun isTerminalViewSelected() = true

    override fun copyModeChanged(copyMode: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
        if (session != null && !session.isRunning && keyCode == KeyEvent.KEYCODE_ENTER) {
            onEndedEnter()
            return true
        }
        followOutput()
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false

    override fun onLongPress(event: MotionEvent): Boolean {
        onLongPressAt(event.x, event.y)
        val target = view ?: return false
        val emulator = target.currentSession?.emulator ?: return false
        val (column, row) = target.getColumnAndRow(event, true)
        val url = TerminalUrls.at(emulator.screen, emulator.mColumns, emulator.mRows, column, row) ?: return false
        onUrl(url)
        return true
    }

    override fun readControlKey() = keys.readCtrl()

    override fun readAltKey() = keys.readAlt()

    override fun readShiftKey() = false

    override fun readFnKey() = false

    /** Soft keyboards send Enter as a character rather than a key event. */
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
        if (session != null && !session.isRunning && (codePoint == '\r'.code || codePoint == '\n'.code)) {
            onEndedEnter()
            return true
        }
        followOutput()
        return false
    }

    /** Typing returns to the end of the output, as terminals do. */
    private fun followOutput() {
        val target = view ?: return
        if (target.topRow != 0) {
            target.topRow = 0
            target.invalidate()
        }
    }

    override fun onEmulatorSet() = Unit

    override fun logError(tag: String?, message: String?) {
        Log.e(tag ?: LOG_TAG, message.orEmpty())
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag ?: LOG_TAG, message.orEmpty())
    }

    // Informational logging would carry what the user typed; it stays off.
    override fun logInfo(tag: String?, message: String?) = Unit
    override fun logDebug(tag: String?, message: String?) = Unit
    override fun logVerbose(tag: String?, message: String?) = Unit

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: LOG_TAG, message.orEmpty(), e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: LOG_TAG, "", e)
    }

    private companion object {
        const val LOG_TAG = "IcodeTerminal"
        const val MIN_STEP = 0.9f
        const val MAX_STEP = 1.1f
    }
}
