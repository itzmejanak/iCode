package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.TerminalSettings

/**
 * Everything decided before a terminal session starts — which shell, where,
 * with what PRoot command line. File work, so it is prepared off the main
 * thread and handed to it.
 */
internal class TerminalLaunch private constructor(
    val workspaceHostPath: String?,
    val transcriptRows: Int,
    val argv: List<String> = emptyList(),
    val environment: List<String> = emptyList(),
    /** The tab's name, where it is not a shell's ("bash 2"). */
    val title: String? = null,
    /** The process whose output the tab shows; null for a shell. */
    val follows: String? = null,
    /** The chat whose tab it is (docs/agent-control-plan.md §3 A4); null for the user's. */
    val assistant: String? = null,
) {
    companion object {
        /** Null when the runtime is not installed. */
        fun prepare(layout: RuntimeLayout, settings: TerminalSettings, workspaceHostPath: String?): TerminalLaunch? {
            if (!layout.isInstalled) return null
            // The ids can change when Android reassigns the app's gids.
            layout.ensureGuestGroups()
            val shell = layout.loginShell()
            val argv = layout.prootCommand(
                guestCommand = shellCommand(layout.recordedShell(), shell),
                variables = GuestVariables.of(GuestContext.Terminal, shell),
                guestWorkingDirectory = workspaceHostPath?.let(layout.workspaces::guestOf) ?: RuntimeLayout.GUEST_HOME,
                killOnExit = true,
            )
            return TerminalLaunch(
                workspaceHostPath = workspaceHostPath,
                transcriptRows = settings.scrollbackLines,
                argv = argv,
                environment = layout.hostEnvironment().map { "${it.key}=${it.value}" },
            )
        }

        /**
         * A chat's tab (docs/agent-control-plan.md §3 A4): bash with iCode's integration, whatever the
         * login shell, in [workspaceHostPath], where nothing pages. Null when the runtime is not installed.
         */
        fun assistant(layout: RuntimeLayout, settings: TerminalSettings, workspaceHostPath: String, chat: String): TerminalLaunch? {
            if (!layout.isInstalled) return null
            layout.ensureGuestGroups()
            val argv = layout.prootCommand(
                guestCommand = ShellIntegration.command(BASH),
                variables = GuestVariables.of(GuestContext.AssistantTerminal, layout.loginShell()),
                guestWorkingDirectory = layout.workspaces.guestOf(workspaceHostPath) ?: RuntimeLayout.GUEST_HOME,
                killOnExit = true,
            )
            return TerminalLaunch(
                workspaceHostPath = workspaceHostPath,
                transcriptRows = settings.scrollbackLines,
                argv = argv,
                environment = layout.hostEnvironment().map { "${it.key}=${it.value}" },
                assistant = chat,
            )
        }

        /**
         * A tab of [output]'s own (docs/runtime-foundation-plan.md §6 P3): `tail -F` of its log, from
         * as many lines back as the terminal keeps, then everything it prints. Null when the runtime
         * is not installed.
         */
        fun following(layout: RuntimeLayout, settings: TerminalSettings, output: FollowedOutput): TerminalLaunch? {
            if (!layout.isInstalled) return null
            val argv = layout.prootCommand(
                guestCommand = listOf(TAIL, "-n", settings.scrollbackLines.toString(), "-F", output.log),
                variables = GuestVariables.of(GuestContext.Terminal, layout.loginShell()),
                killOnExit = true,
            )
            return TerminalLaunch(
                workspaceHostPath = output.workspaceHostPath,
                transcriptRows = settings.scrollbackLines,
                argv = argv,
                environment = layout.hostEnvironment().map { "${it.key}=${it.value}" },
                title = output.title,
                follows = output.key,
            )
        }

        private const val TAIL = "/usr/bin/tail"
        private const val BASH = "/bin/bash"

        /**
         * Root's login [shell] as a login shell (`-l`: bash, zsh, fish and dash all take it), so its
         * profile is read; bash reads it through iCode's rcfile, which adds its marks
         * ([ShellIntegration]). When it is not the shell `/etc/passwd` records ([recorded]) — that one
         * is not installed — the tab's first line says so, and how to change it.
         */
        fun shellCommand(recorded: String?, shell: String): List<String> {
            val login = if (ShellIntegration.isBash(shell)) ShellIntegration.command(shell) else listOf(shell, "-l")
            if (recorded == null || recorded == shell) return login
            val why = "iCode: your login shell $recorded (from /etc/passwd) is not installed — " +
                "`apt install ${recorded.substringAfterLast('/')}`, or `chsh -s $shell`"
            return listOf("/bin/sh", "-c", "printf '%s\\n' \"\$1\"; shift; exec \"\$@\"", "sh", why) + login
        }
    }
}
