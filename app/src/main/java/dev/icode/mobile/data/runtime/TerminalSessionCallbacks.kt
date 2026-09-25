package dev.icode.mobile.data.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dev.icode.mobile.domain.TerminalCursorStyle

/**
 * What iCode does with Termux's callbacks for one session.
 *
 * Termux calls these on the main thread. Session state stays with
 * [TerminalSessionManager]; this only translates: screen changes go
 * to whoever draws the session, the clipboard is Android's, and the exit is
 * handed back for the manager to decide what it means.
 */
internal class TerminalSessionCallbacks(
    private val context: Context,
    private val cursorStyle: () -> TerminalCursorStyle,
    private val onScreenChanged: () -> Unit,
    private val onFinished: (exitStatus: Int) -> Unit,
    private val onBell: () -> Unit,
) : TerminalSessionClient {

    private val clipboard: ClipboardManager? = context.getSystemService(ClipboardManager::class.java)

    override fun onTextChanged(changedSession: TerminalSession) = onScreenChanged()

    // Tab titles are iCode's; a program's window title does not rename them.
    override fun onTitleChanged(changedSession: TerminalSession) = Unit

    /** Termux's exit status: the exit code, or the negated signal that killed the shell. */
    override fun onSessionFinished(finishedSession: TerminalSession) = onFinished(finishedSession.exitStatus)

    /** Selection copy, and OSC 52 from programs such as editors. */
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        clipboard?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    }

    /** Goes through the emulator, so a program that asked for bracketed paste gets it. */
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
        if (!text.isNullOrEmpty()) session?.emulator?.paste(text)
    }

    /** A program asks for the user — Claude Code does, set to `terminal_bell`, when it finishes or waits. */
    override fun onBell(session: TerminalSession) = onBell()

    override fun onColorsChanged(session: TerminalSession) = onScreenChanged()

    // The cursor does not blink, so there is no blinker to start or stop.
    override fun onTerminalCursorStateChange(state: Boolean) = Unit

    override fun getTerminalCursorStyle(): Int = when (cursorStyle()) {
        TerminalCursorStyle.Block -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
        TerminalCursorStyle.Underline -> TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
        TerminalCursorStyle.Bar -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
    }

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
        const val CLIP_LABEL = "Terminal"
    }
}
