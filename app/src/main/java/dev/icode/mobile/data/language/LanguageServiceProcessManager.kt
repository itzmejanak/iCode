package dev.icode.mobile.data.language

import dev.icode.mobile.data.runtime.PosixSignal
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeProcess
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServiceRules
import dev.icode.mobile.domain.LanguageServiceStatus
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns only guest-process lifecycle. No LSP protocol claims are made here. */
internal class LanguageServiceProcessManager(
    private val layout: RuntimeLayout,
    private val scope: CoroutineScope,
    private val onState: (String, LanguageServiceStatus, Int?, String?) -> Unit,
) {
    private data class Session(val process: RuntimeProcess, @Volatile var stopRequested: Boolean = false)
    private val sessions = mutableMapOf<String, Session>()

    fun isActive(id: String): Boolean = synchronized(sessions) { sessions.containsKey(id) }
    fun hasActiveProcesses(): Boolean = synchronized(sessions) { sessions.isNotEmpty() }

    fun start(config: LanguageServiceConfiguration, workspace: String): String? {
        if (!layout.isInstalled) return "Install the Linux runtime first"
        val folder = File(workspace)
        if (!folder.isDirectory) return "Open an available workspace first"
        val arguments = try {
            LanguageServiceRules.commandArguments(config.command)
        } catch (error: IllegalArgumentException) {
            return error.message ?: "Invalid server command"
        }
        synchronized(sessions) {
            if (sessions.containsKey(config.id)) return "This server is already running"
        }
        onState(config.id, LanguageServiceStatus.Starting, null, null)
        val process = try {
            RuntimeProcess.start(
                argv = layout.prootCommand(
                    guestCommand = listOf("/usr/bin/env") + arguments,
                    workspaceHostPath = folder.canonicalPath,
                    guestWorkingDirectory = RuntimeLayout.guestWorkspace(folder.canonicalPath),
                ),
                environment = layout.prootEnvironment(),
                workingDirectory = layout.runtimeRoot.absolutePath,
            )
        } catch (error: Exception) {
            val message = error.message ?: "Could not launch the server process"
            onState(config.id, LanguageServiceStatus.Failed, null, message)
            return message
        }
        val session = Session(process)
        synchronized(sessions) { sessions[config.id] = session }
        onState(config.id, LanguageServiceStatus.Running, process.pid, "Process running; LSP capabilities not yet verified")
        scope.launch(Dispatchers.IO) {
            val lastOutput = ByteArrayOutputStream()
            val drain = launch {
                runCatching {
                    val bytes = ByteArray(2048)
                    while (true) {
                        val count = process.output.read(bytes)
                        if (count < 0) break
                        // The native bridge currently merges stderr with stdout.
                        // Keep only a short tail for exit diagnostics, never treat it as LSP data.
                        if (lastOutput.size() + count > 2048) lastOutput.reset()
                        lastOutput.write(bytes, 0, count)
                    }
                }
            }
            val exitCode = process.waitFor()
            runCatching { process.output.close() }
            drain.join()
            synchronized(sessions) {
                if (sessions[config.id] === session) sessions.remove(config.id)
            }
            val stopped = session.stopRequested
            val output = lastOutput.toString(Charsets.UTF_8.name()).trim().takeLast(240)
            onState(
                config.id,
                if (stopped) LanguageServiceStatus.Stopped else LanguageServiceStatus.Failed,
                null,
                if (stopped) null else "Process exited ($exitCode)${if (output.isNotEmpty()) ": $output" else ""}",
            )
            process.destroy()
        }
        return null
    }

    fun stop(id: String) {
        val session = synchronized(sessions) { sessions[id] } ?: return
        session.stopRequested = true
        onState(id, LanguageServiceStatus.Stopping, session.process.pid, "Stopping process")
        session.process.terminate()
        scope.launch(Dispatchers.IO) {
            delay(2_000)
            if (synchronized(sessions) { sessions[id] === session }) session.process.sendSignal(PosixSignal.KILL)
        }
    }

    fun stopAll() {
        val active = synchronized(sessions) { sessions.values.toList() }
        active.forEach { session ->
            session.stopRequested = true
            session.process.destroy()
        }
    }
}
