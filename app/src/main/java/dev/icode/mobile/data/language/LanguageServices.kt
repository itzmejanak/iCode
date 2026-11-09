package dev.icode.mobile.data.language

import android.content.Context
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.GuestStdio
import dev.icode.mobile.data.runtime.StdioPrograms
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import dev.icode.mobile.domain.IndentType
import dev.icode.mobile.domain.LanguageFeatureIntent
import dev.icode.mobile.domain.LanguageIntent
import dev.icode.mobile.domain.LanguageService
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServiceRules
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.WorkspacePreferencesRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * The language services' one owner (docs/lsp-plan.md §1): their configurations, the servers running
 * from them, the documents those have open and what they report. The Languages pages reach it
 * through [handle], the editor through [features], the assistant through its tools
 * (LanguageServiceTools.kt): the same operations, so none of them has a path of its own.
 */
internal class LanguageServices(
    private val store: LanguageServiceSaves,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    /** Where servers start: the Linux runtime, or a test's own. */
    private val stdio: StdioPrograms,
    val uris: LspUris,
    /** The login environment servers start with, as a terminal has it. */
    private val login: suspend () -> Map<String, String>,
    private val hosts: LanguageHosts,
    private val activeRoot: () -> String?,
) {
    private val loaded = runCatching { store.load() }
    @Volatile private var configs = loaded.getOrDefault(emptyList())
    private val logs = LanguageLogs(stdio)
    val diagnostics = LanguageDiagnostics(stateHolder, scope, uris)
    private val sessions = LanguageSessionEvents(this, stateHolder, scope, diagnostics, logs)
    val pool = LanguagePool(scope, stdio, uris, ::variablesOf, sessions, sessions)
    val documents = LanguageDocumentSync(stateHolder, scope, pool, diagnostics, uris, { configs }, activeRoot)
    val features = LanguageRequests(stateHolder, documents, diagnostics, pool, uris, hosts)
    private val answers = LanguageAnswers(stateHolder, scope, features)

    fun start() {
        publish()
        documents.start()
        pool.watchIdle { documents.servedIds() }
    }

    fun configurations(): List<LanguageServiceConfiguration> = configs

    fun configuration(id: String): LanguageServiceConfiguration? = configs.firstOrNull { it.id == id }

    /** The rows Settings › Languages shows: each configuration with its server's state. */
    fun rows(): List<LanguageService> = stateHolder.current.languageServices

    /** The workspace the servers run for: the one open in the editor. */
    fun workspace(): String? = activeRoot()

    /** The configured services serving the file at [location]. */
    fun servingIds(location: String): List<String> = documents.servingIds(location) { runCatching { java.io.File(location).bufferedReader().use { it.readLine() } }.getOrNull() }

    fun hasActiveProcesses(): Boolean = pool.hasActive()

    /** On a workspace switch, and when no screen is left: every server stops, what they reported goes at once. */
    fun stopAll() {
        documents.reset()
        diagnostics.clearAll()
        pool.stopAll()
    }

    /** Adds [draft] (ID "") or changes it; a server running from it starts again with the change. */
    suspend fun save(draft: LanguageServiceConfiguration): Result<LanguageServiceConfiguration> = runCatching {
        check(loaded.isSuccess) { "Saved services could not be read; nothing was overwritten" }
        val config = draft.copy(
            name = draft.name.trim(),
            command = draft.command.trim(),
            initializationOptions = draft.initializationOptions.trim().ifBlank { "{}" },
            settings = draft.settings.trim().ifBlank { "{}" },
        )
        LanguageServiceRules.validate(config)?.let { throw IllegalArgumentException(it) }
        runCatching { LanguageJson.objectOf(config.initializationOptions) }.onFailure { throw IllegalArgumentException("Initialization options: enter a JSON object") }
        runCatching { LanguageJson.objectOf(config.settings) }.onFailure { throw IllegalArgumentException("Settings: enter a JSON object") }
        val existing = config.id.takeIf { it.isNotEmpty() }
        require(existing == null || configs.any { it.id == existing }) { "There is no language service ${config.id}" }
        require(existing != null || configs.size < LanguageServiceRules.MAX_SERVICES) { "At most ${LanguageServiceRules.MAX_SERVICES} language services" }
        val saved = if (existing == null) config.copy(id = UUID.randomUUID().toString()) else config
        val updated = if (existing == null) configs + saved else configs.map { if (it.id == saved.id) saved else it }
        store.save(updated)
        configs = updated
        stateHolder.update { state ->
            state.copy(languageServices = LanguageServiceRules.rows(updated, state.languageServices, changed = saved.id), selectedLanguageServiceId = saved.id)
        }
        if (pool.isActive(saved.id)) restart(saved.id)
        saved
    }

    suspend fun remove(id: String): Result<LanguageServiceConfiguration> = runCatching {
        check(loaded.isSuccess) { "Saved services could not be read; nothing was overwritten" }
        val removed = configs.firstOrNull { it.id == id } ?: throw IllegalArgumentException("There is no language service $id")
        val updated = configs.filterNot { it.id == id }
        store.save(updated)
        configs = updated
        pool.stop(id)
        diagnostics.clear(id)
        stateHolder.update { state ->
            state.copy(
                languageServices = state.languageServices.filterNot { it.id == id },
                selectedLanguageServiceId = state.selectedLanguageServiceId.takeUnless { it == id },
            )
        }
        removed
    }

    /** Starts [id]'s server in the open workspace, even one held after failing. */
    suspend fun start(id: String): Result<Unit> = runCatching {
        val config = configuration(id) ?: throw IllegalArgumentException("There is no language service $id")
        val workspace = activeRoot() ?: throw LanguageFailure.NoServer("Open a workspace before starting a language service")
        pool.ensure(config, workspace, asked = true)
    }

    suspend fun stop(id: String): Result<Unit> = runCatching {
        configuration(id) ?: throw IllegalArgumentException("There is no language service $id")
        pool.stop(id)
        diagnostics.clear(id)
    }

    suspend fun restart(id: String): Result<Unit> = stop(id).mapCatching { start(id).getOrThrow() }

    /** What [id]'s page shows of its log: what iCode saw of it, then its stderr's last lines. */
    fun log(id: String): List<String> = logs.of(id)

    /** A row's live state; the owner's events change it. */
    fun status(id: String, status: LanguageServiceStatus, pid: Int?, message: String?, capabilities: List<String>?) {
        stateHolder.update { state ->
            state.copy(languageServices = state.languageServices.map { service ->
                if (service.id != id) service else service.copy(
                    status = status, processId = pid, statusMessage = message,
                    capabilities = capabilities ?: service.capabilities,
                    // Starting again, it may have been installed since.
                    notInstalled = service.notInstalled && status == LanguageServiceStatus.Failed,
                    install = service.install.takeIf { status == LanguageServiceStatus.Failed },
                )
            })
        }
        if (stateHolder.current.selectedLanguageServiceId == id) refreshLog()
    }

    /** [id] failed for its program missing, [install] the way to install it ("" when iCode knows none); null: for another reason. */
    fun missing(id: String, install: String?) = stateHolder.update { state ->
        state.copy(languageServices = state.languageServices.map {
            if (it.id != id) it else it.copy(notInstalled = install != null, install = install?.ifEmpty { null })
        })
    }

    /** Types the command that installs [command]'s program in a new terminal tab: the user watches it, and answers sudo or npm there. */
    suspend fun install(command: String): Result<String> = runCatching {
        val program = LanguageServiceRules.commandArguments(command).first()
        val install = LanguageFailure.installOf(program) ?: throw IllegalArgumentException("iCode knows no install command for $program: install it in a terminal")
        if (!hosts.typeInTerminal(install)) throw IllegalStateException("No terminal could open: install the Linux runtime first")
        install
    }

    fun handle(intent: LanguageIntent) {
        when (intent) {
            is LanguageFeatureIntent -> answers.handle(intent)
            is LanguageIntent.Select -> {
                stateHolder.update { state ->
                    state.copy(
                        selectedLanguageServiceId = intent.id.takeIf { id -> state.languageServices.any { it.id == id } },
                        languageServiceLog = if (intent.id.isEmpty()) emptyList() else logs.of(intent.id),
                    )
                }
            }
            LanguageIntent.RefreshLog -> refreshLog()
            else -> scope.launch { perform(intent) }
        }
    }

    /**
     * A change the user asked for, run to its end — the gateway shows it busy until then
     * (docs/loading-plan.md §4.1) — and its failure said as a notice.
     */
    suspend fun perform(intent: LanguageIntent) {
        val (title, result) = when (intent) {
            is LanguageIntent.Save -> "Not saved" to save(intent.configuration)
            is LanguageIntent.Remove -> "Not removed" to remove(intent.id)
            is LanguageIntent.Start -> "Not started" to start(intent.id)
            is LanguageIntent.Stop -> "Not stopped" to stop(intent.id)
            is LanguageIntent.Restart -> "Not restarted" to restart(intent.id)
            is LanguageIntent.Install -> "Not installed" to install(intent.command)
            else -> return handle(intent)
        }
        result.onFailure { failure -> stateHolder.notify(NoticeTone.HeadsUp, title, LanguageFailure.wordsOf(failure)) }
    }

    /** A server asked iCode to apply a change to the workspace. */
    suspend fun applyEdit(session: LanguageServerSession, edit: JsonObject): Boolean = features.applyServerEdit(session, edit)

    private fun refreshLog() {
        val id = stateHolder.current.selectedLanguageServiceId ?: return
        stateHolder.update { it.copy(languageServiceLog = logs.of(id)) }
    }

    /** The user's login environment, as a terminal has it, with the service's own over it. */
    private suspend fun variablesOf(config: LanguageServiceConfiguration): Map<String, String> =
        login() + LanguageServiceRules.environmentOf(config.environment)

    /** Shows the saved services, or why they could not be read. */
    private fun publish() {
        stateHolder.update { it.copy(
            languageServices = LanguageServiceRules.rows(configs, shown = emptyList(), changed = null),
            languageServicesLoadError = loaded.exceptionOrNull()?.let { error -> stateHolder.failureText("language", "Unable to load saved services", error) },
        ) }
        loaded.exceptionOrNull()?.let { error -> scope.launch { stateHolder.report("language", "Unable to load language services", error) } }
    }

    companion object {
        private const val GUEST_LOGS = "/tmp/icode-lsp"

        /** The owner as the gateway makes it: edits to open files as typing, to closed ones through [files], formatting at [settings]' indentation. */
        fun create(
            appContext: Context,
            stateHolder: ICodeStateHolder,
            scope: CoroutineScope,
            layout: RuntimeLayout,
            files: WorkspaceFileSource,
            settings: WorkspacePreferencesRepository,
            activeRoot: () -> String?,
            editOpen: (String, String) -> Unit,
            changed: () -> Unit,
            typeInTerminal: suspend (String) -> Boolean,
        ) = LanguageServices(
            LanguageServiceStore(appContext), stateHolder, scope,
            GuestStdio(layout, GuestContext.LanguageServer, GUEST_LOGS, tag = "language-server"), LspUris(layout.paths),
            { layout.environment.of(GuestContext.LanguageServer).variables },
            LanguageHosts(files, editOpen, changed, indentation = {
                settings.values(activeRoot()).first().let { it.effectiveIndentSize to (it.effectiveIndentType == IndentType.Spaces) }
            }, typeInTerminal = typeInTerminal),
            activeRoot,
        )
    }
}
