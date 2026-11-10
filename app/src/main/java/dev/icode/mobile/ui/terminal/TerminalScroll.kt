package dev.icode.mobile.ui.terminal

import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import java.util.WeakHashMap

/**
 * Each session's place in its output while its view is gone — another tool on screen, another tab
 * shown (docs/dock-navigation-plan.md D5). The view is made again when it comes back, and Termux's
 * view starts at the latest output; scrolled back, it is set again where it was. The place is counted
 * from the oldest line the session keeps, so lines that arrive meanwhile do not move it. At the latest
 * output nothing is kept: the view follows what comes, as it did. A selection is not kept: it is a
 * gesture in hand, and Android drops one on leaving too.
 */
internal object TerminalScroll {
    private val places = WeakHashMap<TerminalSession, Int>()

    /** [view]'s session, as it is left. Main thread. */
    fun leave(view: TerminalView) {
        val session = view.currentSession ?: return
        val kept = session.emulator?.let { keptPlace(view.topRow, it.screen.activeTranscriptRows) }
        if (kept == null) places.remove(session) else places[session] = kept
    }

    /** Back where [session] was left in [view], if it was scrolled back and is still the view's. Main thread. */
    fun restore(view: TerminalView, session: TerminalSession) {
        val place = places.remove(session) ?: return
        val emulator = session.emulator ?: return
        if (view.currentSession !== session) return
        view.topRow = topRowFor(place, emulator.screen.activeTranscriptRows)
        // What arrived meanwhile is counted in the place: the view's own follow must not move it again.
        emulator.clearScrollCounter()
        view.invalidate()
    }

    /** A view's top row, kept as rows from the oldest line; null at the latest output. */
    internal fun keptPlace(topRow: Int, transcriptRows: Int): Int? = if (topRow < 0) transcriptRows + topRow else null

    /** The top row for a place kept as rows from the oldest line, within what the session holds now. */
    internal fun topRowFor(place: Int, transcriptRows: Int): Int = (place - transcriptRows).coerceIn(-transcriptRows, 0)
}
