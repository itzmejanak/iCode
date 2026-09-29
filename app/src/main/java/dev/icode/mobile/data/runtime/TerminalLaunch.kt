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
                workspaceHostPath = workspaceHostPath,
                guestWorkingDirectory = workspaceHostPath?.let(RuntimeLayout::guestWorkspace) ?: RuntimeLayout.GUEST_HOME,
                killOnExit = true,
            )
            return TerminalLaunch(
                workspaceHostPath = workspaceHostPath,
                transcriptRows = settings.scrollbackLines,
                argv = argv,
                environment = layout.prootEnvironment().map { "${it.key}=${it.value}" },
            )
        }

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
