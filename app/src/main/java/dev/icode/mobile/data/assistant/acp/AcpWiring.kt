package dev.icode.mobile.data.assistant.acp

import android.content.Context
import dev.icode.mobile.data.assistant.AssistantAgents
import dev.icode.mobile.data.assistant.AssistantAgentsContext
import dev.icode.mobile.data.assistant.mcp.McpServers
import dev.icode.mobile.data.runtime.RuntimeExecution

/**
 * The coding agents iCode lists (docs/assistant-and-mcp.md), in the runtime [execution] starts processes
 * in: each started as a local MCP server is, given the user's [mcp] servers, and [terminal] for its own
 * CLI and logins.
 */
internal fun codingAgents(
    app: Context,
    context: AssistantAgentsContext,
    execution: RuntimeExecution,
    mcp: McpServers?,
    terminal: suspend (workspace: String?, chat: String?, command: String) -> String,
    refreshFiles: () -> Unit,
): AssistantAgents = AcpAgents(
    context,
    AgentRegistry.pinned(app),
    AgentLauncher(execution.layout, execution.environment),
    AgentStore(app),
    AcpServers { mcp?.forAgents().orEmpty() },
    terminal,
    refreshFiles,
)
