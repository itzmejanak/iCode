package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.networkWords
import dev.icode.mobile.domain.RuntimeTools
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpError
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/**
 * Every MCP failure in the words the pages and the chat show (docs/mcp-plan.md §9), as KoogFailures is
 * for providers. Its types only reach the log, never a server's answer or a tool's arguments.
 */
internal object McpFailures {
    /** The command a local server starts with is not in the runtime. */
    class Missing(val command: String) : Exception("$command is not in the Linux runtime")

    /** A remote server answered 401: it needs its sign-in. */
    class SignInNeeded : Exception(SIGN_IN)

    /** What a server that needs its sign-in says: its state is then [dev.icode.mobile.domain.McpStatus.SignIn]. */
    const val SIGN_IN = "Sign in needed"

    /** Why [server] could not start or answer, [log] the last lines its stderr wrote. */
    fun of(failure: Throwable, server: String, address: String? = null, log: List<String> = emptyList()): String {
        val chain = generateSequence(failure) { it.cause }.take(MAX_DEPTH).toList()
        val host = address?.let { runCatching { URI(it).host }.getOrNull() } ?: server
        chain.firstNotNullOfOrNull { networkWords(it, host) }?.let { return it }
        return when (val cause = chain.firstOrNull { it is Missing || it is SignInNeeded || it is StreamableHttpError || it is McpException } ?: failure) {
            is Missing -> missing(cause.command)
            is SignInNeeded -> SIGN_IN
            is StreamableHttpError -> when (cause.code) {
                401 -> SIGN_IN
                403 -> "$server refused: ${firstLine(cause.message) ?: "access denied"}"
                404 -> "No MCP server at this address"
                else -> "$server answered HTTP ${cause.code ?: "with an error"}"
            }
            is McpException -> when {
                cause.code == RPCError.ErrorCode.REQUEST_TIMEOUT -> "$server did not answer in time"
                MODERN.containsMatchIn(cause.message.orEmpty()) ->
                    "$server speaks a newer MCP only (${MODERN.find(cause.message.orEmpty())?.value}); iCode speaks MCP up to 2025-11-25 until its MCP library does"
                else -> "$server said: ${firstLine(cause.message) ?: "an error"}"
            }
            else -> log.lastOrNull { it.isNotBlank() }?.let { "$server stopped: ${it.trim().take(MAX_LINE)}" }
                ?: "$server stopped: ${firstLine(failure.message) ?: failure.javaClass.simpleName}"
        }
    }

    /** What a tool call that failed says to the model; a stop is never one. */
    fun call(failure: Throwable, server: String): String {
        if (failure is CancellationException) throw failure
        return of(failure, server)
    }

    private fun missing(command: String): String {
        val tool = when (command.substringAfterLast('/')) {
            "npx", "npm", "node" -> RuntimeTools.NodeJs.name
            "uvx", "uv" -> RuntimeTools.Uv.name
            "python3", "python", "pip", "pip3" -> RuntimeTools.Python.name
            else -> return "$command is not in the Linux runtime"
        }
        return "$command is not in the Linux runtime — install $tool in ${RuntimeTools.PAGE}"
    }

    private fun firstLine(text: String?): String? = text?.lineSequence()?.map(String::trim)?.firstOrNull(String::isNotEmpty)?.take(MAX_LINE)

    /** A version newer than the SDK speaks, as a modern-only server names it to `initialize` (§2.1). */
    private val MODERN = Regex("""20(2[6-9]|[3-9]\d)-\d\d-\d\d""")
    private const val MAX_DEPTH = 8
    private const val MAX_LINE = 200
}
