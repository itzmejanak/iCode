package dev.icode.mobile.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.StatusLabel
import dev.icode.mobile.domain.SpeechIntent
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.voice.Regions
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechWay
import dev.icode.mobile.domain.voice.VoiceChoices
import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.domain.voice.VoiceOption
import dev.icode.mobile.domain.voice.VoiceRules
import dev.icode.mobile.domain.voice.VoiceState
import dev.icode.mobile.domain.voice.Voices
import dev.icode.mobile.ui.LocalICodeGateway
import java.util.Locale

// The parts of a speech provider's page (docs/speech-plan.md §5, docs/tts-plan.md §5).

/** A voice chosen from those [saved]'s API listed ([choices]), or why they could not be listed, or that they are being asked for. */
@Composable
internal fun Listed(label: String, saved: SpeechProvider?, choices: VoiceChoices?, options: List<VoiceOption>?, chosen: String, onChoose: (String) -> Unit) {
    val gateway = LocalICodeGateway.current
    when {
        saved == null -> WhereItGoes("Once it is added, its ${label.lowercase()}s are listed here.")
        choices == null -> WhereItGoes("Asking ${saved.name} for its ${label.lowercase()}s…")
        choices.problem != null -> {
            Text(choices.problem, style = IcodeType.Caption.copy(color = IcodeColors.Error))
            SecondaryButton("Try Again", { gateway.dispatch(SpeechIntent.Choices(saved.id)) }, Modifier.fillMaxWidth())
        }
        else -> ListedSelect(label, options.orEmpty(), chosen, onChoose)
    }
}

/** Where [regions] are: Azure's typed, as Azure names it; the others' chosen from their servers, the first by default. */
@Composable
internal fun RegionField(regions: Regions, region: String, onChange: (String) -> Unit) {
    when (regions) {
        is Regions.Typed -> LabeledTextField("Region", region, onChange, placeholder = regions.example, literal = true)
        is Regions.Fixed -> IcodeSelect(
            options = regions.hosts.map { it.second },
            selected = region.ifEmpty { regions.hosts.first().second },
            optionLabel = { host -> regions.hosts.first { it.second == host }.first },
            optionDescription = { it },
            onSelect = { host -> onChange(host.takeIf { it != regions.hosts.first().second }.orEmpty()) },
            label = "Servers",
        )
    }
}

/**
 * One of the voices [voices] fixes for [provider]'s language, the first by default; where an account's
 * own voices are named too, Its Own Voice takes one by its name.
 */
@Composable
internal fun FixedVoice(provider: SpeechProvider, voices: Voices.Fixed, chosen: String, onChoose: (String) -> Unit) {
    val offered = VoiceRules.voices(provider, Locale.getDefault().toLanguageTag())
    var own by rememberSaveable(provider.id) { mutableStateOf(chosen.isNotEmpty() && chosen !in offered) }
    if (offered.isEmpty()) {
        WhereItGoes("${provider.name} has no voice for this language.")
        return
    }
    val options = offered + listOfNotNull(OWN.takeIf { voices.own })
    IcodeSelect(
        options = options,
        selected = if (own) OWN else chosen.takeIf { it in offered } ?: offered.first(),
        optionLabel = { if (it == OWN) "Its Own Voice" else it },
        onSelect = { picked ->
            own = picked == OWN
            onChoose(if (own) "" else picked.takeIf { it != offered.first() }.orEmpty())
        },
        label = "Voice",
    )
    if (own) LabeledTextField("Its Own Voice", chosen, onChoose, placeholder = "Its name, as the dashboard shows it", literal = true)
}

/** The choice that takes an account's own voice by its name. */
private const val OWN = "\u0000own"

/** One of [listed], or Default — the provider's own choice. One it no longer lists stays shown, so saving again does not change it unseen. */
@Composable
internal fun ListedSelect(label: String, listed: List<VoiceOption>, chosen: String, onChoose: (String) -> Unit) {
    val ids = listOf("") + listed.map { it.id } + listOfNotNull(chosen.takeIf { it.isNotEmpty() && listed.none { option -> option.id == it } })
    IcodeSelect(
        options = ids,
        selected = chosen,
        optionLabel = { id -> if (id.isEmpty()) "Default" else listed.firstOrNull { it.id == id }?.label ?: id },
        onSelect = onChoose,
        label = label,
    )
}

/** Where a way's audio or text goes, said under its fields. */
@Composable
internal fun WhereItGoes(words: String) {
    Text(words, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
}

/** One of Android's own settings pages, which iCode opens: Android keeps them. */
@Composable
internal fun AndroidSettings(title: String, subtitle: String, icon: ImageVector, page: Intent) {
    val context = LocalContext.current
    SettingRow(
        title,
        Modifier.fillMaxWidth(),
        subtitle = subtitle,
        leadingIcon = icon,
        onClick = { context.openFirst(page, Intent(Settings.ACTION_SETTINGS)) },
    ) { Chevron() }
}

/** Use for [way], or that [provider] serves it now; [enabled] once what is typed is saved. */
@Composable
internal fun InUse(voice: VoiceState, provider: SpeechProvider, way: SpeechWay, enabled: Boolean) {
    val gateway = LocalICodeGateway.current
    if (voice.chosenId(way) == provider.id) {
        StatusLabel(if (way == SpeechWay.Listening) "In use: it hears the microphone" else "In use: it reads the answers aloud", IcodeColors.Green)
    } else {
        SecondaryButton(
            if (way == SpeechWay.Listening) "Use for Listening" else "Use for Speaking",
            { gateway.dispatch(SpeechIntent.Use(provider.id, way)) },
            Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}

/** Hear It: [provider], as saved, says a sample; Stop while anything is read aloud. */
@Composable
internal fun HearIt(voice: VoiceState, provider: SpeechProvider, enabled: Boolean) {
    val gateway = LocalICodeGateway.current
    if (voice.speaking) {
        SecondaryButton("Stop", { gateway.dispatch(VoiceIntent.Silence) }, Modifier.fillMaxWidth())
    } else {
        SecondaryButton("Hear It", { gateway.dispatch(SpeechIntent.Hear(provider.id)) }, Modifier.fillMaxWidth(), enabled = enabled)
    }
}

/**
 * Test: hears the user once with [provider], as saved, and shows what it heard — or why not — here
 * alone: it is sent nowhere.
 */
@Composable
internal fun Test(voice: VoiceState, provider: SpeechProvider, enabled: Boolean) {
    val gateway = LocalICodeGateway.current
    val heard = voice.heard?.takeIf { it.from == VoiceFrom.Test }
    val listening = voice.listening(VoiceFrom.Test)
    val failure = voice.failures[provider.id]
    when {
        listening -> Text(heard?.text?.ifBlank { null } ?: "Listening…", style = IcodeType.Body)
        heard?.problem != null -> Text(heard.problem, style = IcodeType.Caption.copy(color = IcodeColors.Error))
        heard != null -> Text(heard.text, style = IcodeType.Body)
        failure != null -> Text("Last time: $failure", style = IcodeType.Caption.copy(color = IcodeColors.Error))
        else -> Text("Test: say something. What it hears shows here, and is sent nowhere.", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
    }
    if (listening) {
        Row(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            PrimaryButton("Done", { gateway.dispatch(VoiceIntent.FinishListening) }, Modifier.weight(1f))
            SecondaryButton("Stop", { gateway.dispatch(VoiceIntent.CancelListening) }, Modifier.weight(1f))
        }
    } else {
        SecondaryButton("Test", { gateway.dispatch(SpeechIntent.Test(provider.id)) }, Modifier.fillMaxWidth(), enabled = enabled && voice.microphone)
        if (!voice.microphone) Text("Allow the microphone on the Voice page first.", style = IcodeType.Caption.copy(color = IcodeColors.TextMuted))
    }
}

/** Settings' text-to-speech page: its action, which Android names nowhere public. */
internal const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
