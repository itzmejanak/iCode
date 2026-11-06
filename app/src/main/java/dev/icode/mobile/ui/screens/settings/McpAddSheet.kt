package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Add Server (docs/mcp-plan.md §4.1): a pasted configuration, a local command, a remote address, or a
 * server found in the MCP Registry, which opens its page filled in to check before it is added.
 */
@Composable
internal fun McpAddSheet(modifier: Modifier, onClose: () -> Unit, onOpen: (McpServer) -> Unit) {
    var way by rememberSaveable { mutableStateOf(Way.Choose) }
    val back = { way = Way.Choose }
    when (way) {
        Way.Choose -> IcodeBottomSheet(title = "Add Server", subtitle = "Its tools join the assistant's chats", onClose = onClose, modifier = modifier) {
            Option(Icons.Outlined.ContentPaste, "Paste Configuration", "The JSON a server's README gives") { way = Way.Paste }
            Option(Icons.Outlined.Terminal, "Local Command", "A program in the Linux runtime: npx, uvx, python3…") {
                onOpen(McpServer("", "", "", McpTransport.Local("")))
            }
            Option(Icons.Outlined.Cloud, "Remote URL", "A server's https:// address") { onOpen(McpServer("", "", "", McpTransport.Remote(""))) }
            Option(Icons.Outlined.Search, "MCP Registry", "Search the servers published there") { way = Way.Registry }
        }
        Way.Paste -> PasteSheet(modifier, onBack = back, onClose = onClose)
        Way.Registry -> RegistrySheet(modifier, onBack = back, onClose = onClose, onOpen = onOpen)
    }
}

private enum class Way { Choose, Paste, Registry }

@Composable
private fun Option(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    IcodeListRow(
        card = true,
        title = title,
        subtitle = description,
        subtitleStyle = IcodeType.Caption,
        leading = { Icon(icon, contentDescription = null, tint = IcodeColors.Accent) },
        trailing = { Chevron() },
        onClick = onClick,
    )
}

/** The JSON pasted; never saveable, as it may hold keys. Each server in it is added. */
@Composable
private fun PasteSheet(modifier: Modifier, onBack: () -> Unit, onClose: () -> Unit) {
    val gateway = LocalICodeGateway.current
    var json by remember { mutableStateOf("") }
    IcodeBottomSheet(
        title = "Paste Configuration",
        subtitle = "mcpServers, servers, or one server",
        onClose = onClose,
        onBack = onBack,
        modifier = modifier,
        footer = {
            PrimaryButton(
                "Add",
                {
                    gateway.dispatch(McpIntent.ImportServers(json))
                    onClose()
                },
                Modifier.fillMaxWidth(),
                enabled = json.isNotBlank(),
            )
        },
    ) {
        IcodeTextField(json, { json = it }, placeholder = """{"mcpServers": {"name": {"command": "npx", "args": […]}}}""", singleLine = false, literal = true)
        Text("A value written as \${NAME} waits for yours on the server's page.", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
    }
}

@Composable
private fun RegistrySheet(modifier: Modifier, onBack: () -> Unit, onClose: () -> Unit, onOpen: (McpServer) -> Unit) {
    val gateway = LocalICodeGateway.current
    val search = LocalICodeSnapshot.current.mcp.registry
    var query by rememberSaveable { mutableStateOf(search?.query.orEmpty()) }
    // A search outlives nothing: closed, it is forgotten.
    DisposableEffect(Unit) { onDispose { gateway.dispatch(McpIntent.SearchRegistry(null)) } }
    IcodeBottomSheet(title = "MCP Registry", subtitle = "registry.modelcontextprotocol.io", onClose = onClose, onBack = onBack, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            IcodeTextField(query, { query = it }, Modifier.weight(1f), placeholder = "github, postgres, notion…")
            SecondaryButton("Search", { gateway.dispatch(McpIntent.SearchRegistry(query)) }, enabled = query.isNotBlank())
        }
        when {
            search == null -> Unit
            search.loading -> IndeterminateProgressTrack(Modifier.fillMaxWidth())
            search.failure != null -> Text(search.failure, style = IcodeType.Caption.copy(color = IcodeColors.Error))
            search.entries.isEmpty() -> Text("No server iCode can run matches ${search.query}", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
            else -> Column {
                search.entries.forEach { entry ->
                    IcodeListRow(
                        card = true,
                        title = entry.title ?: entry.name,
                        subtitle = listOf(entry.description, mcpWhere(entry.server)).filter(String::isNotBlank).joinToString("\n"),
                        subtitleStyle = IcodeType.Caption,
                        trailing = { Chevron() },
                        onClick = { onOpen(entry.server) },
                    )
                }
            }
        }
    }
}
