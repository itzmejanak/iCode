package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.storage.WorkspacePaths
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
 * update reaches it.
 */
internal class ShellPrompt(private val rootfs: File) {
    /** Before a shell starts. */
    fun install() {
        runCatching {
            val home = File(rootfs, RuntimeLayout.GUEST_HOME.removePrefix("/"))
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
        }
    }

    companion object {
        private const val SCRIPT_FILE = ".icode-prompt.sh"

        /**
         * Termux's colour slot for the cursor, which iCode fills with the accent
         * (`TerminalPalette`). A slot, not a colour, is stored with each
         * character and looked up when the screen is drawn, so every prompt on
         * screen, earlier ones included, takes a new accent or theme at once.
         * xterm programs never use it: their indexed colours end at 255.
         */
        const val ACCENT_COLOR_INDEX = 258

        /**
         * Shows the path relative to the workspace root, so `/workspace/app/src`
         * reads as `app/src`, and `~` inside the guest home. Matched on whole
         * path segments with `case`: bash expands a `~` written as a
         * substitution's replacement back into the home path.
         */
        val SCRIPT = """
            __icode_prompt() {
              local shown="${'$'}PWD"
              case "${'$'}PWD" in
                ${WorkspacePaths.GUEST_ROOT}/*) shown="${'$'}{PWD#${WorkspacePaths.GUEST_ROOT}/}" ;;
                "${'$'}HOME") shown="~" ;;
                "${'$'}HOME"/*) shown="~${'$'}{PWD#"${'$'}HOME"}" ;;
              esac
              PS1="\[\e[38;5;${ACCENT_COLOR_INDEX}m\]${'$'}{shown}\[\e[0m\] \[\e[32m\]${'$'}\[\e[0m\] "
            }
            PROMPT_COMMAND=__icode_prompt
        """.trimIndent() + "\n"
    }
}
