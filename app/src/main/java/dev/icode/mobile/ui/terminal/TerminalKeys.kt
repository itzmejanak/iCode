package dev.icode.mobile.ui.terminal

import android.view.KeyEvent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.view.TerminalView
import dev.icode.mobile.domain.TerminalExtraKey

/**
 * The extra-key row's side of the terminal.
 *
 * CTRL and ALT are sticky: tap one, then the key it applies to. The view reads
 * them when that key arrives — typed on the keyboard or tapped in the row — and
 * reading clears them, so a modifier applies to exactly one key. The other keys
 * go through the view's own key handling, which knows the modes a program set:
 * the arrows in vim send the application-cursor codes vim asked for.
 */
@Stable
class TerminalKeys {
    var ctrl by mutableStateOf(false)
        private set
    var alt by mutableStateOf(false)
        private set

    internal var view: TerminalView? = null

    fun press(key: TerminalExtraKey) {
        when (key) {
            TerminalExtraKey.Ctrl -> ctrl = !ctrl
            TerminalExtraKey.Alt -> alt = !alt
            else -> {
                val target = view ?: return
                val code = keyCode(key)
                target.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
                target.onKeyUp(code, KeyEvent(KeyEvent.ACTION_UP, code))
            }
        }
    }

    /** Drops a held modifier the row no longer offers. */
    fun keepOnly(available: List<TerminalExtraKey>) {
        if (TerminalExtraKey.Ctrl !in available) ctrl = false
        if (TerminalExtraKey.Alt !in available) alt = false
    }

    internal fun readCtrl(): Boolean = ctrl.also { ctrl = false }

    internal fun readAlt(): Boolean = alt.also { alt = false }

    private fun keyCode(key: TerminalExtraKey): Int = when (key) {
        TerminalExtraKey.Escape -> KeyEvent.KEYCODE_ESCAPE
        TerminalExtraKey.Tab -> KeyEvent.KEYCODE_TAB
        TerminalExtraKey.Left -> KeyEvent.KEYCODE_DPAD_LEFT
        TerminalExtraKey.Up -> KeyEvent.KEYCODE_DPAD_UP
        TerminalExtraKey.Down -> KeyEvent.KEYCODE_DPAD_DOWN
        TerminalExtraKey.Right -> KeyEvent.KEYCODE_DPAD_RIGHT
        TerminalExtraKey.Ctrl, TerminalExtraKey.Alt -> error("$key is a modifier")
    }
}
