package dev.icode.mobile.data.android

import dev.icode.mobile.data.runtime.RuntimeExecution
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AndroidAppStep
import dev.icode.mobile.domain.AndroidProject
import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.GradleIssues
import dev.icode.mobile.domain.ManagedProcess
import dev.icode.mobile.domain.ManagedProcessState
import dev.icode.mobile.domain.RunRecord
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.Severity
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** An Android app's run, started: its build's process, and what follows the build, for the caller to run. */
internal class AndroidRun(val process: ManagedProcess, val follow: suspend () -> Unit)

/** The Android kind of run, as the Run tab drives it ([AndroidApps]). */
internal interface AndroidBuilds {
    /** Starts [project]'s build; what follows it changes the run's record through [report]. */
    suspend fun buildAndRun(project: AndroidProject, report: ((RunRecord) -> RunRecord) -> Unit): Result<AndroidRun>

    /** Opens Android's confirmation of the install in hand again. */
    fun confirmInstall()
}

/**
 * Build & Run (docs/android-toolchain-plan.md §4.4), the Run tab's Android app: the project's own
 * Gradle wrapper builds the debug APK as a managed process — its output is that process's tab, Stop
 * is its Stop — and when it ends with 0 the APK is installed ([ApkInstaller]) and opened. What the
 * build printed as problems goes to Problems. It keeps no state: the run's record is the Run tab's.
 */
internal class AndroidApps(
    private val stateHolder: ICodeStateHolder,
    private val layout: RuntimeLayout,
    private val execution: RuntimeExecution,
    private val apks: ApkInstaller,
) : AndroidBuilds {
    override suspend fun buildAndRun(project: AndroidProject, report: ((RunRecord) -> RunRecord) -> Unit): Result<AndroidRun> {
        val tools = stateHolder.current.runtimeTools
        if (!tools.isInstalled(RuntimeTools.AndroidSdk)) {
            return refuse("Install the Android SDK first, in ${RuntimeTools.PAGE}")
        }
        if (project.native && !tools.buildsNative) {
            return refuse("${RuntimeTools.nativeNeed}: install them first, in ${RuntimeTools.PAGE}")
        }
        val directory = layout.workspaces.guestOf(project.workspace) ?: return refuse("${project.workspace} is not in iCode's workspaces")
        val name = "Build ${project.module.ifEmpty { File(project.workspace).name }}"
        // The project's own wrapper, so its own Gradle; bash runs it whether or not it is executable.
        val command = "bash ./gradlew --console=plain ${project.task}"
        val process = execution.processes.start(project.workspace, directory, command, name, execution.environment.current().variables)
            .getOrElse { return Result.failure(it) }
        problems(project, emptyList())
        return Result.success(AndroidRun(process) { follow(project, process, directory, report) })
    }

    override fun confirmInstall() = apks.confirm()

    /** Once the build ends: its problems, then — when it succeeded — the APK installed and opened. A build stopped by the user says so itself. */
    private suspend fun follow(
        project: AndroidProject,
        process: ManagedProcess,
        directory: String,
        report: ((RunRecord) -> RunRecord) -> Unit,
    ) {
        val ended = stateHolder.snapshot.map { snapshot -> snapshot.processes.firstOrNull { it.id == process.id }?.state }
            .first { it != ManagedProcessState.Running } as? ManagedProcessState.Ended
        val log = File(layout.rootfs, execution.processes.logOf(process.id).removePrefix("/"))
        val output = if (log.isFile) log.readText() else ""
        val issues = GradleIssues.parse(output, directory)
        problems(project, issues)
        if (ended?.stopped == true) return
        val errors = issues.count { it.severity == Severity.Error }
        val failure = when {
            ended == null -> "The build's process is gone"
            ended.code != 0 && errors > 0 -> "The build failed with $errors ${if (errors == 1) "error" else "errors"}, listed in Problems"
            ended.code != 0 -> "The build failed: " + (GradleIssues.failure(output) ?: "its output says why")
            else -> null
        }
        if (failure != null) return report { it.copy(failure = failure) }
        report { it.copy(androidStep = AndroidAppStep.Installing) }
        try {
            val (applicationId, apk) = AndroidProjects.debugApk(File(project.workspace), project)
            apks.install(apk)
            apks.open(applicationId)
            report { it.copy(androidStep = AndroidAppStep.Opened) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            report { it.copy(failure = error.message ?: "The app could not be installed") }
        }
    }

    /** Problems lists a build's problems while its workspace is the open one; the assistant may build another. */
    private fun problems(project: AndroidProject, issues: List<Diagnostic>) = stateHolder.update {
        if (it.activeWorkspaceLocation == project.workspace) it.copy(diagnostics = issues) else it
    }

    private fun refuse(reason: String): Result<AndroidRun> = Result.failure(IOException(reason))
}
