package dev.icode.mobile.data.language

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.notices.LanguageNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * What the servers say and do, turned into the owner's state (docs/lsp-plan.md D10): rows, the log,
 * notices for the server's errors and warnings, problems, and edits it asks for.
 */
internal class LanguageSessionEvents(
    private val owner: LanguageServices,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val diagnostics: LanguageDiagnostics,
    private val logs: LanguageLogs,
) : LanguageServerSession.Client, LanguagePool.Events {
    override fun status(id: String, status: LanguageServiceStatus, pid: Int?, message: String?, capabilities: List<String>) {
        owner.status(id, status, pid, message, capabilities.takeIf { status == LanguageServiceStatus.Running } ?: emptyList())
    }

    /**
     * Said once, as what happened. A server not installed is shown on its page with Install in
     * Terminal, and said only when someone asked to start it: opening a file it serves is not asking.
     */
    override fun failed(id: String, kind: LanguageFailure.Kind, words: String, program: String?, asked: Boolean) {
        val name = owner.configuration(id)?.name ?: "A language server"
        owner.missing(id, program?.let { LanguageFailure.installOf(it) ?: "" })
        val at = stateHolder.current
        val notice = when (kind) {
            LanguageFailure.Kind.Missing -> if (asked) LanguageNotices.missing(at, name, words) else return
            LanguageFailure.Kind.NotStarted -> LanguageNotices.stopped(at, name, started = false, words)
            LanguageFailure.Kind.Crashed -> LanguageNotices.stopped(at, name, started = true, words)
        }
        scope.launch { stateHolder.tell(notice) }
    }

    override fun log(id: String, line: String) = logs.add(id, line)

    override suspend fun ready(session: LanguageServerSession) = owner.documents.ready(session)

    override fun gone(session: LanguageServerSession) {
        diagnostics.clear(session.config.id)
        owner.documents.gone(session)
    }

    override fun diagnostics(session: LanguageServerSession, params: JsonObject) = diagnostics.published(session, params)

    override fun log(session: LanguageServerSession, line: String) = logs.add(session.config.id, line)

    /** Errors and warnings are said; information and logs go to its log (spec 3.17 MessageType). */
    override fun message(session: LanguageServerSession, type: Int, text: String) {
        logs.add(session.config.id, text)
        val error = when (type) {
            1 -> true
            2 -> false
            else -> return
        }
        scope.launch { stateHolder.tell(LanguageNotices.said(stateHolder.current, session.config.name, error, text)) }
    }

    override fun progress(session: LanguageServerSession, words: String?) {
        if (owner.pool.ready(session.config.id) !== session) return
        owner.status(session.config.id, LanguageServiceStatus.Running, session.pid, words, null)
    }

    override fun capabilitiesChanged(session: LanguageServerSession) {
        if (owner.pool.ready(session.config.id) !== session) return
        owner.status(session.config.id, LanguageServiceStatus.Running, session.pid, null, session.capabilities.labels())
    }

    override fun refreshDiagnostics(session: LanguageServerSession) = owner.documents.pullAll(session)

    override suspend fun applyEdit(session: LanguageServerSession, edit: JsonObject): Boolean = owner.applyEdit(session, edit)
}
