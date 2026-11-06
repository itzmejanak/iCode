package dev.icode.mobile.data.runtime

import android.os.Handler
import android.system.Os
import android.system.OsConstants
import com.termux.terminal.TerminalSession

/**
 * A tab's Termux session as [TerminalSessionManager] makes and reads it: [launch]'s program, its output
 * read by [commands] as the emulator gets it, from its first byte (docs/agent-control-plan.md §4.1).
 * Main thread only, as the session is.
 */
internal fun terminalSession(launch: TerminalLaunch, runtimeRoot: String, callbacks: TerminalSessionCallbacks, commands: TerminalCommands): TerminalSession =
    TerminalSession(launch.argv.first(), runtimeRoot, launch.argv.toTypedArray(), launch.environment.toTypedArray(), launch.transcriptRows, callbacks)
        .also { session -> session.setOutputTap { _, data, count -> commands.feed(data, count) } }

/** [text] typed into the session as its keyboard would; false when it no longer runs. */
internal fun TerminalSession.type(text: String): Boolean {
    if (!isRunning) return false
    val bytes = text.toByteArray(Charsets.UTF_8)
    write(bytes, 0, bytes.size)
    return true
}

/** Its last [lines] of screen and scrollback, as text, a wrapped line joined again. */
internal fun TerminalSession.lastLines(lines: Int): String? {
    val screen = emulator?.screen ?: return null
    val bottom = emulator.mRows - 1
    val top = (bottom + 1 - lines).coerceAtLeast(-screen.activeTranscriptRows)
    return screen.getSelectedText(0, top, emulator.mColumns, bottom).trimEnd()
}

/**
 * SIGHUP to PRoot, which passes it on to every program in the session and
 * kills those that ignore it two seconds later (third_party/patches/proot/0002).
 * Should PRoot itself still be running after that, it is killed, and
 * PTRACE_O_EXITKILL takes its programs with it.
 */
internal fun TerminalSession.hangUp(handler: Handler) {
    if (!isRunning) return
    runCatching { Os.kill(pid, OsConstants.SIGHUP) }
    handler.postDelayed({ finishIfRunning() }, HANG_UP_TIMEOUT_MILLIS)
}

private const val HANG_UP_TIMEOUT_MILLIS = 5_000L
