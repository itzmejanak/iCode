package dev.icode.mobile.data.run

import android.content.Context
import dev.icode.mobile.data.android.AndroidApps
import dev.icode.mobile.data.android.AndroidBuilds
import dev.icode.mobile.data.android.AndroidProjects
import dev.icode.mobile.data.android.ApkInstaller
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.RuntimeExecution
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeProcesses
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AndroidAppStep
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ManagedProcess
import dev.icode.mobile.domain.RunConfiguration
import dev.icode.mobile.domain.RunRecord
import dev.icode.mobile.domain.RunRules
import dev.icode.mobile.domain.notices.RunNotices
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The Terminal panel's Run tab (docs/runtime-and-terminal.md): the open workspace's configurations —
 * its Android apps, then the commands saved — and each run's record. Every run is a managed
 * process; the Run tab and the assistant's `run_android_app` both start runs here, and both save
 * and delete commands here (the assistant's `save_run_command`, `delete_run_command`).
 */
internal class RunCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val processes: RuntimeProcesses,
    /** The login environment a saved command starts with. */
    private val environment: suspend () -> Map<String, String>,
    /** A workspace's top, as the runtime names it. */
    private val guestOf: (String) -> String?,
    private val android: AndroidBuilds,
    private val commands: RunCommandStore,
) {
    constructor(stateHolder: ICodeStateHolder, scope: CoroutineScope, layout: RuntimeLayout, execution: RuntimeExecution, context: Context) : this(
        stateHolder,
        scope,
        execution.processes,
        { execution.environment.of(GuestContext.Command).variables },
        layout.workspaces::guestOf,
        AndroidApps(stateHolder, layout, execution, ApkInstaller(context)),
        RunCommandStore(File(context.filesDir, "run-commands.json")),
    )

    /** What follows each Android build, by workspace and configuration: an install waiting on Android is cancelled through it. */
    private val following = mutableMapOf<Pair<String, String>, Job>()

    /** The workspace whose build the Problems panel lists. */
    @Volatile private var problemsOf: String? = null

    /** Follows the open workspace: its configurations, read again as its files change. */
    fun start() {
        scope.launch {
            stateHolder.snapshot.map { it.activeWorkspaceLocation to it.files }.distinctUntilChanged().collect { (location, _) ->
                val configurations = location?.let(::configurationsOf).orEmpty()
                // A build's problems are its workspace's: another workspace opens without them.
                val elsewhere = problemsOf != null && problemsOf != location
                if (elsewhere) problemsOf = null
                stateHolder.update { snapshot ->
                    snapshot.copy(
                        run = snapshot.run.copy(configurations = configurations),
                        diagnostics = if (elsewhere) emptyList() else snapshot.diagnostics,
                    )
                }
            }
        }
    }

    /** The Run tab's intents, for the open workspace. */
    fun handle(intent: ICodeIntent.RunIntent) {
        val workspace = stateHolder.current.activeWorkspaceLocation
        when (intent) {
            ICodeIntent.ConfirmAndroidAppInstall -> android.confirmInstall()
            is ICodeIntent.StartRun -> scope.launch { startRun(intent) }
            is ICodeIntent.StopRun -> workspace?.let { stop(it, intent.configuration) }
            is ICodeIntent.SaveRunCommand -> workspace?.let {
                scope.launch {
                    runCatching { saveCommand(it, intent.id, intent.name, intent.command) }
                        .onFailure { error -> stateHolder.tell(RunNotices.commandNotSaved(stateHolder.current, intent.name), "run", error) }
                }
            }
            is ICodeIntent.DeleteRunCommand -> workspace?.let {
                scope.launch {
                    runCatching { deleteCommand(it, intent.id) }
                        .onFailure { error -> stateHolder.tell(RunNotices.commandNotDeleted(stateHolder.current), "run", error) }
                }
            }
        }
    }

    /** The Run tab's ▶, run until its process has started (or failed to): the gateway shows it busy until then. */
    suspend fun startRun(intent: ICodeIntent.StartRun) {
        stateHolder.current.activeWorkspaceLocation?.let { start(it, intent.configuration) }
    }

    /** [workspace]'s configurations, as its Run tab lists them: its Android apps, then its saved commands. */
    fun configurationsOf(workspace: String): List<RunConfiguration> =
        RunRules.configurations(AndroidProjects.apps(File(workspace), workspace), commands.of(workspace))

    /**
     * Saves a command for [workspace] — new when [id] is null, else that one changed — and lists it on
     * its Run tab when it is open. Throws when the command is blank or [id] names none.
     */
    fun saveCommand(workspace: String, id: String?, name: String, command: String): RunConfiguration.Command =
        commands.save(workspace, id, name, command).also { listAgain(workspace) }

    /** Deletes [workspace]'s saved command [id], and its last run's record. Throws when [id] names none. */
    fun deleteCommand(workspace: String, id: String) {
        commands.delete(workspace, id)
        stateHolder.update { snapshot ->
            snapshot.copy(run = snapshot.run.copy(records = snapshot.run.records.filterNot { it.workspace == workspace && it.configuration == id }))
        }
        listAgain(workspace)
    }

    /** Starts [configuration] of [workspace]: its process, or why it did not start — said on its row too. */
    suspend fun start(workspace: String, configuration: String): Result<ManagedProcess> {
        val found = configurationsOf(workspace).firstOrNull { it.id == configuration }
            ?: return refuse("It is no longer in this workspace")
        val previous = stateHolder.current.run.recordOf(workspace, configuration)
        val previousProcess = previous?.process?.let(processes::info)
        if (previousProcess?.running == true || (previous?.androidStep == AndroidAppStep.Installing && previous.failure == null)) {
            return refuse("${found.name} is running already")
        }
        // One configuration keeps one output: the last run's goes.
        previousProcess?.let { processes.forget(it.id) }
        val key = workspace to configuration
        val started: Result<ManagedProcess> = when (found) {
            is RunConfiguration.AndroidApp -> android.buildAndRun(found.project) { change -> update(workspace, configuration, change) }
                .map { run ->
                    problemsOf = workspace
                    record(RunRecord(workspace, configuration, run.process.id))
                    val job = scope.launch { run.follow() }
                    synchronized(following) { following[key] = job }
                    job.invokeOnCompletion { synchronized(following) { if (following[key] == job) following.remove(key) } }
                    run.process
                }
            is RunConfiguration.Command -> guestOf(workspace)
                ?.let { top -> processes.start(workspace, top, found.command, found.name, environment()) }
                ?.onSuccess { record(RunRecord(workspace, configuration, it.id)) }
                ?: refuse("$workspace is not in iCode's workspaces")
        }
        started.onFailure { record(RunRecord(workspace, configuration, null, failure = it.message ?: "It did not start")) }
        return started
    }

    /** The assistant's `run_android_app`: [workspace]'s first Android app, as the Run tab lists it. */
    suspend fun startAndroidApp(workspace: String): Result<ManagedProcess> {
        val app = configurationsOf(workspace).firstOrNull { it is RunConfiguration.AndroidApp }
            ?: return refuse("This workspace holds no Android app: a Gradle build with an application module")
        return start(workspace, app.id)
    }

    /** Its process is stopped; an install waiting on Android is abandoned. */
    private fun stop(workspace: String, configuration: String) {
        val record = stateHolder.current.run.recordOf(workspace, configuration) ?: return
        val process = record.process?.let(processes::info)
        if (process?.running == true) {
            processes.stop(process.id)
        } else if (record.androidStep == AndroidAppStep.Installing && record.failure == null) {
            synchronized(following) { following.remove(workspace to configuration) }?.cancel()
            update(workspace, configuration) { it.copy(failure = "The install was cancelled") }
        }
    }

    /** A workspace renamed: its runs and its commands follow it. */
    fun workspaceMoved(from: String, to: String) {
        commands.move(from, to)
        synchronized(following) { following.keys.filter { it.first == from }.forEach { key -> following.remove(key)?.let { following[to to key.second] = it } } }
        stateHolder.update { snapshot ->
            snapshot.copy(run = snapshot.run.copy(records = snapshot.run.records.map { if (it.workspace == from) it.copy(workspace = to) else it }))
        }
    }

    /** A workspace deleted: its runs and its commands go. */
    fun workspaceForgotten(location: String) {
        commands.forget(location)
        synchronized(following) { following.keys.filter { it.first == location }.mapNotNull(following::remove) }.forEach(Job::cancel)
        stateHolder.update { snapshot -> snapshot.copy(run = snapshot.run.copy(records = snapshot.run.records.filterNot { it.workspace == location })) }
    }

    private fun listAgain(workspace: String) {
        if (stateHolder.current.activeWorkspaceLocation != workspace) return
        val configurations = configurationsOf(workspace)
        stateHolder.update { it.copy(run = it.run.copy(configurations = configurations)) }
    }

    /** [record] replaces the configuration's last one. */
    private fun record(record: RunRecord) = stateHolder.update { snapshot ->
        val others = snapshot.run.records.filterNot { it.workspace == record.workspace && it.configuration == record.configuration }
        snapshot.copy(run = snapshot.run.copy(records = others + record))
    }

    private fun update(workspace: String, configuration: String, change: (RunRecord) -> RunRecord) = stateHolder.update { snapshot ->
        snapshot.copy(
            run = snapshot.run.copy(
                records = snapshot.run.records.map { if (it.workspace == workspace && it.configuration == configuration) change(it) else it },
            ),
        )
    }

    private fun refuse(reason: String): Result<ManagedProcess> = Result.failure(IOException(reason))
}
