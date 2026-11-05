package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.McpPromptInfo
import dev.icode.mobile.domain.McpState
import dev.icode.mobile.domain.McpStatus
import dev.icode.mobile.ui.LocalICodeGateway

/**
 * What the connected MCP servers offer the user (docs/mcp-plan.md M8): their prompts, filled in and
 * taken into the message, and their resources, read into it — the user's to read and change before
 * it is sent. Nothing shows when no server offers any.
 */
@Composable
internal fun McpOffers(mcp: McpState, onTaken: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val ready = mcp.servers.filter { it.enabled && mcp.state(it.id).status == McpStatus.Ready }
        .map { it to mcp.state(it.id) }.filter { (_, state) -> state.prompts.isNotEmpty() || state.resources.isNotEmpty() }
    if (ready.isEmpty()) return
    var filling by rememberSaveable { mutableStateOf<String?>(null) }
    SectionLabel("From MCP servers", Modifier.padding(top = IcodeSpacing.SectionBreak))
    ready.forEach { (server, state) ->
        state.prompts.forEach { prompt ->
            val key = "${server.id}/${prompt.name}"
            IcodeListRow(
                title = prompt.title ?: prompt.name,
                subtitle = listOf("${server.name} · prompt", prompt.description).filter(String::isNotBlank).joinToString(" — "),
                subtitleStyle = IcodeType.Caption,
                onClick = {
                    if (prompt.arguments.isEmpty()) {
                        gateway.dispatch(McpIntent.UsePrompt(server.id, prompt.name, emptyMap()))
                        onTaken()
                    } else {
                        filling = key.takeIf { filling != it }
                    }
                },
            )
            if (filling == key) {
                PromptForm(prompt) { arguments ->
                    gateway.dispatch(McpIntent.UsePrompt(server.id, prompt.name, arguments))
                    onTaken()
                }
            }
        }
        state.resources.forEach { resource ->
            IcodeListRow(
                title = resource.title ?: resource.name,
                subtitle = listOf("${server.name} · ${resource.mimeType ?: "resource"}", resource.description).filter(String::isNotBlank).joinToString(" — "),
                subtitleStyle = IcodeType.Caption,
                onClick = {
                    gateway.dispatch(McpIntent.UseResource(server.id, resource.uri))
                    onTaken()
                },
            )
        }
    }
}

/** A prompt's arguments, each typed; the required ones before Use. */
@Composable
private fun PromptForm(prompt: McpPromptInfo, onUse: (Map<String, String>) -> Unit) {
    var values by remember(prompt.name) { mutableStateOf(emptyMap<String, String>()) }
    IcodeCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            prompt.arguments.forEach { argument ->
                Text(argument.name + if (argument.required) " *" else "", style = IcodeType.Label.copy(color = IcodeColors.TextSecondary))
                IcodeTextField(values[argument.name].orEmpty(), { values = values + (argument.name to it) }, placeholder = argument.description.ifBlank { null })
            }
            PrimaryButton(
                "Use",
                { onUse(values.filterValues(String::isNotEmpty)) },
                Modifier.fillMaxWidth(),
                enabled = prompt.arguments.none { it.required && values[it.name].isNullOrBlank() },
            )
        }
    }
}
