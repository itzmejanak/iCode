package dev.icode.mobile.data.assistant.tools

/**
 * The paths a command line names (docs/assistant-and-mcp.md), its words split as its
 * shell splits them: quotes, `\` escapes, a `#` comment, and `;`, `&`, `|`, `(`, `)`, `<`, `>`
 * between words. A word names a path when it starts with `/`, `~`, `./` or `../`, or is `.` or `..`,
 * whole or after its first `=` (`--prefix=/opt`). Where a variable, a substitution or a glob makes the
 * rest of a path unknown, the folder named before it stands for it.
 *
 * What it cannot see, by what a command's text is: a path a variable alone names (`$HOME/x`), and
 * what a script or a program reaches by itself.
 */
internal object CommandPaths {
    fun of(command: String): List<String> = words(command).mapNotNull(::pathIn).distinct()

    /** The shell's words in [command], each part it would expand kept as [UNKNOWN]. */
    private fun words(command: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        var inWord = false
        fun end() {
            if (inWord) words += word.toString()
            word.clear()
            inWord = false
        }
        var i = 0
        while (i < command.length) {
            val c = command[i]
            when {
                c == '\'' -> {
                    val close = command.indexOf('\'', i + 1).let { if (it < 0) command.length else it }
                    word.append(command, i + 1, close)
                    inWord = true
                    i = close
                }
                c == '"' -> {
                    i++
                    while (i < command.length && command[i] != '"') {
                        val d = command[i]
                        when {
                            d == '\\' && i + 1 < command.length && command[i + 1] in DOUBLE_QUOTED_ESCAPES -> word.append(command[++i])
                            d == '$' || d == '`' -> word.append(UNKNOWN)
                            else -> word.append(d)
                        }
                        i++
                    }
                    inWord = true
                }
                c == '\\' && i + 1 < command.length -> {
                    word.append(command[++i])
                    inWord = true
                }
                c == '#' && !inWord -> i = command.indexOf('\n', i).let { if (it < 0) command.length else it }
                c.isWhitespace() || c in SEPARATORS -> end()
                c == '$' || c == '`' || c in GLOBS -> {
                    word.append(UNKNOWN)
                    inWord = true
                }
                else -> {
                    word.append(c)
                    inWord = true
                }
            }
            i++
        }
        end()
        return words
    }

    /** The path [word] names, as far as it is known; null when it names none. */
    private fun pathIn(word: String): String? {
        val value = word.takeIf(::looksLikePath)
            ?: word.substringAfter('=', "").takeIf { '=' in word && looksLikePath(it) }
            ?: return null
        val known = value.substringBefore(UNKNOWN)
        if (known.length == value.length) return value
        val folder = known.substringBeforeLast('/', "")
        return when {
            folder.isNotEmpty() -> folder
            known.startsWith('/') -> "/"
            else -> null
        }
    }

    private fun looksLikePath(value: String): Boolean =
        value.startsWith('/') || value == "~" || value.startsWith("~/") ||
            value == "." || value == ".." || value.startsWith("./") || value.startsWith("../")

    private const val UNKNOWN = '\u0000'
    private const val SEPARATORS = ";&|()<>"
    private const val GLOBS = "*?["
    private const val DOUBLE_QUOTED_ESCAPES = "$`\"\\\n"
}
