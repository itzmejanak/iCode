package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
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
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.domain.SpeechIntent
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.domain.voice.SpeechWay
import dev.icode.mobile.domain.voice.VoiceState
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

/**
 * Settings › Assistant › Voice › Speech Providers (docs/voice-and-phone-control.md): the
 * providers that hear the user or read the answers aloud, Android's first, the one that listens and the
 * one that speaks marked; + adds one, by its kind.
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
                "Speech Providers",
                subtitle = "What hears you and reads aloud",
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
                        val ways = SpeechWay.entries.filter { voice.chosenId(it) == provider.id }
                        SpeechProviderRow(provider, ways, failure = voice.failures[provider.id]) { open(provider) }
                    }
                }
                Text(
                    "The microphone's audio goes only to the one that listens, and an answer read aloud only to the one that speaks.",
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

/** A provider, and each way it serves now. */
@Composable
private fun SpeechProviderRow(provider: SpeechProvider, ways: List<SpeechWay>, failure: String?, onClick: () -> Unit) {
    val inUse = ways.isNotEmpty()
    IcodeListRow(
        card = true,
        selected = inUse,
        onClick = onClick,
        leading = { KindBadge(provider.kind, highlighted = inUse) },
        content = {
            Column(Modifier.weight(1f)) {
                Text(provider.name, style = IcodeType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(provider.kind.label, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    failure != null -> StatusLabel(failure, IcodeColors.Error)
                    inUse -> StatusLabel(ways.joinToString(" · ") { it.doing }, IcodeColors.Green)
                }
            }
        },
        trailing = { Chevron() },
    )
}

/** What a provider does in [this] way, as its row says it. */
internal val SpeechWay.doing: String
    get() = when (this) {
        SpeechWay.Listening -> "Listens"
        SpeechWay.Speaking -> "Speaks"
    }

/**
 * Who serves [way], as a sheet from the bottom of the Voice page (docs/voice-and-phone-control.md): the providers that
 * can, the one chosen checked, and Providers › at its foot, where one is added or changed.
 */
@Composable
internal fun SpeechChooserSheet(way: SpeechWay, voice: VoiceState, modifier: Modifier, onClose: () -> Unit, onProviders: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val (title, subtitle) = when (way) {
        SpeechWay.Listening -> "Speech Recognition" to "Who hears you"
        SpeechWay.Speaking -> "Voice" to "Who reads the answers aloud"
    }
    IcodeBottomSheet(
        title = title,
        subtitle = subtitle,
        onClose = onClose,
        modifier = modifier,
        footer = {
            SettingRow("Providers", Modifier.fillMaxWidth(), subtitle = "Add one, or change one", leadingIcon = Icons.Outlined.Tune, onClick = onProviders) { Chevron() }
        },
    ) {
        voice.providers.filter { it.kind.serves(way) }.forEach { provider ->
            val chosen = provider.id == voice.chosenId(way)
            IcodeListRow(
                card = true,
                selected = chosen,
                title = provider.name,
                subtitle = SpeechRules.where(provider, way).ifEmpty { provider.kind.label },
                subtitleStyle = IcodeType.Caption,
                onClick = {
                    gateway.dispatch(SpeechIntent.Use(provider.id, way))
                    onClose()
                },
                leading = { KindBadge(provider.kind, highlighted = chosen) },
                trailing = { if (chosen) Icon(Icons.Outlined.Check, contentDescription = "Chosen", tint = IcodeColors.Accent) },
            )
        }
    }
}

/** The kinds a provider can be, as a sheet: where + starts. Android's is always there, so it is not offered. */
@Composable
private fun SpeechKindSheet(modifier: Modifier, onClose: () -> Unit, onPick: (SpeechProviderKind) -> Unit) {
    IcodeBottomSheet(title = "Add Provider", subtitle = "Choose its kind", onClose = onClose, modifier = modifier) {
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

/** What a kind is, in a few words: whether words come as the user speaks, whether it reads aloud, and what it needs. */
internal val SpeechProviderKind.description: String
    get() = listOfNotNull(
        when {
            !hears -> "Reads aloud"
            live -> "Words as you speak"
            else -> "Words once you stop"
        },
        "reads aloud".takeIf { hears && speaking != null },
        "needs an API key".takeIf { needsKey },
        "a server you name".takeIf { asksAddress },
    ).joinToString(" · ")
