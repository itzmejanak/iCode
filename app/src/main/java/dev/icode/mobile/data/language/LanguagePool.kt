package dev.icode.mobile.data.language

import dev.icode.mobile.data.runtime.StdioPrograms
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServiceRules
import dev.icode.mobile.domain.LanguageServiceStatus
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The language servers running in the open workspace (docs/lsp-plan.md §5.3): one per configuration,
 * started when a document it serves opens or the user starts it; at most [MAX_RUNNING] at once, the
 * one unused longest stopping for another; stopped after [IDLE_MILLIS] without a document; restarted
 * after a crash with growing waits, until [CRASHES] in [CRASH_WINDOW_MILLIS] leave it Failed (D6).
 */
internal class LanguagePool(
    private val scope: CoroutineScope,
    private val stdio: StdioPrograms,
    private val uris: LspUris,
    private val variables: suspend (LanguageServiceConfiguration) -> Map<String, String>,
    private val client: LanguageServerSession.Client,
    private val events: Events,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The waits before each restart after a crash, D6's; shorter in tests. */
    private val backoff: LongArray = BACKOFF_MILLIS,
) {
    /** What the owner shows and does as servers come and go. */
    interface Events {
        fun status(id: String, status: LanguageServiceStatus, pid: Int?, message: String?, capabilities: List<String>)
        fun log(id: String, line: String)

        /** [session] is ready for documents: the owner opens those it serves. */
        suspend fun ready(session: LanguageServerSession)

        /** [session] is gone: what it reported goes with it. */
        fun gone(session: LanguageServerSession)
    }

    private class Entry(val session: LanguageServerSession, @Volatile var lastUsed: Long, @Volatile var ready: Boolean = false)

    private val lock = Mutex()
    private val entries = ConcurrentHashMap<String, Entry>()
    private val crashes = ConcurrentHashMap<String, MutableList<Long>>()
    /** Failed after crashing too often, or failing to start: not started again until the user asks. */
    private val held = ConcurrentHashMap.newKeySet<String>()
    private var idleWatch: Job? = null

    val running: Collection<LanguageServerSession> get() = entries.values.map { it.session }

    fun isActive(id: String): Boolean = entries.containsKey(id)

    fun hasActive(): Boolean = entries.isNotEmpty()

    /** Its session once its handshake is done; null otherwise. */
    fun ready(id: String): LanguageServerSession? = entries[id]?.takeIf { it.ready }?.session

    /** Its session once ready, waiting up to [millis] while it starts; null when it fails, stops or takes longer. */
    suspend fun awaitReady(id: String, millis: Long): LanguageServerSession? {
        val until = clock() + millis
        while (true) {
            ready(id)?.let { return it }
            if (!entries.containsKey(id) || clock() >= until) return null
            delay(READY_POLL_MILLIS)
        }
    }

    fun used(id: String) {
        entries[id]?.lastUsed = clock()
    }

    /**
     * [config]'s server in [workspace], started if it is not running. [asked]: the user or the
     * assistant asked, so a held server starts again.
     */
    suspend fun ensure(config: LanguageServiceConfiguration, workspace: String, asked: Boolean) = lock.withLock {
        entries[config.id]?.let { entry ->
            if (entry.session.workspace == workspace) {
                entry.lastUsed = clock()
                return@withLock
            }
            remove(entry, null)
        }
        if (asked) {
            held.remove(config.id)
            crashes.remove(config.id)
        } else if (config.id in held) {
            return@withLock
        }
        if (entries.size >= MAX_RUNNING) {
            entries.values.minByOrNull { it.lastUsed }?.let { oldest -> remove(oldest, "Stopped for another language server: at most $MAX_RUNNING run at once") }
        }
        launch(config, workspace)
    }

    suspend fun stop(id: String, reason: String? = null) = lock.withLock { entries[id]?.let { remove(it, reason) } }

    /**
     * Every server stops: the workspace is switching, or no screen is left. At once, not behind the
     * lock: a server the next workspace starts after this call is never one it stops.
     */
    fun stopAll() = entries.values.toList().forEach { remove(it, null) }

    /** Stops each server that served nothing for [IDLE_MILLIS]; [inUse] says which serve an open document. */
    fun watchIdle(inUse: () -> Set<String>) {
        if (idleWatch != null) return
        idleWatch = scope.launch {
            while (true) {
                delay(IDLE_CHECK_MILLIS)
                val using = inUse()
                val now = clock()
                entries.values.filter { it.session.config.id !in using && now - it.lastUsed > IDLE_MILLIS }.forEach { entry ->
                    stop(entry.session.config.id, "Stopped after ${IDLE_MILLIS / 60_000} minutes without a file it serves")
                }
            }
        }
    }

    private fun launch(config: LanguageServiceConfiguration, workspace: String) {
        val session = LanguageServerSession(config, workspace, stdio, uris, scope, client)
        val entry = Entry(session, clock())
        entries[config.id] = entry
        events.status(config.id, LanguageServiceStatus.Starting, null, "Starting", emptyList())
        scope.launch {
            val started = runCatching {
                val argv = LanguageServiceRules.commandArguments(config.command)
                val folders = LanguageRoots.folders(workspace, LanguageServiceRules.words(config.rootMarkers))
                val began = clock()
                session.start(argv, variables(config), folders)
                events.log(config.id, "Initialized in ${clock() - began} ms")
            }
            // Stopped while it started: its stop is already under way.
            if (entries[config.id] !== entry) return@launch
            started.onFailure { failure ->
                session.kill()
                entries.remove(config.id, entry)
                held += config.id
                val words = LanguageFailure.wordsOf(failure)
                events.log(config.id, "Did not start: $words")
                events.status(config.id, LanguageServiceStatus.Failed, null, words, emptyList())
                return@launch
            }
            entry.ready = true
            events.status(config.id, LanguageServiceStatus.Running, session.pid, null, session.capabilities.labels())
            events.ready(session)
            val code = session.ended.await()
            if (session.stopRequested || entries[config.id] !== entry) return@launch
            crashed(entry, code)
        }
    }

    private suspend fun crashed(entry: Entry, code: Int) {
        val config = entry.session.config
        val tail = stdio.tail(config.id, TAIL_LINES)
        val words = LanguageFailure.Exited(code, tail).message.orEmpty()
        events.log(config.id, words)
        events.gone(entry.session)
        val now = clock()
        val recent = crashes.getOrPut(config.id) { mutableListOf() }.apply { add(now); removeAll { now - it > CRASH_WINDOW_MILLIS } }
        if (recent.size >= CRASHES) {
            lock.withLock { entries.remove(config.id, entry) }
            held += config.id
            events.status(config.id, LanguageServiceStatus.Failed, null, "Stopped after $CRASHES exits in ${CRASH_WINDOW_MILLIS / 60_000} minutes. $words", emptyList())
            return
        }
        val wait = backoff[(recent.size - 1).coerceIn(0, backoff.lastIndex)]
        events.status(config.id, LanguageServiceStatus.Starting, null, "$words. Starting again in ${(wait + 999) / 1000} s", emptyList())
        delay(wait)
        lock.withLock {
            if (entries[config.id] !== entry) return
            entries.remove(config.id)
            launch(config, entry.session.workspace)
        }
    }

    private fun remove(entry: Entry, reason: String?) {
        val id = entry.session.config.id
        entries.remove(id, entry)
        events.status(id, LanguageServiceStatus.Stopping, entry.session.pid, reason, emptyList())
        events.gone(entry.session)
        scope.launch {
            entry.session.stop()
            reason?.let { events.log(id, it) }
            // Started again meanwhile: the new one's state stands.
            if (!entries.containsKey(id)) events.status(id, LanguageServiceStatus.Stopped, null, reason, emptyList())
        }
    }

    companion object {
        const val MAX_RUNNING = 3
        const val IDLE_MILLIS = 15 * 60_000L
        private const val IDLE_CHECK_MILLIS = 60_000L
        const val CRASHES = 5
        const val CRASH_WINDOW_MILLIS = 3 * 60_000L
        val BACKOFF_MILLIS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000)
        private const val TAIL_LINES = 20
        private const val READY_POLL_MILLIS = 100L
    }
}
