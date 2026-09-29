package dev.icode.mobile.data.runtime

import java.io.File

/**
 * The prompt iCode's shells show.
 *
 * `root@localhost:/workspace` says nothing useful on a phone, where the line
 * is short and there is only ever one host and one user. The prompt is the
 * project name instead, plus any subdirectory the user has moved into, in the
 * app's accent colour.
 *
 * The script is its own file, sourced from `.bashrc` and `.profile`, so a
 * user's edits there are never overwritten, and rewritten on every start so an
 * update reaches it. The accent is a second file the prompt reads each time it
 * is drawn: a running shell follows a new accent or theme at its next prompt,
 * and nothing is ever typed into it. Without that file the prompt keeps the
 * orange it always had.
 */
internal class ShellPrompt(private val rootfs: File, private val installed: () -> Boolean) {
    @Volatile private var accent: Int? = null

    private val home: File get() = File(rootfs, RuntimeLayout.GUEST_HOME.removePrefix("/"))

    /** Before a shell starts. */
    fun install() {
        runCatching {
            home.mkdirs()
            File(home, SCRIPT_FILE).writeText(SCRIPT)
            val sourceLine = ". ${RuntimeLayout.GUEST_HOME}/$SCRIPT_FILE"
            listOf(".bashrc", ".profile").forEach { name ->
                val file = File(home, name)
                val existing = if (file.isFile) file.readText() else ""
                if (!existing.contains(SCRIPT_FILE)) {
                    file.appendText("\n# iCode prompt\n$sourceLine\n")
                }
            }
            accent?.let(::writeAccent)
        }
    }

    /** Follows the app's accent; an uninstalled runtime gets it at its first shell. */
    fun useAccent(argb: Int) {
        accent = argb
        if (installed()) runCatching { writeAccent(argb) }
    }

    private fun writeAccent(argb: Int) = File(home, ACCENT_FILE).writeText(rgb(argb) + "\n")

    companion object {
        private const val SCRIPT_FILE = ".icode-prompt.sh"
        private const val ACCENT_FILE = ".icode-accent"

        /** "255;107;44": red, green and blue as a 24-bit colour escape takes them. */
        fun rgb(argb: Int): String = "${argb shr 16 and 0xFF};${argb shr 8 and 0xFF};${argb and 0xFF}"

        /**
         * Shows the path relative to the workspace root, so `/workspace/app/src`
         * reads as `app/src`, and `~` inside the guest home. Matched on whole
         * path segments with `case`: bash expands a `~` written as a
         * substitution's replacement back into the home path. The accent file
         * must hold digits and semicolons only; anything else keeps the default
         * colour rather than reaching the terminal.
         */
        val SCRIPT = """
            __icode_prompt() {
              local shown="${'$'}PWD"
              case "${'$'}PWD" in
                ${RuntimeLayout.GUEST_WORKSPACE}/*) shown="${'$'}{PWD#${RuntimeLayout.GUEST_WORKSPACE}/}" ;;
                "${'$'}HOME") shown="~" ;;
                "${'$'}HOME"/*) shown="~${'$'}{PWD#"${'$'}HOME"}" ;;
              esac
              local color="38;5;208" rgb=""
              [ -r "${'$'}HOME/$ACCENT_FILE" ] && read -r rgb < "${'$'}HOME/$ACCENT_FILE"
              case "${'$'}rgb" in
                ""|*[!0-9\;]*) ;;
                *) color="38;2;${'$'}rgb" ;;
              esac
              PS1="\[\e[${'$'}{color}m\]${'$'}{shown}\[\e[0m\] \[\e[32m\]${'$'}\[\e[0m\] "
            }
            PROMPT_COMMAND=__icode_prompt
        """.trimIndent() + "\n"
    }
}
