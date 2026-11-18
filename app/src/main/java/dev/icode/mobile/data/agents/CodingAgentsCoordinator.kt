package dev.icode.mobile.data.agents

import android.content.Context
import dev.icode.mobile.data.assistant.acp.AgentPreferences
import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimePackageInstaller
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AgentAction
import dev.icode.mobile.domain.AgentIntent
import dev.icode.mobile.domain.AgentWork
import dev.icode.mobile.domain.CodingAgentsState
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.ToolSetupProgress
import dev.icode.mobile.domain.notices.AgentNotices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Assistant › Coding agents (docs/assistant-and-mcp.md): the agents iCode offers, as the
 * ACP registry says them — read again each time the page opens — each installed, updated or removed
 * in a job of its own, which another agent's never waits on.
 */
internal class CodingAgentsCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val registry: AgentRegistry,
    private val installs: AgentInstalls,
    /** Whether the Linux runtime is installed: agents run in it. */
    private val runtimeReady: () -> Boolean,
    /** The daily update check ([AgentUpdateWorker]), scheduled as iCode starts. */
    private val schedule: () -> Unit = {},
    /** What iCode keeps for an agent deleted with everything: its keys and sign-in ([AgentPreferences.forget]). */
    private val forget: (String) -> Unit = {},
) {
    /** Each agent's work in hand, by ID. */
    private val jobs = HashMap<String, Job>()
    private var checking: Job? = null

    /** The list last kept and what is installed, before the page first opens. */
    fun start() {
        scope.launch { state { it.copy(catalogue = registry.kept(), installed = installs.receipts.installed()) } }
        schedule()
    }

    fun handle(intent: AgentIntent) {
        when (intent) {
            AgentIntent.Check -> check()
            is AgentIntent.Install -> install(intent.id)
            AgentIntent.UpdateAll -> stateHolder.current.agents.updates.forEach { install(it.id) }
            is AgentIntent.Remove -> remove(intent.id, intent.everything)
            is AgentIntent.Cancel -> if (stateHolder.current.agents.work[intent.id]?.progress?.cancellable == true) synchronized(jobs) { jobs[intent.id] }?.cancel()
        }
    }

    /** The registry read again, then each build's download measured; a failure keeps the list last read. */
    private fun check() {
        synchronized(this) {
            if (checking?.isActive == true) return
            state { it.copy(checking = true) }
            checking = scope.launch {
                val read = runCatching { registry.read() }
                val failure = read.exceptionOrNull()?.let { stateHolder.failureText(TAG, "Couldn't read the agents list", it) }
                state { agents -> agents.copy(catalogue = read.getOrDefault(agents.catalogue), checkFailure = failure, installed = installs.receipts.installed()) }
                val sizes = read.getOrDefault(emptyList()).mapNotNull { agent -> registry.size(agent)?.let { agent.id to it } }.toMap()
                state { it.copy(checking = false, sizes = if (read.isSuccess) sizes else it.sizes) }
            }
        }
    }

    private fun install(id: String) {
        val agent = stateHolder.current.agents.catalogue.firstOrNull { it.id == id } ?: return
        if (!runtimeReady()) {
            scope.launch { stateHolder.tell(AgentNotices.needsRuntime()) }
            return
        }
        val action = if (id in stateHolder.current.agents.installed) AgentAction.Update else AgentAction.Install
        run(agent.id, AgentWork(action, ToolSetupProgress("${action.doing} ${agent.name}", cancellable = true))) {
            try {
                val installed = installs.install(agent) { progress -> state { it.copy(work = it.work + (id to AgentWork(action, progress))) } }
                stateHolder.tell(AgentNotices.installed(agent.name, installed.version))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                stateHolder.tell(AgentNotices.notInstalled(agent.name), TAG, failed)
            }
        }
    }

    private fun remove(id: String, everything: Boolean) {
        val name = nameOf(id)
        run(id, AgentWork(AgentAction.Remove, ToolSetupProgress("Removing $name", cancellable = false))) {
            runCatching {
                installs.remove(id, everything)
                if (everything) forget(id)
            }
                .onSuccess { stateHolder.tell(AgentNotices.removed(name)) }
                .onFailure { stateHolder.tell(AgentNotices.notRemoved(name), TAG, it) }
        }
    }

    /**
     * [work] on [id], unless it has some in hand: its row shows it until it ends, and what is installed
     * is read again. A job is in [jobs] exactly while its row shows its work.
     */
    private fun run(id: String, work: AgentWork, body: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                body()
            } finally {
                withContext(NonCancellable) {
                    val installed = installs.receipts.installed()
                    synchronized(jobs) {
                        jobs.remove(id)
                        state { it.copy(work = it.work - id, installed = installed) }
                    }
                }
            }
        }
        synchronized(jobs) {
            if (id in jobs) return job.cancel()
            jobs[id] = job
            state { it.copy(work = it.work + (id to work)) }
        }
        job.start()
    }

    private fun nameOf(id: String): String = stateHolder.current.agents.catalogue.firstOrNull { it.id == id }?.name ?: id

    private fun state(change: (CodingAgentsState) -> CodingAgentsState) = stateHolder.update { it.copy(agents = change(it.agents)) }

    companion object {
        private const val TAG = "agents"

        /** In the runtime's home, Node.js from the runtime's tools, npm run in the runtime. */
        fun create(context: Context, stateHolder: ICodeStateHolder, scope: CoroutineScope, packages: RuntimePackageInstaller): CodingAgentsCoordinator {
            val layout = RuntimeLayout(context)
            val guest = GuestCommand(layout, GuestContext.Command)
            val installs = AgentInstalls(
                layout.home,
                context.cacheDir,
                node = {
                    when (val outcome = packages.ensure(RuntimeTools.NodeJs)) {
                        RuntimePackageInstaller.Outcome.Ready -> null
                        RuntimePackageInstaller.Outcome.Cancelled -> "Installing Node.js was stopped"
                        is RuntimePackageInstaller.Outcome.Failed -> outcome.reason
                    }
                },
                npm = { argv -> guest.capture(null, argv, "npm").let { it.exitCode to it.stderr } },
            )
            return CodingAgentsCoordinator(
                stateHolder, scope, AgentRegistry(AgentUpdateWorker.kept(context)), installs, { layout.isInstalled },
                schedule = { AgentUpdateWorker.schedule(context) }, forget = AgentPreferences(context)::forget,
            )
        }
    }
}
