package dev.icode.mobile.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * One provider: its kind (chosen when it is added), its name, its server's address
 * where it has one, its API key — masked, never shown again once saved — and the models it offers
 * the chat, added from its server's list, iCode's catalogue or by ID. Save checks the form; the
 * first message checks the connection.
 */
@Composable
fun AssistantProviderScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val state = LocalICodeSnapshot.current.assistant
    // Opened again after the process was gone, the page is for a provider being added.
    LaunchedEffect(Unit) { if (state.editing == null) gateway.dispatch(ICodeIntent.EditAssistantProvider("")) }
    val id = state.editing.orEmpty()
    val saved = state.settings.providers.firstOrNull { it.id == id }
    var kind by rememberSaveable(id) { mutableStateOf(saved?.kind ?: state.adding ?: AssistantProviderKind.entries.first()) }
    var name by rememberSaveable(id) { mutableStateOf(saved?.name.orEmpty()) }
    var baseUrl by rememberSaveable(id, kind) { mutableStateOf(saved?.baseUrl ?: kind.defaultBaseUrl.orEmpty()) }
    var models by rememberSaveable(id, kind, stateSaver = SPECS_SAVER) { mutableStateOf(saved?.models?.map { it.spec }.orEmpty()) }
    // Never saveable: saved state leaves the process in a Bundle, and a key must not.
    var key by remember(id, kind) { mutableStateOf("") }
    val keySaved = saved?.hasKey == true
    var replacingKey by remember(id) { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var options by rememberSaveable { mutableStateOf<String?>(null) }
    var kinds by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    // What Back would lose: anything typed on a new provider, any change to a saved one.
    val changed = key.isNotBlank() || if (saved == null) {
        name.isNotBlank() || models.isNotEmpty()
    } else {
        name != saved.name || baseUrl != (saved.baseUrl ?: kind.defaultBaseUrl.orEmpty()) || models != saved.models.map { it.spec }
    }
    BackHandler(enabled = changed) { leaving = true }
    val named = name.trim().ifEmpty { kind.label }
    val problem = AssistantRules.providerProblem(kind, named, baseUrl, key.isNotBlank(), keySaved, models)
    val catalogue = state.catalogue[kind].orEmpty()
    fun knownOf(model: String) = catalogue.firstOrNull { it.id == model }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(adding || options != null || kinds)) {
            IcodeTopBar(saved?.name ?: "Add Provider", subtitle = saved?.kind?.label, onBack = { if (changed) leaving = true else actions.back() })
            IcodePageColumn(Modifier.weight(1f)) {
                if (saved == null) {
                    IcodeSection("Provider") {
                        IcodeListRow(
                            card = true,
                            title = kind.label,
                            subtitle = kind.description,
                            subtitleStyle = IcodeType.Caption,
                            onClick = { kinds = true },
                            trailing = { Chevron() },
                        )
                    }
                }
                LabeledTextField("Name", name, { name = it }, placeholder = kind.label)
                if (kind.asksBaseUrl) {
                    IcodeSection("Server Address") {
                        IcodeTextField(baseUrl, { baseUrl = it }, placeholder = kind.defaultBaseUrl ?: "https://api.example.com/v1", literal = true)
                        AssistantRules.providerHint(kind, baseUrl)?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Amber)) }
                    }
                }
                if (keySaved && !replacingKey) {
                    IcodeSection("API Key") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                            StatusLabel("Saved, never shown again", IcodeColors.Green, Modifier.weight(1f))
                            SecondaryButton("Replace", { replacingKey = true })
                        }
                    }
                } else {
                    LabeledTextField("API Key", key, { key = it }, placeholder = if (kind.needsKey) "Paste the key" else "Optional", secret = true)
                }
                IcodeSection("Models") {
                    if (models.isEmpty()) {
                        Text("The models this provider offers the chat: add one or more.", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
                    } else {
                        IcodeCard(Modifier.fillMaxWidth()) {
                            models.forEach { spec ->
                                val known = knownOf(spec.id)
                                IcodeListRow(
                                    title = spec.id,
                                    titleStyle = IcodeType.Mono,
                                    subtitle = (known?.let(AssistantRules::modelSummary) ?: AssistantRules.modelSummary(spec)).ifEmpty { "Tap to say what it can do" },
                                    subtitleStyle = IcodeType.Caption,
                                    onClick = { options = spec.id },
                                    trailing = {
                                        if (state.settings.choice == AssistantChoice(id, spec.id)) StatusLabel("In use", IcodeColors.Green)
                                        IconButton(onClick = { models = models - spec }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Remove ${spec.id}", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconMd))
                                        }
                                    },
                                )
                            }
                        }
                    }
                    SecondaryButton("Add Models", { adding = true }, Modifier.fillMaxWidth())
                }
                IcodeSection {
                    problem?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted)) }
                    PrimaryButton(
                        if (saved == null) "Add Provider" else "Save",
                        {
                            gateway.dispatch(ICodeIntent.SaveAssistantProvider(id, kind, named, baseUrl, key.takeIf { it.isNotBlank() }, models))
                            actions.back()
                        },
                        Modifier.fillMaxWidth(),
                        enabled = problem == null,
                    )
                    if (saved != null) DangerButton("Remove Provider", { removing = true }, Modifier.fillMaxWidth())
                }
            }
        }
        if (kinds) {
            IcodeSheetOverlay(onClose = { kinds = false }) {
                ProviderKindSheet(selected = kind, modifier = Modifier.align(Alignment.BottomCenter), onClose = { kinds = false }) {
                    kind = it
                    kinds = false
                }
            }
        }
        if (adding || options != null) {
            val close = {
                adding = false
                options = null
            }
            IcodeSheetOverlay(onClose = close) {
                val modifier = Modifier.align(Alignment.BottomCenter)
                val shown = models.firstOrNull { it.id == options }
                if (adding) {
                    AddModelsSheet(
                        providerName = named,
                        available = state.available?.takeIf { it.provider == id },
                        catalogue = catalogue,
                        models = models,
                        modifier = modifier,
                        onList = { gateway.dispatch(ICodeIntent.ListAssistantModels(id, kind, baseUrl, key.takeIf { it.isNotBlank() })) },
                        onClose = close,
                        onModels = { models = it },
                    )
                } else if (shown != null) {
                    ModelOptionsSheet(
                        spec = shown,
                        known = knownOf(shown.id),
                        modifier = modifier,
                        onClose = close,
                        onSave = { changed -> models = models.map { if (it.id == changed.id) changed else it } },
                        onRemove = { models = models - shown },
                    )
                }
            }
        }
    }
    if (leaving) {
        IcodeConfirmSheet(
            title = "Leave without saving?",
            message = if (saved == null) "This provider is not added yet." else "The changes to ${saved.name} are not saved.",
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
            message = "Its API key and models are forgotten on this phone. Chats stay.",
            confirmLabel = "Remove",
            onConfirm = {
                gateway.dispatch(ICodeIntent.RemoveAssistantProvider(saved.id))
                removing = false
                actions.back()
            },
            onDismiss = { removing = false },
        )
    }
}

/** The models on the form, through a configuration change: five strings each. */
private val SPECS_SAVER = listSaver<List<AssistantModelSpec>, String>(
    save = { specs ->
        specs.flatMap { listOf(it.id, it.contextWindow?.toString().orEmpty(), it.maxOutput?.toString().orEmpty(), it.images.toString(), it.reasoning.toString()) }
    },
    restore = { saved ->
        saved.chunked(5).map { (id, contextWindow, maxOutput, images, reasoning) ->
            AssistantModelSpec(id, contextWindow.toLongOrNull(), maxOutput.toLongOrNull(), images.toBoolean(), reasoning.toBoolean())
        }
    },
)
