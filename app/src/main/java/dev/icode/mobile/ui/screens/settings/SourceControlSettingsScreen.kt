package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot
import dev.icode.mobile.ui.screens.tools.AssistantModelsSheet

/**
 * Source Control's settings: Git's credentials, and the model that writes commit messages
 * (docs/commit-message-generator-plan.md M5, M8) — the assistant's, unless another is chosen here.
 */
@Composable
fun SourceControlSettingsScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val state = LocalICodeSnapshot.current.assistant
    val settings = state.settings
    var picking by rememberSaveable { mutableStateOf(false) }
    // The effective model being used for commit messages: explicit choice, or the assistant's.
    val effectiveChoice = settings.commitChoice ?: settings.choice
    // What the row says of the choice: the model, then who hosts it.
    val label = effectiveChoice?.model ?: "No model yet"
    val hint = effectiveChoice?.let { choice -> settings.providers.firstOrNull { it.id == choice.provider }?.name }
        ?: "No model yet"
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().then(if (picking) Modifier.blur(IcodeSizes.SheetBackdropBlur) else Modifier)) {
            IcodeTopBar("Source Control", subtitle = settingsSubtitle(Route.SourceControlSettings), onBack = actions.back)
            IcodePageColumn(Modifier.weight(1f)) {
                IcodeSection {
                    SettingRow(
                        "Git Credentials",
                        Modifier.fillMaxWidth(),
                        subtitle = "Commit identity, token and SSH key",
                        leadingIcon = Icons.Outlined.AccountTree,
                        onClick = { actions.navigate(Route.GitAuth) },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                    }
                    SettingRow(
                        "Commit Message Model",
                        Modifier.fillMaxWidth(),
                        subtitle = "$label · $hint",
                        leadingIcon = Icons.Outlined.AutoAwesome,
                        onClick = { picking = true },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                    }
                    if (settings.providers.isEmpty()) {
                        SecondaryButton("Add a Model Provider", { actions.navigate(Route.AssistantSettings) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (picking) {
            val close = { picking = false }
            IcodeSheetOverlay(onClose = close) {
                AssistantModelsSheet(
                    state,
                    Modifier.align(Alignment.BottomCenter),
                    onClose = close,
                    onProviders = { actions.navigate(Route.AssistantSettings) },
                    title = "Commit Message Model",
                    chosen = { it == effectiveChoice },
                    onChoose = { gateway.dispatch(ICodeIntent.ChooseCommitModel(it)) },
                    showEffort = false,
                )
            }
        }
    }
}
