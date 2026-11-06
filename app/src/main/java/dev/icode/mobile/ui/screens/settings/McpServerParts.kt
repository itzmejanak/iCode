package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeExpandableCard
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextButton
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.McpRules
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpServerState
import dev.icode.mobile.domain.McpToolChoice
import dev.icode.mobile.domain.McpValue
import dev.icode.mobile.ui.LocalICodeGateway

/**
 * A server's environment or headers: each a name and a value, secret unless unlocked (D8). A secret
 * saved is never shown again: its field is empty, and what is typed there replaces it. [fixed]: a
 * workspace's server, whose names its file gives.
 */
@Composable
internal fun McpValuesEditor(title: String, values: List<McpValue>, fixed: Boolean, onValues: (List<McpValue>) -> Unit) {
    if (fixed && values.isEmpty()) return
    IcodeSection(title) {
        values.forEachIndexed { index, value ->
            fun change(to: McpValue) = onValues(values.toMutableList().also { it[index] = to })
            IcodeCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                    if (fixed) {
                        MonoLine(value.name)
                    } else {
                        IcodeTextField(value.name, { change(value.copy(name = it, saved = false)) }, placeholder = "NAME", literal = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IcodeTextField(
                            value.value.orEmpty(),
                            { change(value.copy(value = it.ifEmpty { null })) },
                            Modifier.weight(1f),
                            placeholder = when {
                                value.secret && value.saved -> "•••• saved — type to replace"
                                else -> value.hint ?: "Value"
                            },
                            secret = value.secret,
                            literal = true,
                        )
                        if (!fixed) {
                            // A secret made plain needs its value again: the saved one is never read back.
                            IconButton(onClick = { change(value.copy(secret = !value.secret, saved = false, value = if (value.secret) null else value.value)) }) {
                                Icon(
                                    if (value.secret) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                    contentDescription = if (value.secret) "Secret: make plain" else "Plain: make secret",
                                    tint = if (value.secret) IcodeColors.Accent else IcodeColors.TextSecondary,
                                )
                            }
                            IconButton(onClick = { onValues(values.filterIndexed { at, _ -> at != index }) }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Remove ${value.name}", tint = IcodeColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
        if (!fixed) SecondaryButton("Add Value", { onValues(values + McpValue("")) }, Modifier.fillMaxWidth())
    }
}

/** Its tools: each on or off, and, on, asking first or running without asking — the user's choice alone (§6.4). */
@Composable
internal fun McpToolsSection(server: McpServer, state: McpServerState) {
    if (state.tools.isEmpty()) return
    val gateway = LocalICodeGateway.current
    fun choose(tool: String, choice: McpToolChoice) = gateway.dispatch(McpIntent.SetToolChoice(server.id, tool, choice))
    IcodeSection("Tools · ${McpRules.toolsOn(server, state)} of ${state.tools.size} on") {
        state.tools.forEach { tool ->
            val choice = server.choice(tool.name)
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                        Text(tool.shown, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (tool.description.isNotBlank()) {
                            Text(tool.description, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (tool.readOnly) StatusLabel("Read-only", IcodeColors.Green)
                        if (tool.destructive) StatusLabel("Destructive", IcodeColors.Amber)
                        tool.problem?.let { Text("Left out of chats: $it", style = IcodeType.Caption.copy(color = IcodeColors.Amber)) }
                    }
                    if (tool.problem == null) IcodeToggle(choice != McpToolChoice.Off) { choose(tool.name, if (it) McpToolChoice.AsksFirst else McpToolChoice.Off) }
                }
                if (tool.problem == null && choice != McpToolChoice.Off) {
                    val choices = listOf(McpToolChoice.AsksFirst, McpToolChoice.Runs)
                    SegmentedControl(choices.map { it.label }, choice.label, Modifier.fillMaxWidth()) { label -> choose(tool.name, choices.first { it.label == label }) }
                }
            }
        }
    }
}

/** What the server tells models, closed until opened: its words, not iCode's. */
@Composable
internal fun McpInstructions(instructions: String?) {
    instructions ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    IcodeSection("Instructions") {
        IcodeExpandableCard("What it tells models", open, { open = it }) {
            Text(instructions, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary))
        }
    }
}

/** A local server's stderr since it last started: its last 200 lines. */
@Composable
internal fun McpLogSection(lines: List<String>, onRead: () -> Unit) {
    IcodeSection("Log") {
        CodeSurface(Modifier.fillMaxWidth()) {
            if (lines.isEmpty()) {
                Text("Nothing written yet", style = IcodeType.MonoSmall.copy(color = IcodeColors.TextMuted))
            } else {
                MonoLine(lines.joinToString("\n"), style = IcodeType.MonoSmall, maxLines = Int.MAX_VALUE)
            }
        }
        IcodeTextButton("Read Again", onRead)
    }
}
