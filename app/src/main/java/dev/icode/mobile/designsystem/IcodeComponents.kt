package dev.icode.mobile.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalOpenDemoCatalog = compositionLocalOf<() -> Unit> { {} }

private val SmallShape = RoundedCornerShape(4.dp)

@Composable
fun IcodeScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IcodeColors.Background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            content()
        }
    }
}

@Composable
fun IcodeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val openCatalog = LocalOpenDemoCatalog.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 56.dp else 64.dp)
            .border(width = 0.5.dp, color = IcodeColors.Outline)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = IcodeColors.TextPrimary)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = openCatalog,
                ),
        ) {
            Text(title, style = IcodeType.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = IcodeType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

@Composable
fun PageHeading(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val openCatalog = LocalOpenDemoCatalog.current
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = openCatalog,
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = IcodeType.PageTitle)
        if (description != null) Text(description, style = IcodeType.Body, color = IcodeColors.TextSecondary)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = IcodeType.Label,
        color = IcodeColors.TextSecondary,
    )
}

@Composable
fun IcodeCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    borderColor: Color = IcodeColors.Outline,
    padding: PaddingValues = PaddingValues(12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val clickableModifier = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Column(
        modifier = modifier
            .clip(SmallShape)
            .background(if (elevated) IcodeColors.SurfaceElevated else IcodeColors.Surface)
            .border(1.dp, borderColor, SmallShape)
            .then(clickableModifier)
            .padding(padding),
    ) {
        content()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = SmallShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = IcodeColors.Accent,
            contentColor = Color.Black,
            disabledContainerColor = IcodeColors.SurfaceElevated,
            disabledContentColor = IcodeColors.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text.uppercase(), style = IcodeType.Label.copy(color = LocalContentColor.current))
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = IcodeColors.TextPrimary,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = SmallShape,
        border = BorderStroke(1.dp, IcodeColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = IcodeColors.Surface,
            contentColor = color,
            disabledContentColor = IcodeColors.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, style = IcodeType.Label.copy(color = LocalContentColor.current))
    }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = SmallShape,
            colors = ButtonDefaults.buttonColors(containerColor = IcodeColors.Error, contentColor = Color.Black),
        ) { Text(text, style = IcodeType.Label.copy(color = Color.Black)) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = SmallShape,
            border = BorderStroke(1.dp, IcodeColors.Error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = IcodeColors.Error),
        ) { Text(text, style = IcodeType.Label.copy(color = IcodeColors.Error)) }
    }
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        IcodeCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, modifier = Modifier.weight(1f), style = IcodeType.Mono)
                trailing?.invoke()
            }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    IcodeCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(IcodeColors.SurfaceElevated, SmallShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(leadingIcon, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                if (subtitle != null) Text(subtitle, style = IcodeType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

@Composable
fun IcodeToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = IcodeColors.TextPrimary,
            checkedTrackColor = IcodeColors.Accent,
            uncheckedThumbColor = IcodeColors.TextPrimary,
            uncheckedTrackColor = IcodeColors.SurfaceTrack,
            uncheckedBorderColor = IcodeColors.Outline,
        ),
    )
}

@Composable
fun StatusDot(color: Color, size: Dp = 7.dp) {
    Box(Modifier.size(size).background(color, RoundedCornerShape(50)))
}

@Composable
fun StatusLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusDot(color)
        Text(text, style = IcodeType.Label.copy(color = color, fontSize = 11.sp))
    }
}

@Composable
fun IconText(icon: ImageVector, text: String, modifier: Modifier = Modifier, tint: Color = IcodeColors.TextPrimary) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = IcodeType.Body.copy(color = tint))
    }
}

@Composable
fun ProgressTrack(progress: Float, modifier: Modifier = Modifier, color: Color = IcodeColors.Accent) {
    Box(modifier.height(5.dp).background(IcodeColors.SurfaceTrack, RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).background(color, RoundedCornerShape(2.dp)))
    }
}

@Composable
fun MonoLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = IcodeColors.TextPrimary,
    style: TextStyle = IcodeType.Mono,
) {
    Text(text, modifier = modifier, style = style.copy(color = color), maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Divider() {
    HorizontalDivider(color = IcodeColors.Outline, thickness = 1.dp)
}

@Composable
fun KeyChip(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .background(if (active) IcodeColors.Background else IcodeColors.SurfaceElevated, SmallShape)
            .border(1.dp, if (active) IcodeColors.Accent else Color.Transparent, SmallShape)
            .padding(horizontal = if (compact) 4.dp else 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IcodeType.MonoSmall.copy(
                color = if (active) IcodeColors.Accent else IcodeColors.TextPrimary,
                fontSize = if (compact) 8.sp else IcodeType.MonoSmall.fontSize,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TwoColumnActions(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}

@Composable
fun VerticalGap(height: Dp = 12.dp) = Spacer(Modifier.height(height))

@Composable
fun HorizontalGap(width: Dp = 8.dp) = Spacer(Modifier.width(width))
