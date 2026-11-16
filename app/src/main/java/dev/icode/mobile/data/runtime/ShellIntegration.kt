package dev.icode.mobile.data.runtime

import java.io.File

/**
 * iCode's shell integration (docs/assistant-and-mcp.md): the file every bash tab iCode starts
 * runs as its rcfile, `bash --rcfile` [RCFILE] `-i`. It reads what a login bash reads — `/etc/profile`,
 * then the first of `~/.bash_profile`, `~/.bash_login` and `~/.profile` — so the user's own setup, a
 * prompt of theirs included, is as it was. Then it adds the marks a terminal reads ([TerminalMarks]):
 * before the user's own prompt command, D with the last command's code and OSC 7 with the directory;
 * after it, A, and B at the prompt's end; before a command runs (PS0), C. Written again at every iCode
 * start, as [ShellPrompt]'s file is.
 */
internal object ShellIntegration {
    const val RCFILE = "/etc/icode/bashrc"

    /** The shell [shell] names is bash: iCode's tabs start it with [RCFILE]. */
    fun isBash(shell: String): Boolean = shell.substringAfterLast('/') == "bash"

    /** A bash tab's command line: an interactive shell reading [RCFILE]. */
    fun command(shell: String): List<String> = listOf(shell, "--rcfile", RCFILE, "-i")

    /** With iCode's other own files ([RuntimeLayout.writeOwnFiles]). */
    fun write(rootfs: File) {
        File(rootfs, RCFILE.removePrefix("/")).apply { parentFile?.mkdirs() }.writeText(SCRIPT)
    }

    val SCRIPT = """
        # ${RuntimeLayout.MANAGED} written again at every iCode start. Your own setup goes in ~/.bashrc or ~/.profile.
        # What a login bash reads, then the marks iCode's terminal reads (OSC 133 and OSC 7).
        [ -r /etc/profile ] && . /etc/profile
        if [ -r ~/.bash_profile ]; then . ~/.bash_profile
        elif [ -r ~/.bash_login ]; then . ~/.bash_login
        elif [ -r ~/.profile ]; then . ~/.profile
        fi
        __icode_end() {
          local status=${'$'}?
          printf '\033]7;file://%s%s\007\033]133;D;%s\007' "${'$'}{HOSTNAME:-localhost}" "${'$'}PWD" "${'$'}status"
          return ${'$'}status
        }
        __icode_start() {
          local status=${'$'}?
          printf '\033]133;A\007'
          case "${'$'}PS1" in *'133;B'*) ;; *) PS1="${'$'}PS1"'\[\033]133;B\007\]' ;; esac
          return ${'$'}status
        }
        if (( BASH_VERSINFO[0] > 5 || (BASH_VERSINFO[0] == 5 && BASH_VERSINFO[1] >= 1) )); then
          __icode_commands=("${'$'}{PROMPT_COMMAND[@]}")
          PROMPT_COMMAND=(__icode_end "${'$'}{__icode_commands[@]}" __icode_start)
          unset __icode_commands
        else
          PROMPT_COMMAND="__icode_end;${'$'}{PROMPT_COMMAND:+${'$'}PROMPT_COMMAND;}__icode_start"
        fi
        PS0="${'$'}{PS0-}\e]133;C\a"
        bind 'set enable-bracketed-paste on' 2>/dev/null
    """.trimIndent() + "\n"
}
