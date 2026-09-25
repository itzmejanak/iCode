package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
                // The software keyboard must not cover content. Taking the
                // union rather than chaining imePadding() after
                // navigationBarsPadding() avoids adding both insets at once.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            content()
        }
    }
}

/**
 * Workspace, page and dialog header with optional back and action slots.
 *
 * A hairline divider closes the header by default; pages can hide it with
 * [showDivider]. Pass an [AnnotatedString] title to style parts of it (e.g.
 * the product wordmark) independently from the rest of the text.
 */
@Composable
fun IcodeTopBar(
    title: AnnotatedString,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    dense: Boolean = false,
    showDivider: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(when {
                    dense && subtitle == null -> IcodeSizes.TouchTarget
                    dense -> IcodeSizes.TopBar
                    subtitle == null -> IcodeSizes.TopBar
                    else -> IcodeSizes.TopBarWithSubtitle
                })
                .padding(horizontal = IcodeSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = IcodeColors.TextPrimary)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(title, style = IcodeType.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = IcodeType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
        if (showDivider) {
            HorizontalDivider(color = IcodeColors.Outline, thickness = 0.5.dp)
        }
    }
}

/** String-title convenience overload of [IcodeTopBar]. */
@Composable
fun IcodeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    dense: Boolean = false,
    showDivider: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    IcodeTopBar(AnnotatedString(title), modifier, subtitle, onBack, dense, showDivider, actions)
}

/** Full-page heading used by setup and permission screens without a top bar. */
@Composable
fun PageHeading(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        Text(title, style = IcodeType.PageTitle)
        if (description != null) Text(description, style = IcodeType.Body, color = IcodeColors.TextSecondary)
    }
}
