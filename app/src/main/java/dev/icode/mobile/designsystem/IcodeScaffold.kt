package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow

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
 * A page's content under its top bar, scrolling: [IcodeSpacing.Gutter] from the screen's edges, its
 * sections ([IcodeSection]) [IcodeSpacing.SectionGap] apart (layout.md §4.5).
 */
@Composable
fun IcodePageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(IcodeSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(IcodeSpacing.SectionGap),
        content = content,
    )
}

/**
 * Workspace, page and dialog header with optional back and action slots.
 *
 * Its [EdgeLine] closes the header. Pass an [AnnotatedString] title to style parts of it (e.g.
 * the product wordmark) independently from the rest of the text. [onTitleClick]
 * makes the title a choice, marked ▾ (the file explorer's places), and
 * [titleMenu] is drawn anchored under it.
 */
@Composable
fun IcodeTopBar(
    title: AnnotatedString,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    dense: Boolean = false,
    onTitleClick: (() -> Unit)? = null,
    titleMenu: @Composable () -> Unit = {},
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
                .padding(horizontal = IcodeSpacing.IconButtonGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = IcodeColors.TextPrimary)
                }
            }
            // Without Back, the title starts at the page's gutter, as the content under it does.
            Box(Modifier.weight(1f).padding(start = if (onBack == null) IcodeSpacing.IconButtonInset else IcodeSpacing.None)) {
                Column(
                    modifier = if (onTitleClick == null) Modifier else Modifier.clip(IcodeShapes.Small).clickable(role = Role.Button, onClick = onTitleClick),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = IcodeType.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (onTitleClick != null) {
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Choose", tint = IcodeColors.TextSecondary)
                        }
                    }
                    if (subtitle != null) {
                        Text(subtitle, style = IcodeType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                titleMenu()
            }
            actions()
        }
        EdgeLine()
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
    onTitleClick: (() -> Unit)? = null,
    titleMenu: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    IcodeTopBar(AnnotatedString(title), modifier, subtitle, onBack, dense, onTitleClick, titleMenu, actions)
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
