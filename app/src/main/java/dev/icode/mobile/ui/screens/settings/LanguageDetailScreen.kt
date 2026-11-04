package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.LanguageServiceRules
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** Persisted server configuration plus observed guest-process status. */
@Composable
fun LanguageDetailScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val service = state.languageServices.firstOrNull { it.id == state.selectedLanguageServiceId }
    var name by remember(service?.id) { mutableStateOf(service?.name.orEmpty()) }
    var command by remember(service?.id) { mutableStateOf(service?.command.orEmpty()) }
    var patterns by remember(service?.id) { mutableStateOf(service?.patterns.orEmpty()) }
    var rootMarkers by remember(service?.id) { mutableStateOf(service?.rootMarkers.orEmpty()) }
    var options by remember(service?.id) { mutableStateOf(service?.initializationOptions ?: "{}") }
    var confirmRemove by remember { mutableStateOf(false) }
    val status = service?.status ?: LanguageServiceStatus.Stopped
    val canEdit = status == LanguageServiceStatus.Stopped || status == LanguageServiceStatus.Failed
    val validation = LanguageServiceRules.validate(name, command, patterns, options)

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            service?.name ?: "Add Language Service",
            onBack = actions.back,
            actions = {
                if (service != null) StatusLabel(status.name, status.tone())
            },
        )
        IcodePageColumn(Modifier.weight(1f)) {
            IcodeSection("Configuration") {
                ConfigField("Name", name, { name = it }, "e.g. TypeScript Language Server")
                ConfigField("Command", command, { command = it }, "e.g. typescript-language-server --stdio")
                ConfigField("File Patterns", patterns, { patterns = it }, "e.g. *.ts, *.tsx")
                ConfigField("Root Markers", rootMarkers, { rootMarkers = it }, "e.g. tsconfig.json, package.json")
                ConfigField("Initialization Options (JSON)", options, { options = it }, "{}", singleLine = false)
                Text(
                    "File patterns, root markers and initialization options are saved for the LSP integration cycle. " +
                        "They do not enable editor completion or diagnostics yet.",
                    style = IcodeType.Caption,
                    color = IcodeColors.TextSecondary,
                )
                if (!canEdit) Text("Stop the process before changing its configuration.", style = IcodeType.Caption)
                PrimaryButton(
                    if (service == null) "Add Service" else "Save Configuration",
                    onClick = {
                        gateway.dispatch(ICodeIntent.SaveLanguageService(
                            service?.id, name, command, patterns, rootMarkers, options,
                        ))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canEdit && validation == null && state.languageServicesLoadError == null,
                )
            }
            if (service != null) {
                IcodeSection("Process Status") {
                    IcodeCard(Modifier.fillMaxWidth()) {
                        DetailRow("State", status.name)
                        DetailRow("Host Process ID", service.processId?.toString() ?: "—")
                        service.statusMessage?.let { Text(it, style = IcodeType.Caption) }
                        Text(
                            "A running process has not completed an LSP handshake. Capabilities are unavailable.",
                            style = IcodeType.Caption,
                            color = IcodeColors.TextSecondary,
                        )
                    }
                    when (status) {
                        LanguageServiceStatus.Stopped, LanguageServiceStatus.Failed ->
                            PrimaryButton("Start Server", { gateway.dispatch(ICodeIntent.StartLanguageService(service.id)) }, Modifier.fillMaxWidth())
                        LanguageServiceStatus.Running -> {
                            SecondaryButton("Restart Server", { gateway.dispatch(ICodeIntent.RestartLanguageService(service.id)) }, Modifier.fillMaxWidth())
                            SecondaryButton("Stop Server", { gateway.dispatch(ICodeIntent.StopLanguageService(service.id)) }, Modifier.fillMaxWidth())
                        }
                        LanguageServiceStatus.Starting, LanguageServiceStatus.Stopping -> Unit
                    }
                    DangerButton("Remove Service", { confirmRemove = true }, Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (confirmRemove && service != null) {
        IcodeConfirmSheet(
            title = "Remove ${service.name}?",
            confirmLabel = "Remove",
            onConfirm = {
                gateway.dispatch(ICodeIntent.RemoveLanguageService(service.id))
                confirmRemove = false
                actions.back()
            },
            onDismiss = { confirmRemove = false },
            destructive = true,
        ) {
            Text("This removes the saved configuration and stops its process. It does not uninstall the server package.", style = IcodeType.Body)
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
        IcodeTextField(
            value,
            onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            singleLine = singleLine,
        )
    }
}
