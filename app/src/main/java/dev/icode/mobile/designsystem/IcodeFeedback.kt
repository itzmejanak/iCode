package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Bordered message surface: warnings, errors, success and informational states.
 *
 * The tone owns the border and title color, so severity mapping lives in one
 * place instead of every screen.
 */
@Composable
fun NoticeCard(
    tone: IcodeTone,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    IcodeCard(modifier = modifier, borderColor = tone.tint()) {
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                ) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = tone.tint(),
                            modifier = Modifier.size(IcodeSizes.IconMd),
                        )
                    }
                    Text(title, style = IcodeType.Title.copy(color = tone.tint()))
                }
            }
            content()
        }
    }
}

/** Centered empty state: optional mark or icon, title and description. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    mark: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
    ) {
        if (mark != null) {
            mark()
        } else if (icon != null) {
            Box(
                modifier = Modifier.size(64.dp).background(IcodeColors.Surface, IcodeShapes.Pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(30.dp))
            }
        }
        Text(title, style = IcodeType.Title)
        if (description != null) {
            Text(description, style = IcodeType.Body, color = IcodeColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** Modal backdrop with a top bar and a centered elevated card. */
@Composable
fun DialogScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(IcodeColors.Scrim)) {
            IcodeTopBar(title, subtitle = subtitle, onBack = onBack)
        }
        IcodeCard(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(IcodeSpacing.Space6),
            elevated = true,
            padding = PaddingValues(IcodeSpacing.Space5),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3), content = content)
        }
    }
}

/** Bottom sheet surface: handle, title row with close action and content. */
@Composable
fun IcodeBottomSheet(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(IcodeColors.Surface, IcodeShapes.Sheet)
            .padding(IcodeSpacing.Space4),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .background(IcodeColors.Outline, IcodeShapes.Track),
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = IcodeSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), style = IcodeType.Title)
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = IcodeColors.TextSecondary)
            }
        }
        content()
    }
}
