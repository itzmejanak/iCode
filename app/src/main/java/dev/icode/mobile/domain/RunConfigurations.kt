package dev.icode.mobile.domain

// The Terminal panel's Run tab (docs/runtime-and-terminal.md): what the open workspace can start — each
// Android app iCode finds in it, and the commands the user saved — each run a managed process.

/** One thing the Run tab starts. */
sealed interface RunConfiguration {
    /** Stable within its workspace: `android:app`, or a saved command's own id. */
    val id: String
    val name: String

    /** What runs, as a terminal shows it. */
    val command: String

    /** What it is, as its row says: "Android app", "Command". */
    val kind: String

    /** An Android app module, found by iCode: Build & Run. */
    data class AndroidApp(val project: AndroidProject) : RunConfiguration {
        override val id: String get() = "$ANDROID${project.module}"
        override val name: String get() = project.module.ifEmpty { project.workspace.substringAfterLast('/') }
        override val command: String get() = "./gradlew ${project.task}"
        override val kind: String get() = "Android app"
    }

    /** A command the user saved for the workspace, run in bash at its top. */
    data class Command(override val id: String, override val name: String, override val command: String) : RunConfiguration {
        override val kind: String get() = "Command"
    }

    companion object {
        private const val ANDROID = "android:"
    }
}

/** A configuration's last run in this iCode process. */
data class RunRecord(
    val workspace: String,
    val configuration: String,
    /** Its managed process: the output is that process's Terminal tab. Null when it did not start. */
    val process: String?,
    /** What followed an Android app's build; null for a command, and while the build runs. */
    val androidStep: AndroidAppStep? = null,
    /** Why it stopped short, in the words of what failed; null otherwise. */
    val failure: String? = null,
)

/** The Run tab: the open workspace's configurations, found ones first, and every run's record. */
data class RunState(
    val configurations: List<RunConfiguration> = emptyList(),
    val records: List<RunRecord> = emptyList(),
) {
    fun recordOf(workspace: String, configuration: String): RunRecord? =
        records.firstOrNull { it.workspace == workspace && it.configuration == configuration }
}

/** What a configuration's row says of its last run. */
sealed interface RunStatus {
    data object NeverRun : RunStatus

    /** "Building · 1m", "Running · 1m", with the ports its output names. */
    data class Running(val label: String, val ports: List<Int>) : RunStatus

    /** Built: Android asks the user to confirm the install. */
    data object Installing : RunStatus

    data object Opened : RunStatus

    /** "Exited 0", "Stopped", "Ended by SIGKILL"; [succeeded] whether it exited with 0, null when it was stopped or nothing says. */
    data class Ended(val label: String, val succeeded: Boolean?) : RunStatus

    data class Failed(val reason: String) : RunStatus
}

object RunRules {
    fun status(configuration: RunConfiguration, record: RunRecord?, process: ManagedProcess?, now: Long): RunStatus {
        if (record == null) return RunStatus.NeverRun
        record.failure?.let { return RunStatus.Failed(it) }
        when (record.androidStep) {
            AndroidAppStep.Installing -> return RunStatus.Installing
            AndroidAppStep.Opened -> return RunStatus.Opened
            null -> Unit
        }
        // Its process was forgotten elsewhere (the Assistant's process list): it ran, and nothing says how it ended.
        if (process == null) return RunStatus.Ended("Ended", succeeded = null)
        return when (val state = process.state) {
            ManagedProcessState.Running -> RunStatus.Running(
                (if (configuration is RunConfiguration.AndroidApp) "Building · " else "Running · ") + RelativeTime.span(now - process.startedAt),
                process.ports,
            )
            is ManagedProcessState.Ended -> RunStatus.Ended(ProcessRules.stateLabel(state, now, process.startedAt), succeeded = (state.code == 0).takeUnless { state.stopped })
        }
    }

    /** ■ shows while its process runs, or while its install waits on Android. */
    fun stoppable(status: RunStatus): Boolean = status is RunStatus.Running || status == RunStatus.Installing

    /** The workspace's configurations: its Android apps, `app` first and then by name, then its saved commands as saved. */
    fun configurations(apps: List<AndroidProject>, saved: List<RunConfiguration.Command>): List<RunConfiguration> =
        apps.sortedWith(compareBy<AndroidProject> { it.module != "app" }.thenBy { it.module }).map(RunConfiguration::AndroidApp) + saved
}
