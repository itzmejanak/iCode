package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.LanguageService
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Language server list with running state. */
@Composable
fun LanguageServicesScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val services = state.languageServices
    val gateway = LocalWorkbenchGateway.current
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Language Services",
            onBack = actions.back,
            actions = {
                IconButton(enabled = state.languageServicesLoadError == null, onClick = {
                    gateway.dispatch(WorkbenchIntent.SelectLanguageService(""))
                    actions.navigate(Route.SettingsLanguageDetail)
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add language service", tint = IcodeColors.TextPrimary)
                }
            },
        )
        Column(
            modifier = Modifier.padding(IcodeSpacing.Space4).weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2 + IcodeSpacing.Micro),
        ) {
            state.languageServicesLoadError?.let { error ->
                Text("Saved services could not be read: $error", style = IcodeType.Caption, color = IcodeColors.Error)
            }
            services.forEach { service ->
                LanguageServiceRow(service) {
                    gateway.dispatch(WorkbenchIntent.SelectLanguageService(service.id))
                    actions.navigate(Route.SettingsLanguageDetail)
                }
            }
            Text(
                "Install a language server in the terminal, then add its configuration here.",
                modifier = Modifier.fillMaxWidth().padding(bottom = IcodeSpacing.Space2 + IcodeSpacing.Micro),
                style = IcodeType.Caption,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LanguageServiceRow(service: LanguageService, onClick: () -> Unit) {
    IcodeListRow(
        card = true,
        onClick = onClick,
        leading = {
            IconBadge(
                text = service.initials,
                tint = if (service.status == LanguageServiceStatus.Running) IcodeColors.Blue else IcodeColors.Amber,
                size = IcodeSizes.BadgeSm,
            )
        },
        content = {
            Column(Modifier.weight(1f)) {
                Text(service.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                MonoLine(service.patterns, style = IcodeType.MonoSmall)
            }
        },
        trailing = {
            StatusLabel(
                service.status.name,
                if (service.status == LanguageServiceStatus.Running) IcodeColors.Green else IcodeColors.TextSecondary,
            )
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
        },
    )
}
