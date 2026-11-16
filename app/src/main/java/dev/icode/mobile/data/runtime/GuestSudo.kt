package dev.icode.mobile.data.runtime

import java.io.File

/**
 * The `sudo` the guest answers to.
 *
 * The terminal already runs as the runtime's root user — PRoot's root inside
 * the runtime, not the device's — so there is nothing to elevate: `sudo` runs
 * the command right there, in the same terminal, where it reads the keyboard,
 * stops on Ctrl+C and draws its own progress. It exists so that commands
 * written for an ordinary Linux machine (`sudo apt install git`,
 * `curl … | sudo bash`) work exactly as written.
 *
 * It accepts the options scripts commonly pass and refuses plainly what cannot
 * be done: there is no other user to become. A `sudo` the user installs with
 * apt runs instead (docs/runtime-and-terminal.md): it is theirs to choose.
 */
internal object GuestSudo {
    const val RELATIVE_PATH = "usr/local/bin/sudo"

    /** Where apt installs the real `sudo`. */
    private const val INSTALLED = "/usr/bin/sudo"

    fun install(rootfs: File) {
        val target = File(rootfs, RELATIVE_PATH)
        target.parentFile?.mkdirs()
        target.writeText(script(INSTALLED))
        target.setExecutable(true, false)
    }

    /** POSIX sh: the guest's `/bin/sh` is dash. Every shell `$` is written `\$`. [installed]: a real `sudo`'s path. */
    fun script(installed: String): String = listOf(
        "#!/bin/sh",
        "# ${RuntimeLayout.MANAGED} the runtime's only user is root (inside the runtime,",
        "# not on the device), so sudo runs the command as it is. A sudo installed",
        "# with apt ($installed) runs instead.",
        "[ -x ${shellQuote(installed)} ] && exec ${shellQuote(installed)} \"\$@\"",
        "usage() {",
        "  echo 'usage: sudo [-EHnSbk] [-u root] [-i | -s] [VAR=value]... [command [argument]...]'",
        "}",
        "only_root() {",
        "  case \"\$1\" in",
        "    root|0|'#0') ;;",
        "    *) echo \"sudo: the runtime has one user, root; it cannot run commands as \$1\" >&2; exit 1 ;;",
        "  esac",
        "}",
        "shell=''",
        "background=''",
        "only_forget=''",
        "while [ \$# -gt 0 ]; do",
        "  case \"\$1\" in",
        "    --) shift; break ;;",
        "    -h|--help) usage; exit 0 ;;",
        "    -V|--version) echo \"iCode sudo: runs commands as the runtime's root user\"; exit 0 ;;",
        "    -l|--list) echo 'User root may run the following commands: (ALL) ALL'; exit 0 ;;",
        "    -u|-g|--user|--group)",
        "      [ \$# -ge 2 ] || { usage >&2; exit 1; }",
        "      only_root \"\$2\"; shift 2 ;;",
        "    --user=*) only_root \"\${1#--user=}\"; shift ;;",
        "    --group=*) only_root \"\${1#--group=}\"; shift ;;",
        "    -u?*) only_root \"\${1#-u}\"; shift ;;",
        "    -g?*) only_root \"\${1#-g}\"; shift ;;",
        "    -p|--prompt)",
        "      [ \$# -ge 2 ] || { usage >&2; exit 1; }",
        "      shift 2 ;;",
        "    -p?*|--prompt=*) shift ;;",
        "    -D|--chdir)",
        "      [ \$# -ge 2 ] || { usage >&2; exit 1; }",
        "      cd \"\$2\" || exit 1; shift 2 ;;",
        "    --chdir=*) cd \"\${1#--chdir=}\" || exit 1; shift ;;",
        "    --preserve-env|--preserve-env=*|--set-home|--non-interactive|--stdin|--askpass|--preserve-groups) shift ;;",
        "    --reset-timestamp|--remove-timestamp|--validate) only_forget=1; shift ;;",
        "    --background) background=1; shift ;;",
        "    --login) shell=login; shift ;;",
        "    --shell) shell=plain; shift ;;",
        "    -?*)",
        // Short options, possibly combined as in `-EH`.
        "      flags=\${1#-}",
        "      while [ -n \"\$flags\" ]; do",
        "        flag=\${flags%\"\${flags#?}\"}",
        "        flags=\${flags#?}",
        "        case \"\$flag\" in",
        "          E|H|n|S|A|P) ;;",
        "          k|K|v) only_forget=1 ;;",
        "          b) background=1 ;;",
        "          i) shell=login ;;",
        "          s) shell=plain ;;",
        "          *) echo \"sudo: -\$flag: not an option iCode's sudo supports\" >&2; exit 1 ;;",
        "        esac",
        "      done",
        "      shift ;;",
        "    *) break ;;",
        "  esac",
        "done",
        // NAME=value arguments before the command set its environment.
        "while [ \$# -gt 0 ]; do",
        "  case \"\$1\" in",
        "    *=*)",
        "      name=\${1%%=*}",
        "      case \"\$name\" in",
        "        ''|[0-9]*|*[!A-Za-z0-9_]*) break ;;",
        "      esac",
        "      export \"\$1\"; shift ;;",
        "    *) break ;;",
        "  esac",
        "done",
        "if [ -n \"\$shell\" ]; then",
        "  run_shell=\${SHELL:-/bin/bash}",
        "  if [ \"\$shell\" = login ]; then",
        "    cd \"\$HOME\" 2>/dev/null",
        "    [ \$# -eq 0 ] && exec \"\$run_shell\" -l",
        "  fi",
        "  [ \$# -eq 0 ] && exec \"\$run_shell\"",
        "  exec \"\$run_shell\" -c \"\$*\"",
        "fi",
        "if [ \$# -eq 0 ]; then",
        "  [ -n \"\$only_forget\" ] && exit 0",
        "  usage >&2",
        "  exit 1",
        "fi",
        "if ! command -v \"\$1\" >/dev/null 2>&1; then",
        "  echo \"sudo: \$1: command not found\" >&2",
        "  exit 1",
        "fi",
        "if [ -n \"\$background\" ]; then",
        "  \"\$@\" &",
        "  exit 0",
        "fi",
        "exec \"\$@\"",
    ).joinToString("\n", postfix = "\n")
}
