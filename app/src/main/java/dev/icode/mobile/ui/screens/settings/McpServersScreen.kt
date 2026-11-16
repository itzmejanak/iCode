package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.EmptyState
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.McpRules
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpServerState
import dev.icode.mobile.domain.McpStatus
import dev.icode.mobile.domain.McpToolChoice
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › MCP Servers (docs/assistant-and-mcp.md): a row for each server — its name, its
 * kind and where it is, its state and its switch — the open workspace's own under them; + adds one.
 * Opening the page connects the servers that are on.
 */
@Composable
fun McpServersScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val mcp = LocalICodeSnapshot.current.mcp
    LaunchedEffect(Unit) { gateway.dispatch(McpIntent.Connect) }
    var adding by rememberSaveable { mutableStateOf(false) }
    fun open(server: McpServer) {
        adding = false
        gateway.dispatch(McpIntent.Edit(server))
        actions.navigate(Route.McpServer)
    }
    val (own, declared) = mcp.servers.partition { it.workspace == null }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(adding)) {
            IcodeTopBar(
                "MCP Servers",
                subtitle = if (mcp.servers.isEmpty()) "Tools from other servers" else "${mcp.toolsOn} tools on",
                onBack = actions.back,
                actions = {
                    if (mcp.servers.isNotEmpty()) {
                        IconButton(onClick = { adding = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add server", tint = IcodeColors.TextPrimary)
                        }
                    }
                },
            )
            IcodePageColumn(Modifier.weight(1f)) {
                if (mcp.servers.isEmpty()) {
                    IcodeSection {
                        EmptyState(
                            title = "Add an MCP server",
                            description = "Its tools join the assistant's chats: GitHub, databases, docs and more",
                            icon = Icons.Outlined.Extension,
                            modifier = Modifier.padding(vertical = IcodeSpacing.Space6),
                        )
                        PrimaryButton("Add Server", { adding = true }, Modifier.fillMaxWidth())
                    }
                } else {
                    if (mcp.leftOut > 0) {
                        Text(
                            "The last chat left out ${mcp.leftOut} tools: a request takes ${McpRules.MAX_TOOLS} with iCode's own. Turn some off.",
                            style = IcodeType.Caption.copy(color = IcodeColors.Amber),
                        )
                    }
                    if (own.isNotEmpty()) {
                        IcodeSection("Servers") { own.forEach { ServerRow(it, mcp.state(it.id), { open(it) }) } }
                    }
                    declared.firstOrNull()?.workspace?.let { workspace ->
                        IcodeSection("${AssistantPlace.name(workspace)} · .mcp.json") {
                            declared.forEach { ServerRow(it, mcp.state(it.id), { open(it) }) }
                        }
                    }
                }
            }
        }
        if (adding) {
            IcodeSheetOverlay(onClose = { adding = false }) {
                McpAddSheet(Modifier.align(Alignment.BottomCenter), onClose = { adding = false }, onOpen = ::open)
            }
        }
    }
}

@Composable
private fun ServerRow(server: McpServer, state: McpServerState, onOpen: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val (words, color) = mcpStatus(server, state)
    IcodeListRow(
        card = true,
        onClick = onOpen,
        content = {
            Column(Modifier.weight(1f)) {
                Text(server.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                MonoLine(mcpWhere(server), style = IcodeType.MonoSmall, maxLines = 1)
                StatusLabel(words, color)
            }
        },
        trailing = { IcodeToggle(server.enabled) { gateway.dispatch(McpIntent.SetServerEnabled(server.id, it)) } },
    )
}

/** Its kind and where it is: "Local · npx -y …", "Remote · mcp.example.com". */
internal fun mcpWhere(server: McpServer): String = when (val transport = server.transport) {
    is McpTransport.Local -> "Local · " + (listOf(transport.command) + transport.args).joinToString(" ")
    is McpTransport.Remote -> "Remote · " + transport.url.substringAfter("://").substringBefore('/')
}

/** Its state, as its row and its page say it, and its colour. */
@Composable
internal fun mcpStatus(server: McpServer, state: McpServerState): Pair<String, Color> = McpRules.status(server, state) to when {
    !server.enabled -> IcodeColors.TextMuted
    else -> when (state.status) {
        McpStatus.Off, McpStatus.Starting -> IcodeColors.TextSecondary
        McpStatus.Ready -> IcodeColors.Green
        McpStatus.SignIn, McpStatus.Trust -> IcodeColors.Amber
        McpStatus.Failed -> IcodeColors.Error
    }
}
