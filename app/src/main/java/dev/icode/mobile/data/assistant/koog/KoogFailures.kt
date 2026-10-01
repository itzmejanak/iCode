package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.agent.exception.AIAgentStuckInTheNodeException
import ai.koog.http.client.KoogHttpClientException
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.domain.AssistantProviderKind
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException
import org.json.JSONObject

/**
 * What stopped a turn, in the words the page shows (§6). The provider's own error message is kept
 * when nothing plainer fits; a key is never part of one.
 */
internal object KoogFailures {
    fun explain(failure: Throwable, connection: AssistantConnection): AssistantTurnException {
        val chain = generateSequence(failure) { it.cause }.take(MAX_DEPTH).toList()
        // iCode's own reason, however deep Koog's runner wrapped it.
        chain.firstNotNullOfOrNull { it as? AssistantTurnException }?.let { return it }
        val host = host(connection)
        val message = chain.firstNotNullOfOrNull { cause ->
            when (cause) {
                is KoogHttpClientException -> http(cause, connection)
                // The standard agent's own limit (§12 R1).
                is AIAgentMaxNumberOfIterationsReachedException -> "Stopped at the step limit — send a message to continue"
                // Its only way to stick: a reply with neither text nor a tool call.
                is AIAgentStuckInTheNodeException -> "The model sent an empty reply — send a message to continue"
                // OkHttp's refusal of plain http:// under Android's network security config.
                is UnknownServiceException -> "Android blocks plain http:// to $host — use https://, or a server on this phone (127.0.0.1)"
                is UnknownHostException -> "Unable to reach $host — check the address and the connection"
                is ConnectException -> "Unable to connect to $host — is the server running?"
                is SocketTimeoutException -> "$host did not answer in time"
                is SSLException -> "A secure connection to $host failed"
                else -> null
            }
        } ?: "The assistant stopped: ${failure.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: failure.javaClass.simpleName}"
        return AssistantTurnException(message, failure)
    }

    private fun http(failure: KoogHttpClientException, connection: AssistantConnection): String {
        val said = providerMessage(failure.errorBody)
        val lower = (said ?: failure.errorBody ?: "").lowercase()
        return when {
            failure.statusCode == 401 || failure.statusCode == 403 -> "The provider refused the API key — check it in Assistant settings"
            failure.statusCode == 404 || ("model" in lower && ("not found" in lower || "does not exist" in lower)) ->
                "The provider has no model “${connection.model}”"
            failure.statusCode == 429 -> "The provider is limiting requests — try again shortly" + (said?.let { ": $it" } ?: "")
            CONTEXT_WORDS.any { it in lower } -> "This chat is longer than the model accepts — compact it or start a new chat"
            said != null -> "The provider answered ${failure.statusCode ?: "an error"}: $said"
            else -> "The provider answered ${failure.statusCode ?: "an error"}"
        }
    }

    /** `{"error": {"message": …}}` and its variants, as OpenAI, Anthropic, Google and most others write it. */
    private fun providerMessage(body: String?): String? = runCatching {
        val json = JSONObject(body ?: return null)
        val error = json.opt("error")
        when (error) {
            is JSONObject -> error.optString("message")
            is String -> error
            else -> json.optString("message")
        }.trim().take(MAX_MESSAGE).ifEmpty { null }
    }.getOrNull()

    private fun host(connection: AssistantConnection): String = connection.baseUrl
        ?.let { runCatching { URI(it).host }.getOrNull() }
        ?: when (connection.kind) {
            AssistantProviderKind.OpenAI -> "api.openai.com"
            AssistantProviderKind.Anthropic -> "api.anthropic.com"
            AssistantProviderKind.Google -> "generativelanguage.googleapis.com"
            AssistantProviderKind.OpenRouter -> "openrouter.ai"
            AssistantProviderKind.Ollama, AssistantProviderKind.OpenAICompatible -> connection.kind.label
        }

    private const val MAX_DEPTH = 8
    private const val MAX_MESSAGE = 300
    private val CONTEXT_WORDS = listOf("context length", "context_length", "maximum context", "prompt is too long", "too many tokens")
}
