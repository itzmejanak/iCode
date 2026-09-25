package dev.icode.mobile.data.runtime

import java.io.File

/**
 * The name a user knows a running program by, from what was run.
 *
 * A process's `comm` is its main thread's name: programs set it themselves —
 * Node names its main thread `MainThread` — and the kernel cuts it to 15
 * characters. The arguments say what was run. When the program is an
 * interpreter or launcher running a script (`node /usr/local/bin/freebuff`,
 * `env node …/claude`, `python3 -m http.server`), the script is the program.
 */
internal object ProgramName {
    private const val MAX_LENGTH = 32
    private val launchers = setOf("env", "node", "nodejs", "bun", "deno", "ruby", "perl", "php", "java", "lua", "luajit")
    private val shells = setOf("sh", "bash", "dash", "zsh", "ash", "ksh", "mksh", "fish")
    private val python = Regex("""python\d*(\.\d+)?""")

    /** Reads `/proc/<pid>/cmdline`; null when the process is gone or has no arguments. */
    fun of(pid: Int): String? = runCatching {
        of(File("/proc/$pid/cmdline").readText().split('\u0000').dropLastWhile { it.isEmpty() })
    }.getOrNull()

    fun of(argv: List<String>): String? {
        var index = 0
        var name = argv.firstOrNull()?.let(::baseName) ?: return null
        while (isLauncher(name)) {
            // `sh -c '…'` runs a command string, not a script: the shell is the program.
            if (name in shells && argv.drop(index + 1).any { it == "-c" }) break
            val next = (index + 1 until argv.size).firstOrNull { position ->
                val argument = argv[position]
                !argument.startsWith("-") && !(name == "env" && '=' in argument)
            } ?: break
            index = next
            name = baseName(argv[index]) ?: break
        }
        return clean(name)
    }

    /** Without control characters, and short enough for one notification line. */
    fun clean(name: String): String? =
        name.filterNot { it.isISOControl() }.trim().take(MAX_LENGTH).takeIf { it.isNotEmpty() }

    private fun isLauncher(name: String): Boolean = name in launchers || name in shells || python.matches(name)

    private fun baseName(path: String): String? = path.trimEnd('/').substringAfterLast('/').takeIf { it.isNotEmpty() }
}
