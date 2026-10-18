package dev.icode.mobile.data.runtime

import android.os.FileObserver
import android.system.Os
import android.system.OsConstants
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.GpgAnswer
import dev.icode.mobile.domain.GpgIntent
import dev.icode.mobile.domain.GpgRequest
import dev.icode.mobile.domain.GpgRequests
import dev.icode.mobile.domain.RuntimeState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Between the guest's gpg and iCode (docs/gpg-signing-plan.md). iCode's guest script ([GuestGpg])
 * hands over each request as `<pid>.request` in [GuestGpg.REQUESTS], beside the pipe `<pid>.answer`
 * it reads one line from. Asked for an agent, iCode starts gpg-agent in a PRoot session of its own,
 * without `--kill-on-exit`: it outlives the command that needed it and keeps the passphrase for as
 * long as gpg-agent's cache says. Asked a question, iCode shows it until the dialog answers, or until
 * gpg-agent takes it back and the request goes.
 */
internal class GpgBridge(private val stateHolder: ICodeStateHolder, private val scope: CoroutineScope, private val layout: RuntimeLayout) {
    private val directory = File(layout.rootfs, GuestGpg.REQUESTS)
    private val agents = mutableListOf<RuntimeProcess>()
    @Volatile private var observer: FileObserver? = null

    // From the start it follows the runtime: requests are read while it is ready, and a rebuild ends the agent first.
    init {
        scope.launch {
            stateHolder.snapshot.map { it.runtime is RuntimeState.Ready }.distinctUntilChanged().collect { ready -> if (ready) watch() else stop() }
        }
    }

    fun handle(intent: GpgIntent) = when (intent) {
        // Taken back first: answered, the same pinentry may ask again at once — a wrong passphrase — under the same ID.
        is GpgIntent.Answer -> {
            withdraw(intent.id)
            reply(intent.id, GpgRequests.answerLine(intent.answer))
        }
    }

    private fun watch() {
        directory.mkdirs()
        // What an earlier iCode process was asked went with it, and so did whoever asked.
        directory.listFiles().orEmpty().forEach(File::delete)
        observer = object : FileObserver(directory, MOVED_TO or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                val id = path?.takeIf { it.endsWith(REQUEST) }?.removeSuffix(REQUEST) ?: return
                if (event and DELETE != 0) withdraw(id) else read(id)
            }
        }.also { it.startWatching() }
    }

    private fun stop() {
        observer?.stopWatching()
        observer = null
        // PRoot passes SIGTERM on to gpg-agent, and ends it two seconds later if it is still there.
        synchronized(agents) { agents.toList() }.forEach(RuntimeProcess::terminate)
        stateHolder.update { it.copy(gpgPrompts = emptyList()) }
    }

    private fun read(id: String) {
        val text = runCatching { File(directory, id + REQUEST).readText() }.getOrNull() ?: return
        when (val request = GpgRequests.parse(id, text)) {
            is GpgRequest.StartAgent -> startAgent(id, request.homedir)
            is GpgRequest.Ask -> stateHolder.update { snapshot ->
                snapshot.copy(gpgPrompts = snapshot.gpgPrompts.filterNot { it.id == id } + request.prompt)
            }
            null -> reply(id, GpgRequests.answerLine(GpgAnswer.Cancel))
        }
    }

    /**
     * gpg waits for the script to exit, then connects. Its status is gpg-agent's own check once the
     * start is done: 0 when an agent answers — a second start exits 2 while one already runs.
     */
    private fun startAgent(id: String, homedir: String) {
        val home = shellQuote(homedir)
        val script = "gpg-agent --homedir $home --daemon >/dev/null 2>&1; gpg-agent --homedir $home >/dev/null 2>&1; " +
            "echo \$? 1<>${shellQuote("/${GuestGpg.REQUESTS}/$id$ANSWER")}"
        val agent = runCatching {
            RuntimeProcess.start(layout.prootCommand(listOf("/bin/sh", "-c", script)), layout.prootEnvironment(), layout.runtimeRoot.absolutePath)
        }.getOrElse {
            BackendLog.w(TAG, "gpg-agent could not be started (${it.javaClass.simpleName})")
            return reply(id, "1")
        }
        synchronized(agents) { agents += agent }
        scope.launch(Dispatchers.IO) {
            runCatching { agent.input.close() }
            // Only PRoot writes here; the stream ends when the agent does.
            runCatching {
                agent.output.use { input ->
                    val buffer = ByteArray(BUFFER)
                    while (input.read(buffer) >= 0) Unit
                }
            }
            runCatching { agent.waitFor() }
            agent.destroy()
            synchronized(agents) { agents -= agent }
        }
    }

    /** [line] into [id]'s pipe, without waiting: whoever asked may be gone, and the pipe with them. */
    private fun reply(id: String, line: String) {
        runCatching {
            val pipe = Os.open(File(directory, id + ANSWER).path, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK, 0)
            try {
                val bytes = (line + "\n").toByteArray()
                Os.write(pipe, bytes, 0, bytes.size)
            } finally {
                Os.close(pipe)
            }
        }.onFailure { BackendLog.w(TAG, "A gpg answer found nobody waiting (${it.javaClass.simpleName})") }
    }

    private fun withdraw(id: String) = stateHolder.update { snapshot -> snapshot.copy(gpgPrompts = snapshot.gpgPrompts.filterNot { it.id == id }) }

    private companion object {
        const val TAG = "gpg"
        const val REQUEST = ".request"
        const val ANSWER = ".answer"
        const val BUFFER = 1024
    }
}
