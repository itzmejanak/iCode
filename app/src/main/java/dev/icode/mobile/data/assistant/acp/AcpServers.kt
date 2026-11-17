package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.model.EnvVariable
import com.agentclientprotocol.model.HttpHeader
import com.agentclientprotocol.model.McpCapabilities
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.McpValue
import com.agentclientprotocol.model.McpServer as AcpMcpServer

/**
 * The user's MCP servers, passed to each new or reopened agent session (docs/assistant-and-mcp.md): one
 * server configured once for every assistant. Only the user's own — a workspace's `.mcp.json` is the
 * agent's to read in that workspace — on, with their values as saved. A remote server goes only to an
 * agent that speaks its transport, and with its headers only: iCode's own sign-in to it stays iCode's.
 */
internal class AcpServers(private val enabled: () -> List<Pair<McpServer, List<McpValue>>>) {
    fun of(capabilities: McpCapabilities): List<AcpMcpServer> = enabled().mapNotNull { (server, values) ->
        when (val transport = server.transport) {
            is McpTransport.Local -> AcpMcpServer.Stdio(server.name, transport.command, transport.args, values.mapNotNull { value -> value.value?.let { EnvVariable(value.name, it) } })
            is McpTransport.Remote -> {
                val headers = values.mapNotNull { value -> value.value?.let { HttpHeader(value.name, it) } }
                when {
                    capabilities.http -> AcpMcpServer.Http(server.name, transport.url, headers)
                    capabilities.sse -> AcpMcpServer.Sse(server.name, transport.url, headers)
                    else -> null
                }
            }
        }
    }
}
