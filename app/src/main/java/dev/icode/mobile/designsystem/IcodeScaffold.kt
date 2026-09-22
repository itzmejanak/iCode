package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Optional action invoked when a bare screen title is tapped.
 *
 * The demo app uses it to open the 40-frame screen catalog; production
 * navigation can repurpose it (breadcrumbs) or leave it unset.
 */
val LocalTitleAction = compositionLocalOf<() -> Unit> { {} }

/** Application shell: background, content clamp and system-bar insets. */
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
                .widthIn(max = IcodeSizes.ContentMaxWidth)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            content()
        }
    }
}

/** Workspace, page and dialog header with optional back and action slots. */
@Composable
fun IcodeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val titleAction = LocalTitleAction.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (subtitle == null) IcodeSizes.TopBar else IcodeSizes.TopBarWithSubtitle)
            .border(width = 0.5.dp, color = IcodeColors.Outline)
            .padding(horizontal = IcodeSpacing.Space3),
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
                    onClick = titleAction,
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

/** Full-page heading used by setup and permission screens without a top bar. */
@Composable
fun PageHeading(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val titleAction = LocalTitleAction.current
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = titleAction,
            ),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        Text(title, style = IcodeType.PageTitle)
        if (description != null) Text(description, style = IcodeType.Body, color = IcodeColors.TextSecondary)
    }
}
