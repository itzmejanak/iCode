package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeOptionRow
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.McpAnswer
import dev.icode.mobile.domain.McpField
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.McpQuestion
import dev.icode.mobile.ui.LocalICodeGateway

/**
 * An MCP server's question mid-call (docs/mcp-plan.md §4.3, M4): its message and its form — Decline or
 * Submit — or the address of its own page, shown before anything opens — Decline or Open. × cancels.
 * The call's own time stands still while it waits.
 */
@Composable
internal fun McpQuestionCard(question: McpQuestion, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    // Never saveable: an answer may be a secret.
    var values by remember(question.id) { mutableStateOf(question.fields.mapNotNull { field -> field.default?.let { field.name to it } }.toMap()) }
    fun answer(action: McpAnswer.Action) =
        gateway.dispatch(McpIntent.Answer(McpAnswer(question.id, action, if (action == McpAnswer.Action.Accept) values else emptyMap())))
    val missing = question.fields.any { it.required && values[it.name].isNullOrBlank() }
    IcodeCard(modifier.fillMaxWidth(), borderColor = IcodeColors.Accent) {
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${question.server} asks", style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                IconButton(onClick = { answer(McpAnswer.Action.Cancel) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = IcodeColors.TextSecondary)
                }
            }
            Text(question.message, style = IcodeType.Body)
            question.url?.let { DetailRow("Opens", question.host ?: it) }
            question.fields.forEach { field -> Field(field, values[field.name]) { values = values + (field.name to it) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2, Alignment.End)) {
                SecondaryButton("Decline", { answer(McpAnswer.Action.Decline) }, compact = true)
                val url = question.url
                if (url != null) {
                    PrimaryButton("Open", {
                        answer(McpAnswer.Action.Accept)
                        gateway.dispatch(ICodeIntent.OpenExternalUrl(url))
                    }, compact = true)
                } else {
                    PrimaryButton("Submit", { answer(McpAnswer.Action.Accept) }, compact = true, enabled = !missing)
                }
            }
        }
    }
}

/** One field as its kind asks: typed, switched, one choice, or several — those joined by new lines. */
@Composable
private fun Field(field: McpField, value: String?, onValue: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        val title = field.title + if (field.required) " *" else ""
        when (field.kind) {
            McpField.Kind.Flag -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = IcodeType.Body, modifier = Modifier.weight(1f))
                IcodeToggle(value == "true") { onValue(it.toString()) }
            }
            // The rows the assistant's own questions use: the whole row is the radio, or the checkbox.
            McpField.Kind.Choice, McpField.Kind.Choices -> {
                Text(title, style = IcodeType.Label.copy(color = IcodeColors.TextSecondary))
                val multiple = field.kind == McpField.Kind.Choices
                val chosen = if (multiple) value.orEmpty().lines().filter(String::isNotEmpty).toSet() else setOfNotNull(value)
                Column(if (multiple) Modifier else Modifier.selectableGroup()) {
                    field.options.forEach { (option, label) ->
                        IcodeOptionRow(label, description = null, selected = option in chosen, multiple = multiple, onSelect = {
                            onValue(if (!multiple) option else (if (option in chosen) chosen - option else chosen + option).joinToString("\n"))
                        })
                    }
                }
            }
            else -> {
                Text(title, style = IcodeType.Label.copy(color = IcodeColors.TextSecondary))
                IcodeTextField(value.orEmpty(), onValue, placeholder = FORMS[field.kind], literal = field.kind != McpField.Kind.Text)
            }
        }
        if (field.description.isNotBlank()) Text(field.description, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
    }
}

/** What a typed field's value looks like, where its kind says. */
private val FORMS = mapOf(
    McpField.Kind.Email to "name@example.com",
    McpField.Kind.Uri to "https://…",
    McpField.Kind.Date to "YYYY-MM-DD",
    McpField.Kind.DateTime to "YYYY-MM-DDThh:mm:ssZ",
    McpField.Kind.Integer to "A whole number",
    McpField.Kind.Number to "A number",
)
