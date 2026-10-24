package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File

/**
 * iCode's prompt, a default for new runtimes (docs/runtime-contract-plan.md §5).
 *
 * `root@localhost:/workspace` says nothing useful on a phone, where the line
 * is short and there is only ever one host and one user. The prompt is the
 * project name instead, plus any subdirectory the user has moved into, in the
 * app's accent colour.
 *
 * The script is iCode's own file, [SCRIPT_PATH], written again before every
 * terminal so an update reaches it. A home iCode creates sources it from one
 * line at the end of `~/.bashrc`, which the user deletes to use their own;
 * nothing else touches a dotfile, so a prompt set after that line (Starship's)
 * wins.
 */
internal object ShellPrompt {
    const val SCRIPT_PATH = "/etc/icode/prompt.sh"

    /** The line a new home's `~/.bashrc` ends with. */
    const val SOURCE_LINE = ". $SCRIPT_PATH  # Managed by iCode: its prompt — delete this line to use your own"

    /** Before a terminal starts. A runtime that cannot take it still starts its shell. */
    fun write(rootfs: File) {
        runCatching { File(rootfs, SCRIPT_PATH.removePrefix("/")).apply { parentFile?.mkdirs() }.writeText(SCRIPT) }
    }

    /** Into [home], a home iCode has just created: the one line that sources [SCRIPT_PATH]. */
    fun addTo(home: File) {
        File(home, ".bashrc").appendText("\n$SOURCE_LINE\n")
    }

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
        # Managed by iCode: written again before every terminal starts. Your own prompt goes in ~/.bashrc.
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
