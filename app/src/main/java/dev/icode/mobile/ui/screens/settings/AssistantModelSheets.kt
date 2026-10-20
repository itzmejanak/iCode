package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeCheckbox
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.TwoColumnActions
import dev.icode.mobile.domain.AssistantAvailable
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantRules

/**
 * Models to add to a provider (§13 P2, P5): those its server lists — asked for as the sheet opens,
 * with the address and key as typed — then those iCode knows for its kind, ticked to add; or any
 * ID typed. A long list is narrowed by the search.
 */
@Composable
internal fun AddModelsSheet(
    providerName: String,
    available: AssistantAvailable?,
    catalogue: List<AssistantModel>,
    models: List<AssistantModelSpec>,
    modifier: Modifier,
    onList: () -> Unit,
    onClose: () -> Unit,
    onModels: (List<AssistantModelSpec>) -> Unit,
) {
    LaunchedEffect(Unit) { onList() }
    var query by rememberSaveable { mutableStateOf("") }
    val typed = query.trim()
    val served = available?.models.orEmpty()
    val known = catalogue.filter { model -> served.none { it.id == model.id } }
    val added = models.map { it.id }.toSet()
    fun matches(model: AssistantModel) = typed.isEmpty() || model.id.contains(typed, ignoreCase = true)
    fun toggle(model: AssistantModel) = onModels(if (model.id in added) models.filterNot { it.id == model.id } else models + model.spec)

    @Composable
    fun Choices(choices: List<AssistantModel>) {
        val shown = choices.filter(::matches)
        shown.take(MAX_SHOWN).forEach { model ->
            IcodeListRow(
                title = model.id,
                titleStyle = IcodeType.Mono,
                subtitle = AssistantRules.modelSummary(model).ifEmpty { null },
                subtitleStyle = IcodeType.Caption,
                onClick = { toggle(model) },
                leading = { IcodeCheckbox(model.id in added, onCheckedChange = null) },
            )
        }
        if (shown.size > MAX_SHOWN) Muted("${shown.size - MAX_SHOWN} more match: narrow the search")
    }

    IcodeBottomSheet(
        title = "Add Models",
        onClose = onClose,
        modifier = modifier,
        footer = { PrimaryButton(if (models.size == 1) "Done · 1 model" else "Done · ${models.size} models", onClose, Modifier.fillMaxWidth()) },
    ) {
        IcodeTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Search, or type a model ID", leadingIcon = Icons.Outlined.Search, literal = true)
        val listed = (served + catalogue).any { it.id == typed }
        if (typed.isNotEmpty() && typed.none(Char::isWhitespace) && typed !in added && !listed) {
            IcodeListRow(
                title = "Add “$typed”",
                subtitle = "A model $providerName serves",
                subtitleStyle = IcodeType.Caption,
                onClick = {
                    onModels(models + AssistantModelSpec(typed))
                    query = ""
                },
                leading = { Icon(Icons.Outlined.Add, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd)) },
            )
        }
        SectionLabel("Served by $providerName", Modifier.padding(top = IcodeSpacing.SectionBreak))
        when {
            available == null || available.loading -> Row(
                Modifier.padding(vertical = IcodeSpacing.Space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
            ) {
                CircularProgressIndicator(Modifier.size(IcodeSizes.IconSm), color = IcodeColors.Accent, strokeWidth = IcodeSpacing.Micro)
                Muted("Asking $providerName for its models…")
            }
            available.failure != null -> {
                Muted(available.failure)
                SecondaryButton("Try Again", onList)
            }
            served.isEmpty() -> Muted("$providerName lists no models iCode can chat with: type a model's ID")
            else -> Choices(served)
        }
        if (known.any(::matches)) {
            SectionLabel("Known to iCode", Modifier.padding(top = IcodeSpacing.SectionBreak))
            Choices(known)
        }
    }
}

/**
 * One model of a provider: what iCode knows of it, or — for one it does not know — what the user
 * says: its context window and reply limit (the meter, compaction and Anthropic's limit need them),
 * whether it reads images, whether it reasons. Remove takes it off the provider.
 */
@Composable
internal fun ModelOptionsSheet(
    spec: AssistantModelSpec,
    known: AssistantModel?,
    modifier: Modifier,
    onClose: () -> Unit,
    onSave: (AssistantModelSpec) -> Unit,
    onRemove: () -> Unit,
) {
    var contextWindow by rememberSaveable(spec.id) { mutableStateOf(spec.contextWindow?.toString().orEmpty()) }
    var maxOutput by rememberSaveable(spec.id) { mutableStateOf(spec.maxOutput?.toString().orEmpty()) }
    var images by rememberSaveable(spec.id) { mutableStateOf(spec.images) }
    var reasoning by rememberSaveable(spec.id) { mutableStateOf(spec.reasoning) }
    val problem = if (known == null) AssistantRules.modelOptionsProblem(contextWindow, maxOutput) else null
    IcodeBottomSheet(
        title = spec.id,
        onClose = onClose,
        modifier = modifier,
        footer = {
            TwoColumnActions(
                left = {
                    DangerButton(
                        "Remove",
                        {
                            onRemove()
                            onClose()
                        },
                        Modifier.fillMaxWidth(),
                    )
                },
                right = {
                    PrimaryButton(
                        "Done",
                        {
                            if (known == null) onSave(AssistantModelSpec(spec.id, AssistantRules.tokens(contextWindow), AssistantRules.tokens(maxOutput), images, reasoning))
                            onClose()
                        },
                        Modifier.fillMaxWidth(),
                        enabled = problem == null,
                    )
                },
            )
        },
    ) {
        if (known != null) {
            Muted("iCode knows this model.")
            DetailRow("Context", known.contextWindow?.let { "${AssistantRules.tokenCount(it)} tokens" } ?: "—")
            DetailRow("Reply limit", known.maxOutput?.let { "${AssistantRules.tokenCount(it)} tokens" } ?: "—")
            DetailRow("Reads", listOfNotNull("text", "images".takeIf { known.images }, "PDFs".takeIf { known.documents }).joinToString(", "))
            DetailRow("Reasoning", known.efforts.joinToString(", ") { it.label }.ifEmpty { "—" })
        } else {
            Muted("iCode does not know this model: say what it can do. Limits left empty are not used.")
            LabeledTextField("Context Window", contextWindow, { contextWindow = it }, placeholder = "Tokens it reads, as 128k", literal = true)
            LabeledTextField("Output Limit", maxOutput, { maxOutput = it }, placeholder = "Tokens in one reply, as 8k", literal = true)
            SettingRow("Reads Images", subtitle = "Photos go to it with the message") { IcodeToggle(images) { images = it } }
            SettingRow("Reasons", subtitle = "The chat offers its provider's reasoning levels") { IcodeToggle(reasoning) { reasoning = it } }
            problem?.let { Muted(it) }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted), modifier = Modifier.padding(vertical = IcodeSpacing.Space1))
}

/** Rows a sheet draws at most: a server of hundreds (OpenRouter) is searched. */
private const val MAX_SHOWN = 50
