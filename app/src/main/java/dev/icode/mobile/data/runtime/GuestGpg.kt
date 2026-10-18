package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.GpgRequests
import java.io.File

/**
 * gpg's side of signed commits (docs/gpg-signing-plan.md), written with the runtime as `sudo` is
 * ([GuestSudo]): iCode's pinentry and agent program — one script under both names — and the system's
 * gpg settings that name them. A user's own `~/.gnupg` files override these: gpg reads `/etc/gnupg`
 * first.
 */
internal object GuestGpg {
    /** Where the script hands its requests over ([GpgBridge]), from the root filesystem: the guest's `/tmp/icode-gpg`. */
    const val REQUESTS = "tmp/icode-gpg"

    /** `<pid>.request`, and the pipe `<pid>.answer` its one-line answer comes through. */
    const val REQUEST = ".request"
    const val ANSWER = ".answer"

    private const val PINENTRY = "/usr/local/bin/icode-pinentry"
    private const val AGENT_PROGRAM = "/usr/local/bin/icode-gpg-agent"

    fun install(rootfs: File) {
        val script = script("/$REQUESTS")
        listOf(PINENTRY, AGENT_PROGRAM).forEach { path ->
            File(rootfs, path.removePrefix("/")).apply {
                parentFile?.mkdirs()
                writeText(script)
                setExecutable(true, false)
            }
        }
        File(rootfs, "etc/gnupg").apply {
            mkdirs()
            File(this, "gpg.conf").writeText(GPG_CONF)
            File(this, "gpg-agent.conf").writeText(AGENT_CONF)
        }
    }

    /**
     * The script, handing its requests over in the guest's [requests] folder. POSIX sh, as `sudo`'s:
     * the guest's `/bin/sh` is dash. Every shell `$` is written `\$`.
     */
    fun script(requests: String): String = listOf(
        "#!/bin/sh",
        "# Installed by iCode (docs/gpg-signing-plan.md), under two names.",
        "# icode-pinentry: gpg-agent's pinentry; it asks for a passphrase, or a yes or",
        "#   no, in iCode's own dialog.",
        "# icode-gpg-agent: gpg's agent program; it has iCode start gpg-agent in a",
        "#   session of its own, where it outlives the command that needed it and keeps",
        "#   the passphrase as long as /etc/gnupg/gpg-agent.conf says.",
        "dir=${shellQuote(requests)}",
        "request=\"\$dir/\$\$$REQUEST\"",
        "answer=\"\$dir/\$\$$ANSWER\"",
        "cleanup() { rm -f \"\$request\" \"\$request.new\" \"\$answer\"; }",
        "trap 'cleanup; exit 1' HUP INT TERM PIPE",
        "trap cleanup EXIT",
        "",
        "# ask KIND [LINE]...: hands iCode a request; \$reply is the one line it answers.",
        "# It runs in this shell, not a subshell, so gpg-agent's signal ends it through",
        "# the trap, and iCode's dialog closes with the request.",
        "ask() {",
        "  mkdir -p \"\$dir\" && mkfifo -m 600 \"\$answer\" || return 1",
        "  # Read and write: opening never waits on iCode, and the answer waits in the pipe.",
        "  exec 3<>\"\$answer\"",
        "  printf '%s\\n' \"\$@\" > \"\$request.new\" && mv \"\$request.new\" \"\$request\" || return 1",
        "  IFS= read -r reply <&3",
        "  exec 3>&-",
        "  cleanup",
        "}",
        "",
        "if [ \"\${0##*/}\" = ${AGENT_PROGRAM.substringAfterLast('/')} ]; then",
        "  # gpg runs it as `--homedir DIR --use-standard-socket --daemon` when no agent",
        "  # answers, and connects once it has exited with 0: iCode answers 0 when one does.",
        "  home=\"\$HOME/.gnupg\"",
        "  while [ \$# -gt 0 ]; do",
        "    [ \"\$1\" = --homedir ] && [ \$# -ge 2 ] && { home=\$2; shift; }",
        "    shift",
        "  done",
        "  ask ${GpgRequests.AGENT} \"${GpgRequests.HOMEDIR} \$home\" || exit 1",
        "  [ \"\$reply\" = 0 ]",
        "  exit",
        "fi",
        "",
        "# Pinentry's side of the Assuan protocol. The texts go to iCode as gpg-agent",
        "# sent them, percent-escaped.",
        "reset() { title= desc= prompt= error= ok= cancel=; }",
        "reset",
        "echo 'OK Pleased to meet you'",
        "while IFS= read -r line; do",
        "  case \"\$line\" in",
        "    'SETTITLE '*) title=\$line; echo OK ;;",
        "    'SETDESC '*) desc=\$line; echo OK ;;",
        "    'SETPROMPT '*) prompt=\$line; echo OK ;;",
        "    'SETERROR '*) error=\$line; echo OK ;;",
        "    'SETOK '*) ok=\$line; echo OK ;;",
        "    'SETCANCEL '*) cancel=\$line; echo OK ;;",
        "    # Unknown, so gpg-agent names the second button with SETCANCEL instead.",
        "    'SETNOTOK '*) echo 'ERR 536871187 Unknown IPC command <User defined source 1>' ;;",
        "    GETPIN|CONFIRM|CONFIRM\\ *|MESSAGE)",
        "      [ \"\$line\" = GETPIN ] && kind=${GpgRequests.PIN} || kind=${GpgRequests.CONFIRM}",
        "      ask \"\$kind\" \"\$title\" \"\$desc\" \"\$prompt\" \"\$error\" \"\$ok\" \"\$cancel\" || reply=",
        "      # An error is said once, with the next question.",
        "      error=",
        "      case \"\$reply\" in",
        "        'D '*) printf '%s\\nOK\\n' \"\$reply\" ;;",
        "        OK) echo OK ;;",
        "        *) echo 'ERR 83886179 Operation cancelled <Pinentry>' ;;",
        "      esac ;;",
        "    'GETINFO pid') printf 'D %s\\nOK\\n' \"\$\$\" ;;",
        "    'GETINFO flavor') printf 'D icode\\nOK\\n' ;;",
        "    RESET) reset; echo OK ;;",
        "    BYE) echo OK; exit 0 ;;",
        "    # OPTION, SETKEYINFO, SETREPEAT, SETQUALITYBAR, SETTIMEOUT and the like",
        "    # change nothing here: without PIN_REPEATED, gpg-agent asks again itself.",
        "    *) echo OK ;;",
        "  esac",
        "done",
    ).joinToString("\n", postfix = "\n")

    private val GPG_CONF = """
        # Installed by iCode. With no gpg-agent running, gpg has iCode start one in a session of
        # its own, where it outlives the command that needed it. ~/.gnupg/gpg.conf overrides this.
        agent-program $AGENT_PROGRAM
    """.trimIndent() + "\n"

    private val AGENT_CONF = """
        # Installed by iCode. A passphrase is asked in iCode's own dialog, and kept until 30 minutes
        # after it was last used (2 hours at most, gpg-agent's max-cache-ttl).
        # ~/.gnupg/gpg-agent.conf overrides these.
        pinentry-program $PINENTRY
        default-cache-ttl 1800
    """.trimIndent() + "\n"
}
