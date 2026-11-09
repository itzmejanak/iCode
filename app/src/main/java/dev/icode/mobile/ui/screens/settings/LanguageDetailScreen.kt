package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.LanguageIntent
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServicePresets
import dev.icode.mobile.domain.LanguageServiceRules
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** A language service's configuration, its server's state and log (docs/lsp-plan.md §7). */
@Composable
fun LanguageDetailScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val service = state.languageServices.firstOrNull { it.id == state.selectedLanguageServiceId }
    val saved = service?.let(LanguageServiceRules::configurationOf) ?: LanguageServiceConfiguration("", "", "")
    var draft by rememberSaveable(service?.id, stateSaver = ConfigurationSaver) { mutableStateOf(saved) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val status = service?.status ?: LanguageServiceStatus.Stopped
    val validation = LanguageServiceRules.validate(draft)
    val install = LanguageServicePresets.all.firstOrNull { it.configuration.command == draft.command.trim() }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            service?.name ?: "Add Language Service",
            onBack = actions.back,
            actions = { if (service != null) StatusLabel(status.name, status.tone()) },
        )
        IcodePageColumn(Modifier.weight(1f)) {
            if (service == null) PresetSection { preset -> draft = preset.configuration }
            IcodeSection("Configuration") {
                ConfigField("Name", draft.name, { draft = draft.copy(name = it) }, "e.g. Python (pylsp)")
                ConfigField("Command", draft.command, { draft = draft.copy(command = it) }, "e.g. typescript-language-server --stdio")
                install?.let { preset ->
                    Text("Install it in a terminal" + (preset.needs?.let { ", after $it" } ?: "") + ":", style = IcodeType.Caption, color = IcodeColors.TextSecondary)
                    MonoLine(preset.install, maxLines = 2)
                }
                ConfigField("Languages", draft.languages, { draft = draft.copy(languages = it) }, "e.g. typescript, typescriptreact")
                ConfigField("File Patterns", draft.patterns, { draft = draft.copy(patterns = it) }, "e.g. *.ts, *.tsx")
                ConfigField("Root Markers", draft.rootMarkers, { draft = draft.copy(rootMarkers = it) }, "e.g. tsconfig.json, package.json")
                ConfigField("Environment", draft.environment, { draft = draft.copy(environment = it) }, "NAME=value, one a line", singleLine = false)
                ConfigField("Initialization Options (JSON)", draft.initializationOptions, { draft = draft.copy(initializationOptions = it) }, "{}", singleLine = false)
                ConfigField("Settings (JSON)", draft.settings, { draft = draft.copy(settings = it) }, "{}", singleLine = false)
                Text(
                    "It starts when a file of its languages or patterns opens in a workspace. Saving changes to a running server starts it again.",
                    style = IcodeType.Caption,
                    color = IcodeColors.TextSecondary,
                )
                validation?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
                PrimaryButton(
                    if (service == null) "Add Service" else "Save Configuration",
                    onClick = { gateway.dispatch(LanguageIntent.Save(draft.copy(id = service?.id.orEmpty()))) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = validation == null && state.languageServicesLoadError == null && (service == null || draft != saved),
                )
            }
            if (service != null) {
                ServerSection(service, onRemove = { confirmRemove = true })
                LogSection(state.languageServiceLog)
            }
        }
    }
    if (confirmRemove && service != null) {
        IcodeConfirmSheet(
            title = "Remove ${service.name}?",
            confirmLabel = "Remove",
            onConfirm = {
                gateway.dispatch(LanguageIntent.Remove(service.id))
                confirmRemove = false
                actions.back()
            },
            onDismiss = { confirmRemove = false },
            destructive = true,
        ) {
            Text("This removes the saved configuration and stops its server. It does not uninstall the server.", style = IcodeType.Body)
        }
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        Text(label, style = IcodeType.Caption)
        IcodeTextField(value, onValueChange, modifier = Modifier.fillMaxWidth(), placeholder = placeholder, singleLine = singleLine)
    }
}
