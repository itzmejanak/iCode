package dev.icode.mobile.data.runtime

import java.io.File

/** What a terminal session has in the foreground right now. */
internal sealed interface TerminalForeground {
    /** The shell itself is in the foreground: it is waiting at its prompt. */
    data object Idle : TerminalForeground

    /** A program the user started holds the terminal. */
    data class Running(val program: String) : TerminalForeground

    /** Nothing could be read, for instance because the processes just ended. */
    data object Unknown : TerminalForeground

    companion object {
        /**
         * Reads it from `/proc`, for the session whose PRoot is [prootPid].
         *
         * PRoot shares the session's terminal, so its own `stat` names the
         * terminal's foreground process group. When that group's leader is
         * PRoot's direct child — the shell — the shell is at its prompt;
         * otherwise the leader is the program the user is running, named by
         * what was run ([ProgramName]); its thread name is only the fallback.
         * Android shows an app only its own processes, so nothing else is read.
         */
        fun of(prootPid: Int): TerminalForeground {
            val proot = ProcStat.read(prootPid) ?: return Unknown
            if (proot.foregroundGroup <= 0) return Unknown
            val leader = ProcStat.read(proot.foregroundGroup) ?: return Unknown
            if (leader.parent == prootPid) return Idle
            val name = ProgramName.of(proot.foregroundGroup) ?: ProgramName.clean(leader.command) ?: return Unknown
            return Running(name)
        }
    }
}

/** The fields of `/proc/<pid>/stat` this app reads. */
internal data class ProcStat(val command: String, val parent: Int, val foregroundGroup: Int) {
    companion object {
        fun read(pid: Int): ProcStat? = runCatching { parse(File("/proc/$pid/stat").readText()) }.getOrNull()

        /**
         * `pid (comm) state ppid pgrp session tty_nr tpgid …` (proc(5)). The
         * command sits in parentheses and may itself contain spaces and
         * parentheses, so the fields are counted from its last `)`.
         */
        fun parse(stat: String): ProcStat? {
            val open = stat.indexOf('(')
            val close = stat.lastIndexOf(')')
            if (open < 0 || close <= open) return null
            val fields = stat.substring(close + 1).trim().split(' ')
            if (fields.size < 6) return null
            return ProcStat(
                command = stat.substring(open + 1, close),
                parent = fields[1].toIntOrNull() ?: return null,
                foregroundGroup = fields[5].toIntOrNull() ?: return null,
            )
        }
    }
}
