package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

/** Language server list with running state. */
@Composable
fun LanguageServicesScreen(actions: DemoActions) {
    val services = LocalWorkbenchSnapshot.current.languageServices
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Language Services",
            onBack = actions.back,
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add language service", tint = IcodeColors.TextPrimary)
                }
            },
        )
        Column(
            modifier = Modifier.padding(IcodeSpacing.Space4).weight(1f),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2 + IcodeSpacing.Micro),
        ) {
            services.forEach { service ->
                LanguageServiceRow(service) { actions.navigate(DemoScreen.SettingsLanguageDetail) }
            }
            Spacer(Modifier.weight(1f))
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
                tint = if (service.running) IcodeColors.Blue else IcodeColors.Amber,
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
                if (service.running) "Running" else "Stopped",
                if (service.running) IcodeColors.Green else IcodeColors.TextSecondary,
            )
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
        },
    )
}
