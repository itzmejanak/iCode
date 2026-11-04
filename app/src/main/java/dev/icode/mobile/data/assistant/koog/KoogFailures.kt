package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.agent.exception.AIAgentStuckInTheNodeException
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.streaming.IncompleteStreamException
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantStepLimitException
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantRetryKind
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import org.json.JSONArray
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
        // The standard agent's own limit (§12 R1): the turn asks whether it goes on.
        if (chain.any { it is AIAgentMaxNumberOfIterationsReachedException }) return AssistantStepLimitException()
        val host = host(connection)
        // Koog's clients wrap what failed on the way to and from the server; a file read is not that.
        val fromServer = chain.any { it is LLMClientException || it is KoogHttpClientException }
        // A stream that broke after it began: Ktor wraps what broke with the stream's own 200 — which
        // Koog carries as the failure's status — and says nothing of the answer. What broke is deeper
        // (§3.13). Ktor's own refusals of a stream (another status, not an event stream) carry no cause.
        val broke = chain.indexOfFirst { it is SSEClientException && it.cause != null }
        val reasons = if (broke < 0) chain else chain.drop(broke + 1)
        // Its types only: a parser's message quotes the reply.
        if (broke >= 0) BackendLog.w(TAG, "A stream broke after it began: ${reasons.joinToString(" ← ") { it.javaClass.simpleName }}")
        val message = reasons.firstNotNullOfOrNull { cause ->
            when (cause) {
                // Without a status the server never answered: what failed is the cause, deeper in the chain.
                is KoogHttpClientException -> cause.statusCode?.let { http(cause, connection, model) }
                is IncompleteStreamException -> CONNECTION_ENDED
                // An answer Ktor could not read as the API's: a page, or JSON of another shape.
                is NoTransformationFoundException, is ContentConvertException -> notTheApi(connection)
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
        } ?: run {
            if (broke >= 0) return AssistantTurnException("The reply stopped while it was written (${reasons.firstOrNull()?.javaClass?.simpleName ?: "no cause given"}) — try again", failure)
            val said = failure.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: failure.javaClass.simpleName
            // Listing a server's models is no turn: nothing was running to stop.
            if (model == null) "Unable to list the models: $said" else "The assistant stopped: $said"
        }
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

    /** Why a one-shot reply is not there: it was cut short, filtered, or empty. */
    class Writing(val cutShort: String, val filtered: String, val empty: String)

    val SUMMARY = Writing(SUMMARY_CUT_SHORT, SUMMARY_FILTERED, "The model sent an empty summary — try again")

    val ANSWER = Writing(
        "The model's answer was cut short at its output limit — try again",
        "The provider's content filter stopped the answer",
        "The model sent an empty answer — try again",
    )

    /** OpenRouter's word for a provider that failed after the reply began (`finish_reason: "error"`). */
    const val PROVIDER_FAILED = "The model's provider failed while writing the reply — try again"

    /** Provider-agnostic transient check — architecture fix, not per-provider. */
    fun isTransient(failure: Throwable): Boolean = retryKind(failure) != null

    fun retryKind(failure: Throwable): AssistantRetryKind? {
        if (failure is CancellationException) return null
        val chain = generateSequence(failure) { it.cause }.take(MAX_DEPTH).toList()
        if (chain.any { it is CancellationException }) return null
        val brokeIndex = chain.indexOfFirst { it is SSEClientException && it.cause != null }
        val reasons = if (brokeIndex < 0) chain else chain.drop(brokeIndex + 1)
        for (cause in reasons) {
            when (cause) {
                is KoogHttpClientException -> when {
                    cause.statusCode == 429 -> return AssistantRetryKind.RateLimited
                    cause.statusCode in 500..504 -> {
                        val msg = (cause.message.orEmpty() + " " + (cause.errorBody.orEmpty())).lowercase()
                        if ("overloaded" in msg || "temporarily" in msg) return AssistantRetryKind.Overloaded
                        return AssistantRetryKind.Server
                    }
                    cause.statusCode == null -> return AssistantRetryKind.Connection
                }
                is ConnectTimeoutException, is HttpRequestTimeoutException, is SocketTimeoutException,
                is ConnectException, is UnknownHostException -> return AssistantRetryKind.Connection
                is IncompleteStreamException -> return AssistantRetryKind.Connection
                is IOException -> if (reasons.any { it is LLMClientException || it is KoogHttpClientException }) return AssistantRetryKind.Connection
                else -> {
                    val msg = (cause.message ?: "").lowercase()
                    if ("rate limit" in msg || "too many requests" in msg || "token rate limit" in msg) return AssistantRetryKind.RateLimited
                    if ("overloaded" in msg || "temporarily" in msg) return AssistantRetryKind.Overloaded
                }
            }
            val msg = (cause.message ?: "").lowercase()
            if ("rate limit" in msg || "too many requests" in msg || "token rate limit" in msg) return AssistantRetryKind.RateLimited
            if ("overloaded" in msg || "temporarily" in msg) return AssistantRetryKind.Overloaded
        }
        // Also check wrapped provider body for rate-limit/overloaded wording (503 overloaded → Overloaded, 429 → RateLimited)
        val body = chain.filterIsInstance<KoogHttpClientException>().firstOrNull()?.errorBody?.lowercase().orEmpty()
        if ("rate limit" in body || "rate_limit" in body || "too many requests" in body) return AssistantRetryKind.RateLimited
        if ("overloaded" in body || "temporarily" in body || "temporarily overloaded" in body) return AssistantRetryKind.Overloaded
        // Fallback: message contains overloaded even without known exception type
        val msg = chain.joinToString(" ") { it.message.orEmpty().lowercase() }
        if ("rate limit" in msg && chain.any { it is KoogHttpClientException || it is LLMClientException }) return AssistantRetryKind.RateLimited
        if ("overloaded" in msg && chain.any { it is KoogHttpClientException || it is LLMClientException }) return AssistantRetryKind.Overloaded
        return null
    }

    fun retryAfterMillis(failure: Throwable): Long? {
        val body = generateSequence(failure) { it.cause }.take(MAX_DEPTH).filterIsInstance<KoogHttpClientException>().firstOrNull()?.errorBody ?: return null
        val text = body.lowercase()
        // "retry after 3s" / "retry-after: 5" / "try again in 2 seconds" — best-effort parse, 1–60s
        val match = Regex("""retry[^0-9]*([0-9]+)\s*s""").find(text) ?: Regex("""try again in ([0-9]+)""").find(text)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull()?.coerceIn(1, 60)?.let { it * 1000 }
    }

    /**
     * The server answered, but not as this API: a web page, or another service's JSON — as a server
     * does for a path it does not know. Nearly always the address, where one is asked for.
     */
    fun notTheApi(connection: AssistantConnection): String {
        val host = host(connection)
        return if (connection.kind.asksBaseUrl) {
            "$host did not answer as this API does — check the server address in the provider's settings"
        } else {
            "$host did not answer as its API does — try again shortly"
        }
    }

    /**
     * A server's refusal, said as what happened and what to do (owner's request, 2026-11-04: every kind
     * of failure in plain words, never the server's JSON): its status, then its own words where they
     * tell more.
     */
    private fun http(failure: KoogHttpClientException, connection: AssistantConnection, model: String?): String {
        val said = providerMessage(failure.errorBody)
        val lower = (said ?: failure.errorBody ?: "").lowercase()
        val status = failure.statusCode
        val host = host(connection)
        val named = model?.let { "“$it”" } ?: "This model"
        return when {
            // Koog's failure with a success status: the stream was not the API's (a page, sent with 200).
            status in SUCCESS -> notTheApi(connection)
            status == 401 || status == 403 -> "The provider refused the API key — check it in the provider's settings"
            picturesRefused(lower) -> "$named does not see pictures — turn off Reads Images for it in the provider's settings"
            TOOL_WORDS.any { it in lower } -> "$named cannot call tools, which the assistant needs — choose another model"
            model != null && (status == 404 || ("model" in lower && ("not found" in lower || "does not exist" in lower))) ->
                "$host does not serve $named for chat — it may be listed and still not answer: choose another model"
            status == 404 -> "$host has no such API — check the address in the provider's settings"
            status == 402 || CREDIT_WORDS.any { it in lower } -> "The provider's account has no credit left for this — add some with the provider"
            status == 408 || status == 504 -> "$host took too long to answer — try again"
            status == 413 -> "This request is larger than the provider takes — remove a file, or compact the chat"
            status == 429 -> "The provider is limiting requests — try again shortly" + (said?.let { ": $it" } ?: "")
            CONTEXT_WORDS.any { it in lower } -> "This chat is longer than the model accepts — compact it or start a new chat"
            status != null && status >= 500 -> "$host had a problem on its side ($status) — try again shortly"
            said != null -> "The provider refused the request: $said"
            else -> "The provider refused the request (${status ?: "no status"})"
        }
    }

    /** A server refusing a picture to a model iCode was told sees them: the step goes on without it. */
    fun refusesPictures(failure: Throwable): Boolean = generateSequence(failure) { it.cause }.take(MAX_DEPTH)
        .filterIsInstance<KoogHttpClientException>()
        .any { it.statusCode in 400..499 && picturesRefused((providerMessage(it.errorBody) ?: it.errorBody.orEmpty()).lowercase()) }

    private fun picturesRefused(lower: String) = PICTURE_WORDS.any { it in lower } && "support" in lower

    /**
     * What the server said, in whatever shape its error takes: `{"error": {"message"}}` (OpenAI,
     * Anthropic, Google, most others), `{"error": "…"}`, `{"message"}`, FastAPI's `{"detail"}` — words,
     * or a list of `{"msg"}`, and that list bare — and RFC 9457's `{"title", "detail"}`. Pydantic's
     * "Value error, " goes.
     */
    private fun providerMessage(body: String?): String? = runCatching {
        val text = body?.trim() ?: return null
        said(if (text.startsWith("[")) JSONArray(text) else JSONObject(text))
    }.getOrNull()?.removePrefix("Value error, ")?.trim()?.take(MAX_MESSAGE)?.ifEmpty { null }

    private fun said(value: Any?): String? = when (value) {
        is String -> value.ifBlank { null }
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { said(value.opt(it)) }
        is JSONObject -> SAID_KEYS.firstNotNullOfOrNull { said(value.opt(it)) }
        else -> null
    }

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
    private val SUCCESS = 200..299
    private const val MAX_DEPTH = 8
    private const val TAG = "KoogFailures"
    private const val MAX_MESSAGE = 300
    private val CONTEXT_WORDS = listOf("context length", "context_length", "maximum context", "prompt is too long", "too many tokens")
    private val PICTURE_WORDS = listOf("image", "vision", "multimodal")
    private val TOOL_WORDS = listOf("does not support tools", "tool use is not supported", "tools are not supported", "function calling is not supported", "does not support function")
    private val CREDIT_WORDS = listOf("insufficient_quota", "insufficient credit", "insufficient balance", "credit balance", "out of credits", "exceeded your current quota", "payment required")
    private val SAID_KEYS = listOf("error", "message", "msg", "detail", "title", "errors")
}
