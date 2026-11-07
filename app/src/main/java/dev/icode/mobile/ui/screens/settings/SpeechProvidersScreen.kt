package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.SpeechIntent
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › Voice › Speech Recognition (docs/speech-plan.md §5): the providers that can
 * hear the user, Android's first, the one in use marked; + adds one, by its kind.
 */
@Composable
fun SpeechProvidersScreen(actions: NavigationActions) {
    val gateway = LocalICodeGateway.current
    val voice = LocalICodeSnapshot.current.voice
    var picking by rememberSaveable { mutableStateOf(false) }
    fun open(provider: SpeechProvider) {
        picking = false
        gateway.dispatch(SpeechIntent.Edit(provider))
        actions.navigate(Route.SpeechProvider)
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(picking)) {
            IcodeTopBar(
                "Speech Recognition",
                subtitle = "What hears the microphone",
                onBack = actions.back,
                actions = {
                    IconButton(onClick = { picking = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add provider", tint = IcodeColors.TextPrimary)
                    }
                },
            )
            IcodePageColumn(Modifier.weight(1f)) {
                IcodeSection("Providers") {
                    voice.providers.forEach { provider ->
                        SpeechProviderRow(provider, inUse = provider.id == voice.inUse, failure = voice.failures[provider.id]) { open(provider) }
                    }
                }
                Text(
                    "The one in use hears the bubble and the chat's microphone. Audio goes only to it.",
                    style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
                )
            }
        }
        if (picking) {
            IcodeSheetOverlay(onClose = { picking = false }) {
                SpeechKindSheet(Modifier.align(Alignment.BottomCenter), onClose = { picking = false }) { kind ->
                    open(SpeechProvider("", kind, kind.label))
                }
            }
        }
    }
}

@Composable
private fun SpeechProviderRow(provider: SpeechProvider, inUse: Boolean, failure: String?, onClick: () -> Unit) {
    IcodeListRow(
        card = true,
        selected = inUse,
        onClick = onClick,
        leading = { KindBadge(provider.kind, highlighted = inUse) },
        content = {
            Column(Modifier.weight(1f)) {
                Text(provider.name, style = IcodeType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(SpeechRules.where(provider), style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    failure != null -> StatusLabel(failure, IcodeColors.Error)
                    inUse -> StatusLabel("In use", IcodeColors.Green)
                }
            }
        },
        trailing = { Chevron() },
    )
}

/** The kinds a provider can be, as a sheet: where + starts. Android's is always there, so it is not offered. */
@Composable
private fun SpeechKindSheet(modifier: Modifier, onClose: () -> Unit, onPick: (SpeechProviderKind) -> Unit) {
    IcodeBottomSheet(title = "Add Provider", subtitle = "Choose who hears you", onClose = onClose, modifier = modifier) {
        SpeechProviderKind.entries.filter { it != SpeechProviderKind.Android }.forEach { kind ->
            IcodeListRow(
                card = true,
                title = kind.label,
                subtitle = kind.description,
                subtitleStyle = IcodeType.Caption,
                onClick = { onPick(kind) },
                leading = { KindBadge(kind, highlighted = false) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary) },
            )
        }
    }
}

@Composable
internal fun KindBadge(kind: SpeechProviderKind, highlighted: Boolean) {
    IconBadge(
        text = kind.label.filter(Char::isLetterOrDigit).take(2).uppercase(),
        tint = if (highlighted) IcodeColors.Accent else IcodeColors.TextSecondary,
        size = IcodeSizes.BadgeSm,
    )
}

/** What a kind is, in a few words: whether words come as the user speaks, and what it needs. */
internal val SpeechProviderKind.description: String
    get() = listOfNotNull(
        if (live) "Words as you speak" else "Words once you stop",
        "needs an API key".takeIf { needsKey },
        "a server you name".takeIf { asksAddress },
    ).joinToString(" · ")
