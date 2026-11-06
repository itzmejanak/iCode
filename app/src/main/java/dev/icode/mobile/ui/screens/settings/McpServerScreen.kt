package dev.icode.mobile.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.McpRules
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpState
import dev.icode.mobile.domain.McpStatus
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * One MCP server (docs/mcp-plan.md §4.2): how it is now and what to do about it — Restart, Sign in, Trust
 * — then its fields as on adding, a secret never shown again once saved; its tools, each on or off and
 * asking first or running; what it tells models; its log; Remove. A workspace's server shows what its
 * `.mcp.json` says, and keeps only the user's values and choices.
 */
@Composable
fun McpServerScreen(actions: NavigationActions) {
    val mcp = LocalICodeSnapshot.current.mcp
    val editing = mcp.editing
    // Opened again after the process was gone: back to the list, where a page is opened from.
    if (editing == null) LaunchedEffect(Unit) { actions.back() } else ServerPage(editing, mcp, actions)
}

@Composable
private fun ServerPage(editing: McpServer, mcp: McpState, actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val saved = mcp.servers.firstOrNull { it.id == editing.id && it.id.isNotEmpty() }
    val state = saved?.let { mcp.state(it.id) }
    val declared = editing.workspace != null
    val local = editing.transport as? McpTransport.Local
    val remote = editing.transport as? McpTransport.Remote
    var name by rememberSaveable(editing.id) { mutableStateOf(editing.name) }
    var command by rememberSaveable(editing.id) { mutableStateOf(local?.command.orEmpty()) }
    var arguments by rememberSaveable(editing.id) { mutableStateOf(local?.args.orEmpty().joinToString("\n")) }
    var folder by rememberSaveable(editing.id) { mutableStateOf(local?.folder.orEmpty()) }
    var url by rememberSaveable(editing.id) { mutableStateOf(remote?.url.orEmpty()) }
    var clientId by rememberSaveable(editing.id) { mutableStateOf(remote?.clientId.orEmpty()) }
    var timeout by rememberSaveable(editing.id) { mutableStateOf(editing.toolTimeoutSeconds.toString()) }
    // Never saveable: a value typed may be a secret, and saved state leaves the process in a Bundle.
    var values by remember(editing.id) { mutableStateOf(local?.env ?: remote?.headers.orEmpty()) }
    var removing by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    val draft = (saved ?: editing).copy(
        name = name.trim(),
        transport = if (local != null) {
            McpTransport.Local(command.trim(), arguments.lines().map(String::trim).filter(String::isNotEmpty), folder.trim().ifEmpty { null }, values)
        } else {
            McpTransport.Remote(url.trim(), values, clientId.trim().ifEmpty { null })
        },
        toolTimeoutSeconds = timeout.trim().toIntOrNull() ?: -1,
    )
    val changed = draft != (saved ?: editing) || values.any { it.value != null && it.secret }
    BackHandler(enabled = changed) { leaving = true }
    // A workspace's server is checked against its file, not here: only its values are the user's.
    val problem = if (declared) null else McpRules.problem(draft, mcp.servers)
    fun save() {
        gateway.dispatch(McpIntent.SaveServer(draft))
        actions.back()
    }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            saved?.name ?: editing.name.ifEmpty { "Add Server" },
            subtitle = state?.serverInfo ?: if (local != null) "Local command" else "Remote URL",
            onBack = { if (changed) leaving = true else actions.back() },
        )
        IcodePageColumn(Modifier.weight(1f)) {
            if (saved != null && state != null) {
                IcodeSection {
                    val (words, color) = mcpStatus(saved, state)
                    StatusLabel(words, color)
                    state.failure?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                        when {
                            state.status == McpStatus.Trust -> PrimaryButton("Trust and Start", { gateway.dispatch(McpIntent.Trust(saved.id, true)) }, Modifier.weight(1f))
                            state.status == McpStatus.SignIn -> PrimaryButton("Sign In", { gateway.dispatch(McpIntent.SignIn(saved.id)) }, Modifier.weight(1f))
                            saved.enabled -> SecondaryButton("Restart", { gateway.dispatch(McpIntent.RestartServer(saved.id)) }, Modifier.weight(1f))
                        }
                        if (saved.id in mcp.signedIn) SecondaryButton("Sign Out", { gateway.dispatch(McpIntent.SignOut(saved.id)) }, Modifier.weight(1f))
                    }
                }
            }
            if (declared) {
                IcodeSection("From .mcp.json") {
                    DetailRow(if (local != null) "Command" else "Address", mcpWhere(editing).substringAfter(" · "))
                    Text("Change it in the workspace's .mcp.json. A change asks for trust again.", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
                }
            } else {
                LabeledTextField("Name", name, { name = it }, placeholder = "GitHub")
                if (local != null) {
                    LabeledTextField("Command", command, { command = it }, placeholder = "npx", literal = true)
                    LabeledTextField("Arguments", arguments, { arguments = it }, placeholder = "One per line", singleLine = false, literal = true)
                    LabeledTextField("Folder", folder, { folder = it }, placeholder = "/root", literal = true)
                } else {
                    LabeledTextField("Address", url, { url = it }, placeholder = "https://mcp.example.com/mcp", literal = true)
                    LabeledTextField("Client ID", clientId, { clientId = it }, placeholder = "Only if its sign-in asks for one", literal = true)
                }
            }
            McpValuesEditor(if (local != null) "Environment" else "Headers", values, fixed = declared) { values = it }
            if (!declared) LabeledTextField("Tool Time (seconds)", timeout, { timeout = it }, placeholder = McpRules.TOOL_TIMEOUT_SECONDS.toString())
            if (saved == null || changed) {
                IcodeSection {
                    problem?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
                    PrimaryButton(if (saved == null) "Add Server" else "Save", ::save, Modifier.fillMaxWidth(), enabled = problem == null)
                }
            }
            if (saved != null && state != null) {
                McpToolsSection(saved, state)
                McpInstructions(state.instructions)
                if (local != null) McpLogSection(mcp.log?.takeIf { it.server == saved.id }?.lines.orEmpty()) { gateway.dispatch(McpIntent.Edit(editing)) }
                if (!declared) {
                    IcodeSection { DangerButton("Remove Server", { removing = true }, Modifier.fillMaxWidth()) }
                } else if (state.status != McpStatus.Trust) {
                    IcodeSection { SecondaryButton("Stop Trusting", { gateway.dispatch(McpIntent.Trust(saved.id, false)) }, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
    if (leaving) {
        IcodeConfirmSheet(
            title = "Leave without saving?",
            message = if (saved == null) "This server is not added yet." else "The changes to ${saved.name} are not saved.",
            confirmLabel = "Discard",
            onConfirm = {
                leaving = false
                actions.back()
            },
            onDismiss = { leaving = false },
        )
    }
    if (removing && saved != null) {
        IcodeConfirmSheet(
            title = "Remove ${saved.name}?",
            message = "Its values, its sign-in and its tools' choices are forgotten on this phone.",
            confirmLabel = "Remove",
            onConfirm = {
                gateway.dispatch(McpIntent.RemoveServer(saved.id))
                removing = false
                actions.back()
            },
            onDismiss = { removing = false },
        )
    }
}
