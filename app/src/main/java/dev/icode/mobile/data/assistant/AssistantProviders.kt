package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.AssistantAvailable
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantProvider
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSettings
import dev.icode.mobile.domain.WorkbenchIntent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The assistant's providers and the chat page's choices (§13 P1–P5), as the screens change them:
 * each kept by [store], then shown with every provider's models as [engine] knows them.
 */
internal class AssistantProviders(
    private val workbench: WorkbenchStateHolder,
    private val scope: CoroutineScope,
    private val engine: AssistantEngine,
    private val store: AssistantProviderStore,
    private val page: AssistantPage,
) {
    /** A server being asked for its models: the newest asking replaces the one before. */
    private var listing: Job? = null

    /** The settings as saved, each provider's models as the engine knows them. */
    fun settings(): AssistantSettings {
        val stored = store.settings()
        return AssistantSettings(
            providers = stored.providers.map { provider ->
                AssistantProvider(provider.id, provider.kind, provider.name, provider.baseUrl, provider.hasKey, provider.models.map { engine.model(provider.kind, it) })
            },
            choice = stored.choice,
            askBeforeChanges = stored.askBeforeChanges,
            effort = stored.effort,
            mode = stored.mode,
        )
    }

    /** What the screens show of the settings, as saved now. */
    fun shown() {
        val settings = settings()
        page.state { it.copy(settings = settings) }
    }

    fun choose(choice: AssistantChoice) = remember { store.choose(choice) }

    /** Another provider's page, or a new one's: what was listed for the last one is not its. */
    fun edit(id: String) = page.state { it.copy(editing = id, available = null) }

    suspend fun save(intent: WorkbenchIntent.SaveAssistantProvider) {
        val saved = settings().providers.firstOrNull { it.id == intent.id }
        val keySaved = saved?.hasKey == true && saved.kind == intent.kind
        AssistantRules.providerProblem(intent.kind, intent.name, intent.baseUrl, !intent.key.isNullOrBlank(), keySaved, intent.models)
            ?.let { return workbench.message(it) }
        val stored = runCatching {
            store.saveProvider(intent.id, intent.kind, intent.name, AssistantRules.storedBaseUrl(intent.kind, intent.baseUrl), intent.key, intent.models)
        }
        stored.exceptionOrNull()?.let { return workbench.message(it.message ?: "Unable to save the provider") }
        shown()
        page.state { it.copy(editing = stored.getOrNull(), available = null) }
        workbench.message("${intent.name.trim()} saved")
    }

    fun remove(id: String) = remember {
        store.removeProvider(id)
        page.state { it.copy(editing = null, available = null) }
    }

    /**
     * Asks a server for its models, with the address and key as typed — a key not typed is the
     * provider's saved one, while its kind is the same.
     */
    fun list(intent: WorkbenchIntent.ListAssistantModels) {
        val saved = settings().providers.firstOrNull { it.id == intent.provider }
        val key = intent.key?.takeIf { it.isNotBlank() }
            ?: store.connection(intent.provider).takeIf { saved?.kind == intent.kind }?.apiKey
        val problem = when {
            intent.kind.asksBaseUrl && intent.baseUrl.isBlank() -> "Enter the server's address"
            intent.kind.needsKey && key == null -> "Enter the API key to list its models"
            else -> null
        }
        if (problem != null) return page.state { it.copy(available = AssistantAvailable(intent.provider, loading = false, failure = problem)) }
        val connection = AssistantConnection(intent.kind, AssistantRules.storedBaseUrl(intent.kind, intent.baseUrl), key)
        listing?.cancel()
        page.state { it.copy(available = AssistantAvailable(intent.provider)) }
        listing = scope.launch {
            val available = try {
                AssistantAvailable(intent.provider, loading = false, models = engine.available(connection))
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (refused: AssistantTurnException) {
                AssistantAvailable(intent.provider, loading = false, failure = refused.message)
            } catch (unexpected: Exception) {
                BackendLog.w(TAG, "Unable to list models (${unexpected.javaClass.simpleName})")
                AssistantAvailable(intent.provider, loading = false, failure = "The provider's models could not be listed")
            }
            // Only for the page still on that provider.
            page.state { if (it.editing == intent.provider) it.copy(available = available) else it }
        }
    }

    /** A choice kept, then shown; a failure said. */
    fun remember(change: () -> Unit) {
        runCatching(change).exceptionOrNull()?.let { failure ->
            scope.launch { workbench.message(failure.message ?: "Unable to save the assistant's settings") }
            return
        }
        shown()
    }

    private companion object {
        const val TAG = "AssistantProviders"
    }
}
