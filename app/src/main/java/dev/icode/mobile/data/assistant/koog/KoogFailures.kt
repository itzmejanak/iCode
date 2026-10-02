package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.agent.exception.AIAgentStuckInTheNodeException
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.streaming.IncompleteStreamException
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantProviderKind
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import org.json.JSONObject

/**
 * What stopped a turn, in the words the page shows (§6). The provider's own error message is kept
 * when nothing plainer fits; a key is never part of one.
 */
internal object KoogFailures {
    /** [model]: what was asked of the server; null when its models were listed. */
    fun explain(failure: Throwable, connection: AssistantConnection, model: String?): AssistantTurnException {
        val chain = generateSequence(failure) { it.cause }.take(MAX_DEPTH).toList()
        // iCode's own reason, however deep Koog's runner wrapped it.
        chain.firstNotNullOfOrNull { it as? AssistantTurnException }?.let { return it }
        val host = host(connection)
        // Koog's clients wrap what failed on the way to and from the server; a file read is not that.
        val fromServer = chain.any { it is LLMClientException || it is KoogHttpClientException }
        val message = chain.firstNotNullOfOrNull { cause ->
            when (cause) {
                // Without a status the server never answered: what failed is the cause, deeper in the chain.
                is KoogHttpClientException -> cause.statusCode?.let { http(cause, connection, model) }
                is IncompleteStreamException -> CONNECTION_ENDED
                // The standard agent's own limit (§12 R1).
                is AIAgentMaxNumberOfIterationsReachedException -> "Stopped at the step limit — send a message to continue"
                // Its only way to stick: a reply with neither text nor a tool call.
                is AIAgentStuckInTheNodeException -> "The model sent an empty reply — send a message to continue"
                // OkHttp's refusal of plain http:// under Android's network security config.
                is UnknownServiceException -> "Android blocks plain http:// to $host — use https://, or a server on this phone (127.0.0.1)"
                is UnknownHostException -> "Unable to reach $host — check the address and the connection"
                // Ktor's timeouts: a ConnectException and an IOException, so they come first.
                is ConnectTimeoutException, is HttpRequestTimeoutException, is SocketTimeoutException -> "$host did not answer in time"
                is ConnectException -> "Unable to connect to $host — is the server running?"
                is SSLException -> "A secure connection to $host failed"
                // A reply that is not what the provider's API sends: a parser's words mean nothing here.
                is SerializationException -> UNREADABLE
                // A connection that broke while the reply came: the stream's, or a whole reply's.
                is IOException -> CONNECTION_ENDED.takeIf { fromServer }
                else -> null
            }
        } ?: "The assistant stopped: ${failure.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: failure.javaClass.simpleName}"
        return AssistantTurnException(message, failure)
    }

    /**
     * Said under a reply that ended at [model]'s output limit, so that it does not pass for a
     * finished one. The limit of a model iCode does not know is the user's to raise.
     */
    fun cutShort(model: AssistantModel): String =
        "The reply was cut short at the model's output limit — ask it to continue" +
            if (model.known) "" else ", or set a higher Output Limit for ${model.id} in its provider's settings"

    /** A summary cut short would replace the chat with part of one: the chat stays as it is. */
    const val SUMMARY_CUT_SHORT = "The model's summary was cut short at its output limit, so the chat was not compacted"

    /** Said under a reply its provider's content rules stopped, so that it does not pass for a finished one. */
    const val FILTERED = "The provider's content filter stopped the reply"

    const val SUMMARY_FILTERED = "The provider's content filter stopped the summary, so the chat was not compacted"

    /** OpenRouter's word for a provider that failed after the reply began (`finish_reason: "error"`). */
    const val PROVIDER_FAILED = "The model's provider failed while writing the reply — try again"

    private fun http(failure: KoogHttpClientException, connection: AssistantConnection, model: String?): String {
        val said = providerMessage(failure.errorBody)
        val lower = (said ?: failure.errorBody ?: "").lowercase()
        return when {
            failure.statusCode == 401 || failure.statusCode == 403 -> "The provider refused the API key — check it in the provider's settings"
            model != null && (failure.statusCode == 404 || ("model" in lower && ("not found" in lower || "does not exist" in lower))) ->
                "The provider has no model “$model”"
            failure.statusCode == 404 -> "${host(connection)} has no such API — check the address in the provider's settings"
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

    private const val CONNECTION_ENDED = "The connection ended before the reply was complete — try again"
    private const val UNREADABLE = "The provider's reply could not be read — try again"
    private const val MAX_DEPTH = 8
    private const val MAX_MESSAGE = 300
    private val CONTEXT_WORDS = listOf("context length", "context_length", "maximum context", "prompt is too long", "too many tokens")
}
