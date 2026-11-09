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
import dev.icode.mobile.designsystem.BottomBarItem
import dev.icode.mobile.designsystem.IcodeBottomBar
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
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.BusyRules
import dev.icode.mobile.domain.LanguageIntent
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServicePresets
import dev.icode.mobile.domain.LanguageServiceRules
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.ToolPanelTab
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * The pages of a language service, one on screen at a time, as Git Credentials' steps are: a new one
 * starts from a known server or nothing, then is configured; a saved one has its configuration, its
 * server and its log. All of them on one page buried the one the user needed.
 */
internal enum class LanguagePage(val caption: String, val short: String) {
    StartFrom("Start from a known server, or your own", "Start From"),
    Configuration("How it runs and what it serves", "Configuration"),
    Server("Its state and what it offers", "Server"),
    Log("What iCode saw of it, and its errors", "Log"),
    ;

    companion object {
        fun of(saved: Boolean): List<LanguagePage> = if (saved) listOf(Configuration, Server, Log) else listOf(StartFrom, Configuration)
    }
}

/** A language service's configuration, its server's state and log (docs/lsp-plan.md §7). */
@Composable
fun LanguageDetailScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val service = state.languageServices.firstOrNull { it.id == state.selectedLanguageServiceId }
    val saved = service?.let(LanguageServiceRules::configurationOf) ?: LanguageServiceConfiguration("", "", "")
    var draft by rememberSaveable(service?.id, stateSaver = ConfigurationSaver) { mutableStateOf(saved) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val pages = LanguagePage.of(saved = service != null)
    // Keyed on the service: once added, its page opens on Configuration, its first.
    var page by rememberSaveable(service?.id) { mutableStateOf(pages.first()) }
    val status = service?.status ?: LanguageServiceStatus.Stopped
    // The install command typed in a new terminal tab, shown at once, where sudo or npm can be answered.
    val install: (String) -> Unit = { command ->
        gateway.dispatch(LanguageIntent.Install(command))
        actions.openPanel(ToolPanelTab.Terminal)
    }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            service?.name ?: "Add Language Service",
            subtitle = page.caption,
            onBack = actions.back,
            actions = { if (service != null) StatusLabel(service.statusLabel, status.tone()) },
        )
        IcodePageColumn(Modifier.weight(1f)) {
            when (page) {
                LanguagePage.StartFrom -> PresetSection(
                    onChoose = { preset -> draft = preset.configuration; page = LanguagePage.Configuration },
                    onCustom = { draft = LanguageServiceConfiguration("", "", ""); page = LanguagePage.Configuration },
                )
                LanguagePage.Configuration -> ConfigurationSection(
                    draft = draft,
                    onDraft = { draft = it },
                    adding = service == null,
                    canSave = state.languageServicesLoadError == null && (service == null || draft != saved),
                    onSave = { gateway.dispatch(LanguageIntent.Save(draft.copy(id = service?.id.orEmpty()))) },
                    saving = state.isBusy(BusyAction.LanguageService, service?.id ?: BusyRules.NEW),
                    installing = state.isBusy(BusyAction.LanguageService, draft.command.trim()),
                    onInstall = { install(draft.command.trim()) },
                )
                LanguagePage.Server -> service?.let { ServerSection(it, onRemove = { confirmRemove = true }, onInstall = { install(it.command) }) }
                LanguagePage.Log -> LogSection(state.languageServiceLog)
            }
        }
        IcodeBottomBar(
            items = pages.mapIndexed { index, entry ->
                BottomBarItem(
                    if (service == null) "${index + 1} ${entry.short}" else entry.short,
                    onClick = { page = entry },
                    active = entry == page,
                )
            },
        )
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

/** The fields a service runs with, and the button that saves them. */
@Composable
private fun ConfigurationSection(
    draft: LanguageServiceConfiguration,
    onDraft: (LanguageServiceConfiguration) -> Unit,
    adding: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
    saving: Boolean,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    val validation = LanguageServiceRules.validate(draft)
    val install = LanguageServicePresets.all.firstOrNull { it.configuration.command == draft.command.trim() }
    IcodeSection("Server") {
        ConfigField("Name", draft.name, { onDraft(draft.copy(name = it)) }, "e.g. Python (pylsp)")
        ConfigField("Command", draft.command, { onDraft(draft.copy(command = it)) }, "e.g. typescript-language-server --stdio")
        install?.let { preset ->
            Text("Install it in a terminal" + (preset.needs?.let { ", after $it" } ?: "") + ":", style = IcodeType.Caption, color = IcodeColors.TextSecondary)
            MonoLine(preset.install, maxLines = 2)
            SecondaryButton("Install in Terminal", onInstall, Modifier.fillMaxWidth(), busy = installing)
        }
    }
    IcodeSection("Files It Serves") {
        ConfigField("Languages", draft.languages, { onDraft(draft.copy(languages = it)) }, "e.g. typescript, typescriptreact")
        ConfigField("File Patterns", draft.patterns, { onDraft(draft.copy(patterns = it)) }, "e.g. *.ts, *.tsx")
        ConfigField("Root Markers", draft.rootMarkers, { onDraft(draft.copy(rootMarkers = it)) }, "e.g. tsconfig.json, package.json")
        Text(
            "It starts when a file of its languages or patterns opens in a workspace.",
            style = IcodeType.Caption,
            color = IcodeColors.TextSecondary,
        )
    }
    IcodeSection("Advanced") {
        ConfigField("Environment", draft.environment, { onDraft(draft.copy(environment = it)) }, "NAME=value, one a line", singleLine = false)
        ConfigField("Initialization Options (JSON)", draft.initializationOptions, { onDraft(draft.copy(initializationOptions = it)) }, "{}", singleLine = false)
        ConfigField("Settings (JSON)", draft.settings, { onDraft(draft.copy(settings = it)) }, "{}", singleLine = false)
    }
    IcodeSection {
        if (!adding) Text("Saving changes to a running server starts it again.", style = IcodeType.Caption, color = IcodeColors.TextSecondary)
        validation?.let { Text(it, style = IcodeType.Caption, color = IcodeColors.Error) }
        PrimaryButton(
            if (adding) "Add Service" else "Save Configuration",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = validation == null && canSave,
            busy = saving,
        )
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
