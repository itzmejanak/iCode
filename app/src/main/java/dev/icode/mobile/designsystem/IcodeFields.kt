package dev.icode.mobile.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Card-wrapped text input used by every form and search surface.
 *
 * Technical values default to the mono style, matching the Figma frames.
 */
@Composable
fun IcodeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingText: String? = null,
    textStyle: TextStyle = IcodeType.Mono,
    singleLine: Boolean = true,
    borderColor: Color = IcodeColors.Outline,
) {
    IcodeCard(
        modifier = modifier,
        borderColor = borderColor,
        padding = PaddingValues(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = IcodeColors.TextSecondary,
                    modifier = Modifier.size(IcodeSizes.IconMd),
                )
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = textStyle.copy(color = IcodeColors.TextMuted))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = textStyle,
                    singleLine = singleLine,
                    cursorBrush = SolidColor(IcodeColors.Accent),
                )
            }
            if (trailingText != null) {
                Text(trailingText, style = IcodeType.Caption)
            }
        }
    }
}

/** [IcodeTextField] with a section label above it, for labeled forms. */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        SectionLabel(label)
        IcodeTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = singleLine,
        )
    }
}

/** Read-only technical value presented as a labeled field. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        SectionLabel(label)
        IcodeCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = IcodeSpacing.Space3, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, modifier = Modifier.weight(1f), style = IcodeType.Mono)
                trailing?.invoke()
            }
        }
    }
}
