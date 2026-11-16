package dev.icode.mobile.ui.terminal

import com.termux.terminal.TerminalSession
import dev.icode.mobile.domain.TerminalSettings

/**
 * Where the terminal view finds the sessions it draws.
 *
 * The session manager in the data layer owns them; the composition root adapts
 * it to this, so screens never reach into the data layer and the data layer
 * never into UI. Main thread only.
 */
interface TerminalSessionSource {
    /** The live session behind a tab, or null when it has none (it failed to start). */
    fun session(id: String): TerminalSession?

    /** Calls [onScreenChanged] whenever that session's screen changes, until the handle is closed. */
    fun observe(id: String, onScreenChanged: () -> Unit): AutoCloseable

    /** What a command printed, as a session that runs nothing (docs/assistant-and-mcp.md); null without a terminal. */
    fun replay(printed: String, settings: TerminalSettings): TerminalSession?

    companion object {
        /** No sessions: what a composition without a runtime behind it has. */
        val None: TerminalSessionSource = object : TerminalSessionSource {
            override fun session(id: String): TerminalSession? = null
            override fun observe(id: String, onScreenChanged: () -> Unit) = AutoCloseable { }
            override fun replay(printed: String, settings: TerminalSettings): TerminalSession? = null
        }
    }
}
