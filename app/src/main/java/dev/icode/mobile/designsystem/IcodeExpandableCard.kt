package dev.icode.mobile.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight

/**
 * A card that opens and closes, drawn as a settings select is: its [title] and an arrow at its end that
 * turns over while it is open, its edge the accent then. Closed it shows [closed], a glance at what it
 * holds; open, [content]. The title, and what shows while closed, take the tap.
 */
@Composable
fun IcodeExpandableCard(
    title: String,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    closed: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "arrow")
    IcodeCard(modifier.fillMaxWidth(), borderColor = if (open) IcodeColors.Accent else IcodeColors.Outline) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(IcodeShapes.Small)
                .clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Open") { onOpen(!open) }
                .semantics { stateDescription = if (open) "Open" else "Closed" },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                Text(title, Modifier.weight(1f), style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = IcodeColors.TextSecondary,
                    modifier = Modifier.size(IcodeSizes.IconMd).rotate(turn),
                )
            }
            if (closed != null) AnimatedVisibility(!open) { Column(Modifier.padding(top = IcodeSpacing.ItemGap), content = closed) }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = IcodeSpacing.ItemGap), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap), content = content)
        }
    }
}
