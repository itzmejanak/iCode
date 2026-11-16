package dev.icode.mobile.ui.terminal

import android.content.ClipboardManager
import android.view.KeyEvent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.view.TerminalView
import dev.icode.mobile.domain.TerminalExtraKey

/**
 * What iCode's own controls send the terminal: the extra keys row's keys, and the bar's Paste, Stop
 * and Clear (docs/runtime-and-terminal.md).
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

    /**
     * The clipboard's text at the cursor, as Termux's own Paste puts it: newlines as Enter, and
     * bracketed when the program asked for it — bash then holds a pasted command for Enter.
     */
    fun paste() {
        val target = view ?: return
        val text = target.context.getSystemService(ClipboardManager::class.java)?.primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(target.context)
        if (text.isNullOrEmpty()) return
        target.currentSession?.emulator?.paste(text.toString())
        target.followOutput()
    }

    /** Ctrl+C: the program in the foreground stops; at the prompt, the line typed is dropped. */
    fun interrupt() = control('c')

    /** Ctrl+L: the shell clears the screen, its prompt on the top line. */
    fun clear() = control('l')

    /**
     * [letter] with Ctrl, through the view's own input, as the row's CTRL and a typed letter go —
     * exactly that key: a held CTRL or ALT is let go, not added to it.
     */
    private fun control(letter: Char) {
        ctrl = false
        alt = false
        view?.inputCodePoint(letter.code, true, false)
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
