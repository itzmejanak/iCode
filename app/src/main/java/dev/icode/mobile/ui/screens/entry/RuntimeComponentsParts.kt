package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeCheckbox
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTone
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IndeterminateProgressTrack
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.NoticeCard
import dev.icode.mobile.designsystem.ProgressTrack
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.domain.ComponentWork
import dev.icode.mobile.domain.RuntimeComponent

/** One component: its tick (the whole row takes the tap), what it is, what uses it, and what it costs. */
@Composable
internal fun ComponentRow(component: RuntimeComponent, ticked: Boolean, enabled: Boolean, cost: String, onToggle: () -> Unit) {
    IcodeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IcodeShapes.Small)
            .toggleable(value = ticked, enabled = enabled, role = Role.Checkbox) { onToggle() },
        borderColor = if (ticked && cost != INSTALLED) IcodeColors.Accent else IcodeColors.Outline,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            IcodeCheckbox(checked = ticked, onCheckedChange = null, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(component.name, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                Text(component.summary, style = IcodeType.Caption)
                component.usedBy?.let { MonoLine("Used by $it", color = IcodeColors.TextMuted, style = IcodeType.MonoSmall) }
            }
            MonoLine(
                cost,
                color = when (cost) {
                    INSTALLED -> IcodeColors.Green
                    REMOVES -> IcodeColors.Amber
                    else -> IcodeColors.TextSecondary
                },
                style = IcodeType.MonoSmall,
            )
        }
    }
}

/**
 * apt's run, as Android Studio's Downloading Components: the bar (it sweeps while apt reads and
 * downloads, and fills by the packages dpkg sets up), the step and package in hand, and Details —
 * apt's own last lines. A failure says apt's words.
 */
@Composable
internal fun ComponentProgress(work: ComponentWork) {
    var details by rememberSaveable { mutableStateOf(work.failure != null) }
    val failure = work.failure
    if (failure != null) {
        NoticeCard(tone = IcodeTone.Error, title = "Could not finish", modifier = Modifier.fillMaxWidth()) {
            Text(failure, style = IcodeType.Caption)
        }
    } else {
        IcodeCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                Text(work.title, style = IcodeType.Title)
                work.progress.fraction?.let { ProgressTrack(it, Modifier.fillMaxWidth()) }
                    ?: IndeterminateProgressTrack(Modifier.fillMaxWidth())
                MonoLine(work.progress.step, color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                work.progress.item?.let { MonoLine(it, style = IcodeType.MonoSmall) }
            }
        }
    }
    SecondaryButton(if (details) "Hide details" else "Show details", { details = !details }, Modifier.fillMaxWidth())
    if (details) {
        CodeSurface(Modifier.fillMaxWidth()) {
            work.log.ifEmpty { listOf("apt has printed nothing yet.") }.forEach { MonoLine(it, style = IcodeType.MonoSmall) }
        }
    }
}

internal const val INSTALLED = "Installed"
internal const val REMOVES = "Removes"
