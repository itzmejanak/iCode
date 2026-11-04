package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.AccentColorRules

/**
 * Chooses the accent: a preset, a point on the HSV wheel with its
 * brightness, or an exact hex value. The preview is drawn on the current
 * theme's background, with a warning when the colour will be hard to read.
 * [onApply] receives null for the theme's own accent.
 */
@Composable
internal fun AccentColorSheet(current: Int, themeAccent: Int, onApply: (Int?) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableIntStateOf(current) }
    var hexText by remember { mutableStateOf(AccentColorRules.hex(current)) }
    val controller = rememberColorPickerController()
    val background = IcodeColors.Background.toArgb()
    val contrast = AccentColorRules.contrastRatio(chosen, background)
    // Presets and typed hex are kept exactly; only a drag reads the wheel back.
    fun choose(argb: Int) {
        chosen = AccentColorRules.opaque(argb)
        hexText = AccentColorRules.hex(chosen)
        controller.selectByColor(Color(chosen), fromUser = false)
    }
    IcodeConfirmSheet(
        title = "Accent color",
        confirmLabel = "Apply",
        onConfirm = { onApply(chosen.takeIf { it != themeAccent }) },
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.heightIn(max = IcodeSizes.ContentMaxWidth).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            PresetChips(chosen, ::choose)
            HsvColorPicker(
                modifier = Modifier.fillMaxWidth().height(IcodeSizes.ColorWheel),
                controller = controller,
                initialColor = Color(current),
                onColorChanged = { envelope ->
                    if (envelope.fromUser) {
                        chosen = AccentColorRules.opaque(envelope.color.toArgb())
                        hexText = AccentColorRules.hex(chosen)
                    }
                },
            )
            BrightnessSlider(
                modifier = Modifier.fillMaxWidth().height(IcodeSizes.ColorBar),
                controller = controller,
                borderColor = IcodeColors.Outline,
                initialColor = Color(current),
            )
            IcodeTextField(
                value = hexText,
                onValueChange = { typed ->
                    hexText = typed
                    AccentColorRules.parseHex(typed)?.let(::choose)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "#RRGGBB",
                literal = true,
            )
            Preview(chosen, contrast)
            Text(
                "Use theme default",
                modifier = Modifier.clickable { choose(themeAccent) }.padding(vertical = IcodeSpacing.Space1),
                style = IcodeType.Label.copy(color = IcodeColors.Accent),
            )
        }
    }
}

@Composable
private fun PresetChips(chosen: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AccentColorRules.presets.forEach { preset ->
            val picked = preset.argb == chosen
            Box(
                modifier = Modifier
                    .size(IcodeSizes.ColorChip)
                    .background(Color(preset.argb), IcodeShapes.Pill)
                    .border(IcodeSpacing.Micro, if (picked) IcodeColors.TextPrimary else Color.Transparent, IcodeShapes.Pill)
                    .clickable { onPick(preset.argb) }
                    .semantics {
                        contentDescription = preset.name
                        selected = picked
                    },
            )
        }
    }
}

/** Accent text and an accent button on this theme's background, as the app will draw them. */
@Composable
private fun Preview(chosen: Int, contrast: Double) {
    val accent = Color(chosen)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
        Text(AccentColorRules.nameOf(chosen), style = IcodeType.Body.copy(color = accent))
        Text(
            "Button",
            modifier = Modifier.background(accent, IcodeShapes.Small).padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space1),
            style = IcodeType.Label.copy(color = Color(AccentColorRules.onColor(chosen))),
        )
    }
    Text(
        if (contrast < AccentColorRules.MIN_CONTRAST) {
            "Contrast %.1f:1 — hard to read on this theme".format(contrast)
        } else {
            "Contrast %.1f:1 on this theme".format(contrast)
        },
        style = IcodeType.Caption.copy(color = if (contrast < AccentColorRules.MIN_CONTRAST) IcodeColors.Amber else IcodeColors.TextSecondary),
    )
}
