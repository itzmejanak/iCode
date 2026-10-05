package dev.icode.mobile.data.language

import android.content.Context
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServiceRules
import dev.icode.mobile.domain.ICodeIntent
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * The user's language services (S8.1): their saved configuration, the server
 * processes started from it, and the rows the Languages settings show. A
 * server runs in the active workspace, which the gateway owns.
 */
internal class LanguageServiceCoordinator(
    appContext: Context,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    runtimeLayout: RuntimeLayout,
    private val activeRoot: () -> String?,
    /** The user's login environment, as a terminal's shell has it: a server installed through a profile is found. */
    private val environment: suspend () -> Map<String, String>,
) {
    private val store = LanguageServiceStore(appContext)
    private val loaded = runCatching { store.load() }
    private var configs = loaded.getOrDefault(emptyList())
    private val processes = LanguageServiceProcessManager(runtimeLayout, scope) { id, status, pid, message ->
        stateHolder.update { state -> state.copy(languageServices = state.languageServices.map { service ->
            if (service.id == id) service.copy(
                status = status,
                processId = pid,
                statusMessage = message,
            ) else service
        }) }
    }

    /** Shows the saved services, or why they could not be read. */
    fun publish() {
        stateHolder.update { it.copy(
            languageServices = LanguageServiceRules.rows(configs, shown = emptyList(), changed = null),
            languageServicesLoadError = loaded.exceptionOrNull()?.let { stateHolder.failureText("language", "Unable to load saved services", it) },
        ) }
        loaded.exceptionOrNull()?.let { error ->
            scope.launch { stateHolder.report("language", "Unable to load language services", error) }
        }
    }

    fun hasActiveProcesses(): Boolean = processes.hasActiveProcesses()

    /** On a workspace switch, and when no screen is left: servers run in one workspace, for the editor. */
    fun stopAll() = processes.stopAll()

    fun select(id: String) {
        stateHolder.update { state ->
            state.copy(selectedLanguageServiceId = id.takeIf { selected ->
                state.languageServices.any { service -> service.id == selected }
            })
        }
    }

    suspend fun save(intent: ICodeIntent.SaveLanguageService) {
        if (stateHolder.current.languageServicesLoadError != null) {
            stateHolder.message("Saved services could not be read; configuration was not overwritten")
            return
        }
        val name = intent.name.trim()
        val command = intent.command.trim()
        val options = intent.initializationOptions.trim().ifBlank { "{}" }
        val error = LanguageServiceRules.validate(name, command, intent.patterns, options)
            ?: runCatching { LanguageServiceRules.commandArguments(command); JSONObject(options) }
                .exceptionOrNull()?.let { "Check command quoting and enter a JSON object for initialization options" }
        if (error != null) {
            stateHolder.message(error)
            return
        }
        val existingId = intent.id
        if (existingId != null && configs.none { it.id == existingId }) {
            stateHolder.message("This language service no longer exists")
            return
        }
        if (existingId != null && processes.isActive(existingId)) {
            stateHolder.message("Stop this server before editing its configuration")
            return
        }
        if (existingId == null && configs.size >= LanguageServiceRules.MAX_SERVICES) {
            stateHolder.message("Maximum ${LanguageServiceRules.MAX_SERVICES} language services")
            return
        }
        val id = existingId ?: UUID.randomUUID().toString()
        val config = LanguageServiceConfiguration(
            id, name, command, intent.patterns.trim(), intent.rootMarkers.trim(), options,
        )
        val updated = if (existingId == null) configs + config else configs.map {
            if (it.id == existingId) config else it
        }
        try {
            store.save(updated)
            configs = updated
            stateHolder.update { state -> state.copy(
                languageServices = LanguageServiceRules.rows(updated, state.languageServices, changed = id),
                selectedLanguageServiceId = id,
            ) }
        } catch (failure: Exception) {
            stateHolder.report("language", "Unable to save language service", failure)
        }
    }

    suspend fun remove(id: String) {
        if (stateHolder.current.languageServicesLoadError != null) return
        if (configs.none { it.id == id }) return
        val updated = configs.filterNot { it.id == id }
        try {
            store.save(updated)
            configs = updated
            processes.stop(id)
            stateHolder.update { state -> state.copy(
                languageServices = state.languageServices.filterNot { it.id == id },
                selectedLanguageServiceId = null,
            ) }
        } catch (failure: Exception) {
            stateHolder.report("language", "Unable to remove language service", failure)
        }
    }

    suspend fun start(id: String) {
        val config = configs.firstOrNull { it.id == id } ?: return
        val workspace = activeRoot()
        if (workspace == null) {
            stateHolder.message("Open a workspace before starting a language service")
            return
        }
        processes.start(config, workspace, environment())?.let { error ->
            stateHolder.message(error)
        }
    }

    fun stop(id: String) = processes.stop(id)

    fun restart(id: String) {
        processes.stop(id)
        scope.launch {
            repeat(30) {
                if (!processes.isActive(id)) {
                    stateHolder.mutations.withLock { start(id) }
                    return@launch
                }
                delay(100)
            }
            stateHolder.message("Server did not stop; retry after it exits")
        }
    }
}
