package dev.icode.mobile.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.domain.AssistantProviderKind

/** The kinds of provider, as a sheet: where + on Providers starts, and where a new one's kind changes. */
@Composable
internal fun ProviderKindSheet(
    selected: AssistantProviderKind?,
    modifier: Modifier,
    onClose: () -> Unit,
    onPick: (AssistantProviderKind) -> Unit,
) {
    IcodeBottomSheet(title = "Add Provider", subtitle = "Choose the kind of model provider", onClose = onClose, modifier = modifier) {
        AssistantProviderKind.entries.forEach { kind ->
            IcodeListRow(
                card = true,
                selected = kind == selected,
                title = kind.label,
                subtitle = kind.description,
                subtitleStyle = IcodeType.Caption,
                onClick = { onPick(kind) },
                leading = {
                    IconBadge(
                        text = kind.label.filter(Char::isLetterOrDigit).take(2).uppercase(),
                        tint = if (kind == selected) IcodeColors.Accent else IcodeColors.TextSecondary,
                        size = IcodeSizes.BadgeSm,
                    )
                },
                trailing = {
                    if (kind == selected) {
                        Icon(Icons.Outlined.Check, contentDescription = "Chosen", tint = IcodeColors.Accent)
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                    }
                },
            )
        }
    }
}

internal val AssistantProviderKind.description: String
    get() = when (this) {
        AssistantProviderKind.Ollama -> "A model server; on this phone by default"
        AssistantProviderKind.OpenAICompatible -> "DeepSeek, Groq, LM Studio and other servers"
        else -> "Needs an API key"
    }
