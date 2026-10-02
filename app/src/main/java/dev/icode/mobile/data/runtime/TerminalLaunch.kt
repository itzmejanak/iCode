package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.TerminalSettingRules
import dev.icode.mobile.domain.TerminalSettings
import java.io.File

/**
 * Everything decided before a terminal session starts — which shell, where,
 * with what PRoot command line — or why it cannot start. File work, so it is
 * prepared off the main thread and handed to it.
 */
internal class TerminalLaunch private constructor(
    val workspaceHostPath: String?,
    val transcriptRows: Int,
    val argv: List<String> = emptyList(),
    val environment: List<String> = emptyList(),
    /** Set when no session can start; the tab shows it instead of a shell. */
    val failure: String? = null,
    /** The tab's name, where it is not a shell's ("bash 2"). */
    val title: String? = null,
    /** The process whose output the tab shows; null for a shell. */
    val follows: String? = null,
) {
    companion object {
        /** Null when the runtime is not installed. */
        fun prepare(layout: RuntimeLayout, prompt: ShellPrompt, settings: TerminalSettings, workspaceHostPath: String?): TerminalLaunch? {
            if (!layout.isInstalled) return null
            // The ids can change when Android reassigns the app's gids.
            layout.ensureGuestGroups()
            if (!shellAvailable(layout.rootfs, settings.shellPath)) {
                return TerminalLaunch(
                    workspaceHostPath = workspaceHostPath,
                    transcriptRows = settings.scrollbackLines,
                    failure = "The configured shell ${settings.shellPath} is unavailable in the runtime",
                )
            }
            prompt.install()
            val argv = layout.prootCommand(
                guestCommand = shellCommand(settings.shellPath),
                guestWorkingDirectory = workspaceHostPath?.let(layout.workspaces::guestOf) ?: RuntimeLayout.GUEST_HOME,
                killOnExit = true,
            )
            return TerminalLaunch(
                workspaceHostPath = workspaceHostPath,
                transcriptRows = settings.scrollbackLines,
                argv = argv,
                environment = layout.prootEnvironment().map { "${it.key}=${it.value}" },
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
                killOnExit = true,
            )
            return TerminalLaunch(
                workspaceHostPath = output.workspaceHostPath,
                transcriptRows = settings.scrollbackLines,
                argv = argv,
                environment = layout.prootEnvironment().map { "${it.key}=${it.value}" },
                title = output.title,
                follows = output.key,
            )
        }

        private const val TAIL = "/usr/bin/tail"

        /** A shell the settings allow, inside the runtime and not out of it through a link. */
        fun shellAvailable(rootfs: File, shellPath: String): Boolean {
            if (!TerminalSettingRules.validShellPath(shellPath)) return false
            val shell = File(rootfs, shellPath.removePrefix("/"))
            return shell.canonicalFile.toPath().startsWith(rootfs.canonicalFile.toPath()) && shell.isFile
        }

        /** bash starts as a login shell, so the user's profile and ours are read. */
        fun shellCommand(shellPath: String): List<String> =
            if (shellPath == "/bin/bash") listOf(shellPath, "-l") else listOf(shellPath)
    }
}
