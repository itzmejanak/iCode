package dev.icode.mobile.data.language

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServiceRules
import dev.icode.mobile.domain.OpenDocument
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The editor's open files, as the servers see them (docs/lsp-plan.md §5.6, D3): followed from the
 * snapshot, so every way a file opens, changes, moves or closes — typing, a reload, the assistant, a
 * rename — reaches them alike. A server starts when a file it serves opens; changes go out
 * [QUIET_MILLIS] after typing stops, at most every [MAX_WAIT_MILLIS] while it goes on, and at once
 * before any request ([flush]).
 */
internal class LanguageDocumentSync(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val pool: LanguagePool,
    private val diagnostics: LanguageDiagnostics,
    private val uris: LspUris,
    private val configs: () -> List<LanguageServiceConfiguration>,
    private val activeRoot: () -> String?,
) {
    private val lock = Mutex()
    /** Each open file's text as last seen, by location. */
    private val seen = ConcurrentHashMap<String, OpenDocument>()
    private val changed = ConcurrentHashMap.newKeySet<String>()
    private var flushJob: Job? = null
    private var firstChangeAt = 0L
    private val pulls = LanguagePulls(scope, diagnostics)

    fun start() {
        scope.launch {
            stateHolder.snapshot.map { it.openDocuments }
                .distinctUntilChanged { old, new -> old === new }
                .collect { documents -> follow(documents) }
        }
    }

    /** The workspace is switching: nothing is open at any server. */
    fun reset() {
        seen.clear()
        changed.clear()
        flushJob?.cancel()
    }

    /** The services serving a file open now: they are not idle. */
    fun servedIds(): Set<String> = seen.values.flatMap { document -> serving(document).map { it.id } }.toSet()

    /** [session] has started: every open file it serves opens at it. */
    suspend fun ready(session: LanguageServerSession) {
        for (document in seen.values) if (serving(document).any { it.id == session.config.id }) open(session, document)
    }

    fun gone(session: LanguageServerSession) = pulls.forget(session)

    /** The server asked for its problems again (`workspace/diagnostic/refresh`). */
    fun pullAll(session: LanguageServerSession) {
        scope.launch { session.documents.opened().forEach { pulls.pull(session, it.location, it.uri) } }
    }

    /** A file was saved: the servers that have it open are told. */
    fun saved(location: String) {
        scope.launch {
            flush(location)
            pool.running.forEach { it.documents.saved(location) }
        }
    }

    /** What [location] holds now reaches every server that has it open, before a request about it. */
    suspend fun flush(location: String) {
        val document = stateHolder.current.openDocuments.firstOrNull { it.location == location } ?: return
        changed.remove(location)
        send(document)
    }

    /**
     * The ready servers serving [location], opened there if they were not; the file need not be open
     * in the editor. One that is starting is waited for up to [waitMillis].
     */
    suspend fun sessionsFor(location: String, text: () -> String?, waitMillis: Long): List<LanguageServerSession> {
        val name = location.substringAfterLast('/')
        val body = stateHolder.current.openDocuments.firstOrNull { it.location == location }?.text ?: text() ?: return emptyList()
        val languageId = LanguageServiceRules.languageIdOf(FileTypes.of(name, body.take(256).substringBefore('\n')))
        val root = activeRoot() ?: return emptyList()
        return configs().filter { LanguageServiceRules.serves(it, name, languageId) }.mapNotNull { config ->
            pool.ensure(config, root, asked = false)
            pool.awaitReady(config.id, waitMillis)?.also { session ->
                pool.used(config.id)
                if (!session.documents.isOpen(location)) uris.uriOf(location)?.let { session.documents.open(location, it, languageId, body) }
            }
        }
    }

    /** The configured services serving the file at [location], by its name and its text's first line. */
    fun servingIds(location: String, text: () -> String?): List<String> {
        val name = location.substringAfterLast('/')
        val body = stateHolder.current.openDocuments.firstOrNull { it.location == location }?.text ?: text().orEmpty()
        val languageId = LanguageServiceRules.languageIdOf(FileTypes.of(name, body.take(256).substringBefore('\n')))
        return configs().filter { LanguageServiceRules.serves(it, name, languageId) }.map { it.id }
    }

    /** A file opened at the servers for one request goes from them again, unless the editor has it open. */
    suspend fun release(location: String) {
        if (seen.containsKey(location)) return
        pool.running.forEach { it.documents.close(location) }
    }

    /** [location]'s text now at [session]: opened if it was not, else its change sent. True when anything went. */
    suspend fun openOrFlush(session: LanguageServerSession, location: String, text: () -> String?): Boolean {
        val body = stateHolder.current.openDocuments.firstOrNull { it.location == location }?.text ?: text() ?: return false
        if (!session.documents.isOpen(location)) {
            val uri = uris.uriOf(location) ?: return false
            val name = location.substringAfterLast('/')
            session.documents.open(location, uri, LanguageServiceRules.languageIdOf(FileTypes.of(name, body.take(256).substringBefore('\n'))), body)
            pulls.pull(session, location, uri)
            return true
        }
        val before = session.documents.versionOf(location)
        val after = session.documents.change(location, body)
        if (after != before) session.documents.opened().firstOrNull { it.location == location }?.let { pulls.pull(session, it.location, it.uri) }
        return after != before
    }

    private suspend fun follow(documents: List<OpenDocument>) = lock.withLock {
        val now = documents.associateBy { it.location }
        for (gone in seen.keys - now.keys) {
            seen.remove(gone)
            changed.remove(gone)
            pool.running.forEach { it.documents.close(gone) }
        }
        val root = activeRoot() ?: return@withLock
        for (document in documents) {
            val before = seen.put(document.location, document)
            when {
                before == null -> serving(document).forEach { config ->
                    pool.ensure(config, root, asked = false)
                    pool.ready(config.id)?.let { open(it, document) }
                }
                before.text !== document.text -> changed += document.location
            }
        }
        if (changed.isNotEmpty()) schedule()
    }

    private fun schedule() {
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (flushJob?.isActive != true) firstChangeAt = now
            flushJob?.cancel()
            val wait = minOf(QUIET_MILLIS, (MAX_WAIT_MILLIS - (now - firstChangeAt)).coerceAtLeast(0))
            flushJob = scope.launch {
                delay(wait)
                val due = changed.toList()
                changed.removeAll(due.toSet())
                due.forEach { location -> seen[location]?.let { send(it) } }
            }
        }
    }

    /** [document]'s text now, to every server that has it open; its problems asked for where the server is asked. */
    private suspend fun send(document: OpenDocument) {
        val text = stateHolder.current.openDocuments.firstOrNull { it.location == document.location }?.text ?: document.text
        for (session in pool.running) {
            if (session.documents.change(document.location, text) == null) continue
            session.documents.opened().firstOrNull { it.location == document.location }?.let { pulls.pull(session, it.location, it.uri) }
        }
    }

    private suspend fun open(session: LanguageServerSession, document: OpenDocument) {
        val uri = uris.uriOf(document.location) ?: return
        val text = stateHolder.current.openDocuments.firstOrNull { it.location == document.location }?.text ?: document.text
        session.documents.open(document.location, uri, languageIdOf(document), text)
        pulls.pull(session, document.location, uri)
    }

    private fun serving(document: OpenDocument): List<LanguageServiceConfiguration> {
        val languageId = languageIdOf(document)
        return configs().filter { LanguageServiceRules.serves(it, document.name, languageId) }
    }

    private fun languageIdOf(document: OpenDocument): String =
        LanguageServiceRules.languageIdOf(FileTypes.of(document.name, document.text.take(256).substringBefore('\n')))

    companion object {
        const val QUIET_MILLIS = 150L
        const val MAX_WAIT_MILLIS = 500L
    }
}
