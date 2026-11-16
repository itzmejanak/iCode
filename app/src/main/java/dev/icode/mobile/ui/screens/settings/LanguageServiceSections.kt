package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.BusyAction
import dev.icode.mobile.domain.LanguageIntent
import dev.icode.mobile.domain.LanguageService
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServicePreset
import dev.icode.mobile.domain.LanguageServicePresets
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.isBusy
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** A new service starts from a known server, filled in for it, or from nothing (Custom). */
@Composable
internal fun PresetSection(onChoose: (LanguageServicePreset) -> Unit, onCustom: () -> Unit) {
    IcodeSection("Known Servers") {
        LanguageServicePresets.all.forEach { preset ->
            IcodeListRow(
                title = preset.configuration.name,
                subtitle = preset.configuration.command,
                card = true,
                onClick = { onChoose(preset) },
            )
        }
    }
    IcodeSection("Your Own") {
        IcodeListRow(
            title = "Custom",
            subtitle = "Any language server installed in the runtime",
            card = true,
            onClick = onCustom,
        )
    }
}

/** The server: its state, what it said it can do, and the buttons that start and stop it. */
@Composable
internal fun ServerSection(service: LanguageService, onRemove: () -> Unit, onInstall: () -> Unit) {
    val gateway = LocalICodeGateway.current
    // Start, Stop, Restart and Remove: one at a time, each shown working from its tap.
    val state = LocalICodeSnapshot.current
    val acting = state.isBusy(BusyAction.LanguageService, service.id)
    val installing = state.isBusy(BusyAction.LanguageService, service.command)
    IcodeSection("Server") {
        IcodeCard(Modifier.fillMaxWidth()) {
            DetailRow("State", service.status.name)
            DetailRow("Process ID", service.processId?.toString() ?: "—")
            service.statusMessage?.let { Text(it, style = IcodeType.Caption) }
            Text(
                if (service.capabilities.isEmpty()) "What it offers shows once it has started."
                else "Offers: " + service.capabilities.joinToString(", "),
                style = IcodeType.Caption,
                color = IcodeColors.TextSecondary,
            )
        }
        if (service.notInstalled) {
            Text(
                service.install?.let { "Its program is not in the Linux runtime. It installs with:" } ?: "Its program is not in the Linux runtime: install it in a terminal.",
                style = IcodeType.Caption,
                color = IcodeColors.Amber,
            )
            service.install?.let { command ->
                MonoLine(command, maxLines = 2)
                PrimaryButton("Install in Terminal", onInstall, Modifier.fillMaxWidth(), busy = installing)
            }
        }
        when (service.status) {
            LanguageServiceStatus.Stopped, LanguageServiceStatus.Failed -> {
                val start = { gateway.dispatch(LanguageIntent.Start(service.id)) }
                // Installing comes first while its program is missing.
                if (service.notInstalled && service.install != null) SecondaryButton("Start Server", start, Modifier.fillMaxWidth(), busy = acting)
                else PrimaryButton("Start Server", start, Modifier.fillMaxWidth(), busy = acting)
            }
            LanguageServiceStatus.Running -> {
                // Either changes the state at once (Stopping), so neither needs a spinner; a tap meanwhile does nothing.
                SecondaryButton("Restart Server", { gateway.dispatch(LanguageIntent.Restart(service.id)) }, Modifier.fillMaxWidth(), enabled = !acting)
                SecondaryButton("Stop Server", { gateway.dispatch(LanguageIntent.Stop(service.id)) }, Modifier.fillMaxWidth(), enabled = !acting)
            }
            LanguageServiceStatus.Starting -> SecondaryButton("Stop Server", { gateway.dispatch(LanguageIntent.Stop(service.id)) }, Modifier.fillMaxWidth(), busy = acting)
            LanguageServiceStatus.Stopping -> Unit
        }
        DangerButton("Remove Service", onRemove, Modifier.fillMaxWidth(), enabled = !acting)
    }
}

/** What iCode saw of the server, then its stderr's last lines (docs/editor-and-language-services.md). */
@Composable
internal fun LogSection(lines: List<String>) {
    val gateway = LocalICodeGateway.current
    IcodeSection("Log") {
        IcodeCard(Modifier.fillMaxWidth()) {
            Column {
                if (lines.isEmpty()) Text("Nothing yet.", style = IcodeType.Caption, color = IcodeColors.TextSecondary)
                lines.takeLast(SHOWN_LINES).forEach { MonoLine(it, style = IcodeType.MonoSmall, maxLines = 4) }
            }
        }
        SecondaryButton("Refresh Log", { gateway.dispatch(LanguageIntent.RefreshLog) }, Modifier.fillMaxWidth())
    }
}

private const val SHOWN_LINES = 80

/** The form's draft across a rotation or a trip to another page. */
internal val ConfigurationSaver = Saver<LanguageServiceConfiguration, List<String>>(
    save = { listOf(it.id, it.name, it.command, it.languages, it.patterns, it.rootMarkers, it.environment, it.initializationOptions, it.settings) },
    restore = { LanguageServiceConfiguration(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8]) },
)
