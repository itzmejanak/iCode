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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle

/**
 * Card-wrapped text input used by every form and search surface.
 *
 * Technical values default to the mono style, matching the design.
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
    inputModifier: Modifier = Modifier,
    /** Masks the value; use for tokens and passwords, never for ordinary text. */
    secret: Boolean = false,
    /**
     * Typed exactly as it must be read — an address, a host, a branch or
     * account name: no autocorrection, suggestion or capital letter.
     */
    literal: Boolean = false,
    /** An action at the field's end, after [trailingText]: a button that fills it. */
    trailing: (@Composable () -> Unit)? = null,
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
                    modifier = Modifier.fillMaxWidth().then(inputModifier),
                    textStyle = textStyle,
                    singleLine = singleLine,
                    cursorBrush = SolidColor(IcodeColors.Accent),
                    visualTransformation = if (secret) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = when {
                        // A secret must not be learned, suggested or capitalised.
                        secret -> KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                        )
                        literal -> KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                        )
                        else -> KeyboardOptions.Default
                    },
                )
            }
            if (trailingText != null) {
                Text(trailingText, style = IcodeType.Caption)
            }
            trailing?.invoke()
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
    secret: Boolean = false,
    literal: Boolean = false,
) {
    IcodeSection(label, modifier) {
        IcodeTextField(
            value = value,
            onValueChange = onValueChange,
            secret = secret,
            literal = literal,
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        SectionLabel(label)
        // Plain text, no field around it (owner, 2026-09-30): a value the user reads never looks
        // like one to type into.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, modifier = Modifier.weight(1f), style = IcodeType.Mono)
            trailing?.invoke()
        }
    }
}
