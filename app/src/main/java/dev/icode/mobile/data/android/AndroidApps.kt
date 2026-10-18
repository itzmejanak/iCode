package dev.icode.mobile.data.android

import dev.icode.mobile.data.runtime.RuntimeExecution
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AndroidAppRun
import dev.icode.mobile.domain.AndroidAppStep
import dev.icode.mobile.domain.AndroidProject
import dev.icode.mobile.domain.GradleIssues
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ManagedProcess
import dev.icode.mobile.domain.ManagedProcessState
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.Severity
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Build & Run (docs/android-toolchain-plan.md §4.4), one action for the Explorer's ▶ and the
 * assistant's `run_android_app`: the project's own Gradle wrapper builds the debug APK as a managed
 * process — its output is that process's tab, Stop is its Stop — and when it ends with 0 the APK
 * is installed ([ApkInstaller]) and opened. What the build printed as problems goes to Problems.
 */
internal class AndroidApps(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val layout: RuntimeLayout,
    private val execution: RuntimeExecution,
    private val apks: ApkInstaller,
) {
    /** What follows the build in hand: its problems, then the install. */
    private var finishing: Job? = null

    /** Follows the open workspace: whether it holds an Android app, read again as its files change. */
    fun start() {
        scope.launch {
            stateHolder.snapshot.map { it.activeWorkspaceLocation to it.files }.distinctUntilChanged().collect { (location, _) ->
                val project = location?.let { AndroidProjects.find(File(it), it) }
                stateHolder.update { snapshot ->
                    // A build's problems are its workspace's: another workspace opens without them.
                    val elsewhere = snapshot.androidApps.run?.workspace?.let { it != location } == true
                    snapshot.copy(
                        androidApps = snapshot.androidApps.copy(project = project),
                        diagnostics = if (elsewhere) emptyList() else snapshot.diagnostics,
                    )
                }
            }
        }
    }

    fun handle(intent: ICodeIntent.AndroidAppIntent) {
        when (intent) {
            ICodeIntent.BuildAndRunAndroidApp -> scope.launch {
                val workspace = stateHolder.current.activeWorkspaceLocation ?: return@launch
                buildAndRun(workspace).onFailure { publish(AndroidAppRun(workspace, AndroidAppStep.Building, failure = it.message)) }
            }
            ICodeIntent.ConfirmAndroidAppInstall -> apks.confirm()
            // A failure read goes; an install waiting on Android's confirmation is abandoned. A build is stopped as a process.
            ICodeIntent.DismissAndroidAppRun -> {
                val run = stateHolder.current.androidApps.run ?: return
                if (run.failure == null && run.step != AndroidAppStep.Installing) return
                if (run.failure == null) finishing?.cancel()
                stateHolder.update { it.copy(androidApps = it.androidApps.copy(run = null)) }
            }
        }
    }

    /** Starts [workspace]'s Build & Run: the build's process, or why it did not start. */
    suspend fun buildAndRun(workspace: String): Result<ManagedProcess> {
        val project = AndroidProjects.find(File(workspace), workspace)
            ?: return refuse("This workspace holds no Android app: a Gradle build with an application module")
        if (!stateHolder.current.runtimeTools.isInstalled(RuntimeTools.AndroidSdk)) {
            return refuse("Install the Android SDK first, in Settings › Runtime Environment › Runtime Tools")
        }
        if (stateHolder.current.androidApps.run?.let { it.workspace == workspace && it.running } == true) {
            return refuse("Build & Run is already building this app")
        }
        val directory = layout.workspaces.guestOf(workspace) ?: return refuse("$workspace is not in iCode's workspaces")
        val name = "Build ${project.module.ifEmpty { File(workspace).name }}"
        // The project's own wrapper, so its own Gradle; bash runs it whether or not it is executable.
        val command = "bash ./gradlew --console=plain ${project.task}"
        val started = System.currentTimeMillis()
        val process = execution.processes.start(workspace, directory, command, name, execution.environment.current().variables)
            .getOrElse { return Result.failure(it) }
        stateHolder.update { it.copy(diagnostics = emptyList()) }
        publish(AndroidAppRun(workspace, AndroidAppStep.Building, process.id))
        finishing = scope.launch { finish(project, process, started, directory) }
        return Result.success(process)
    }

    /** Once the build ends: its problems, then the APK installed and opened when it succeeded. */
    private suspend fun finish(project: AndroidProject, process: ManagedProcess, started: Long, directory: String) {
        val ended = stateHolder.snapshot.map { snapshot -> snapshot.processes.firstOrNull { it.id == process.id }?.state }
            .first { it != ManagedProcessState.Running } as? ManagedProcessState.Ended
        val log = File(layout.rootfs, execution.processes.logOf(process.id).removePrefix("/"))
        val issues = if (log.isFile) GradleIssues.parse(log.readText(), directory) else emptyList()
        stateHolder.update { it.copy(diagnostics = issues) }
        val errors = issues.count { it.severity == Severity.Error }
        val failure = when {
            ended == null -> "The build's process is gone"
            ended.stopped -> "The build was stopped"
            ended.code != 0 -> "The build failed" + (if (errors > 0) " with $errors ${if (errors == 1) "error" else "errors"}, listed in Problems" else ": its output says why")
            else -> null
        }
        if (failure != null) return publish(AndroidAppRun(project.workspace, AndroidAppStep.Building, process.id, failure))
        publish(AndroidAppRun(project.workspace, AndroidAppStep.Installing, process.id))
        try {
            val metadata = AndroidProjects.metadata(File(project.workspace), project)
            if (!metadata.isFile || metadata.lastModified() < started) throw IOException("The build wrote no debug APK where Gradle writes one")
            val (applicationId, apk) = AndroidProjects.apk(metadata)
            apks.install(apk)
            apks.open(applicationId)
            publish(AndroidAppRun(project.workspace, AndroidAppStep.Opened, process.id))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            publish(AndroidAppRun(project.workspace, AndroidAppStep.Installing, process.id, error.message ?: "The app could not be installed"))
        }
    }

    private fun publish(run: AndroidAppRun) = stateHolder.update { it.copy(androidApps = it.androidApps.copy(run = run)) }

    private fun refuse(reason: String): Result<ManagedProcess> = Result.failure(IOException(reason))
}
