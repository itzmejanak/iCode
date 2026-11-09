package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.LanguageService
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.LanguageIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/** Language server list with running state. */
@Composable
fun LanguageServicesScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val services = state.languageServices
    val gateway = LocalICodeGateway.current
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar(
            "Language Services",
            onBack = actions.back,
            actions = {
                IconButton(enabled = state.languageServicesLoadError == null, onClick = {
                    gateway.dispatch(LanguageIntent.Select(""))
                    actions.navigate(Route.SettingsLanguageDetail)
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add language service", tint = IcodeColors.TextPrimary)
                }
            },
        )
        IcodePageColumn(Modifier.weight(1f)) {
            state.languageServicesLoadError?.let { error ->
                Text("Saved services could not be read: $error", style = IcodeType.Caption, color = IcodeColors.Error)
            }
            IcodeSection {
                services.forEach { service ->
                    LanguageServiceRow(service) {
                        gateway.dispatch(LanguageIntent.Select(service.id))
                        actions.navigate(Route.SettingsLanguageDetail)
                    }
                }
                Text(
                    "Add a language server from a known one or your own; install it in a terminal. It starts when a file it serves opens.",
                    modifier = Modifier.fillMaxWidth(),
                    style = IcodeType.Caption,
                    textAlign = TextAlign.Center,
                )
            }
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
                MonoLine(service.servesLabel, style = IcodeType.MonoSmall)
            }
        },
        trailing = {
            StatusLabel(service.statusLabel, service.status.tone())
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
        },
    )
}

/** Its state in a word: "Not installed" says more than Failed when that is why. */
internal val LanguageService.statusLabel: String get() = if (notInstalled) "Not installed" else status.name

/** One colour per process state, on the list and on a service's page. */
@Composable
internal fun LanguageServiceStatus.tone(): Color = when (this) {
    LanguageServiceStatus.Running -> IcodeColors.Green
    LanguageServiceStatus.Failed -> IcodeColors.Error
    LanguageServiceStatus.Starting, LanguageServiceStatus.Stopping -> IcodeColors.Amber
    LanguageServiceStatus.Stopped -> IcodeColors.TextSecondary
}
