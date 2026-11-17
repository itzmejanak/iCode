package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.protocol.AcpRequestCancelledException
import com.agentclientprotocol.protocol.JsonRpcException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * What went wrong with a coding agent, in the words the page shows (docs/assistant-and-mcp.md): the
 * agent's own message where it gave one, and the end of its log when its process ended. Never what the
 * chat said: a failure's text is the agent's or iCode's, not the conversation's.
 */
internal object AcpFailures {
    /** ACP's "authentication required" (`-32000`): the agent asks to sign in before it goes on. */
    const val AUTH_REQUIRED = -32000

    fun needsSignIn(failure: Throwable): Boolean = failure is JsonRpcException && failure.code == AUTH_REQUIRED

    fun words(entry: AgentEntry, failure: Throwable, log: List<String> = emptyList()): String {
        val name = entry.agent.name
        val said = when (failure) {
            is TimeoutCancellationException -> "$name did not answer in time"
            is JsonRpcException -> if (failure.code == AUTH_REQUIRED) "Sign in to $name first" else "$name: ${failure.message}"
            is AcpRequestCancelledException -> "$name stopped the request"
            else -> "$name stopped: ${failure.message ?: failure.javaClass.simpleName}"
        }
        return withLog(said, log)
    }

    fun ended(entry: AgentEntry, code: Int, log: List<String>): String =
        withLog("${entry.agent.name} ended (exit $code)", log)

    private fun withLog(said: String, log: List<String>): String {
        val tail = log.filter(String::isNotBlank).takeLast(LOG_LINES)
        return if (tail.isEmpty()) said else said + "\n\n" + tail.joinToString("\n")
    }

    private const val LOG_LINES = 12
}
