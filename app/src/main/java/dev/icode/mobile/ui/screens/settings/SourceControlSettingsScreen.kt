package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeDropdown
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Source Control's settings: Git's credentials, and the model that writes commit messages
 * (docs/commit-message-generator-plan.md M5, M8) — the assistant's, unless another is chosen here.
 */
@Composable
fun SourceControlSettingsScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val settings = LocalICodeSnapshot.current.assistant.settings
    val choices = listOf<AssistantChoice?>(null) + settings.providers.flatMap { provider -> provider.models.map { AssistantChoice(provider.id, it.id) } }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Source Control", subtitle = settingsSubtitle(Route.SourceControlSettings), onBack = actions.back)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4),
        ) {
            SettingRow(
                "Git Credentials",
                Modifier.fillMaxWidth(),
                subtitle = "Commit identity, token and SSH key",
                leadingIcon = Icons.Outlined.AccountTree,
                onClick = { actions.navigate(Route.GitAuth) },
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
            }
            IcodeDropdown(
                label = "Commit Message Model",
                options = choices,
                selected = settings.commitChoice,
                optionLabel = { choice -> choice?.model ?: "The assistant's model" },
                optionDescription = { choice ->
                    if (choice != null) {
                        settings.providers.firstOrNull { it.id == choice.provider }?.name
                    } else {
                        settings.model?.let { model -> "Now ${model.id}" } ?: "No model yet"
                    }
                },
                onSelect = { gateway.dispatch(ICodeIntent.ChooseCommitModel(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "✨ in Source Control's message field writes a commit message from the staged changes, which are sent to this " +
                    "model's provider. The message is yours to edit before you commit.",
                style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
            )
            if (settings.providers.isEmpty()) {
                SecondaryButton("Add a Model Provider", { actions.navigate(Route.AssistantSettings) }, Modifier.fillMaxWidth())
            }
        }
    }
}
