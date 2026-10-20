package dev.icode.mobile.designsystem

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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

/** Shared elevated surface for compact modals and full-screen dialog routes. */
@Composable
fun IcodeDialogCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    IcodeCard(
        modifier = modifier,
        elevated = true,
        padding = PaddingValues(IcodeSpacing.Space4),
        content = content,
    )
}

/** Compact modal confirmation using the same surface and actions as screen dialogs. */
@Composable
fun IcodeConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    dismissLabel: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = { if (dismissEnabled) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(IcodeSpacing.Space4), contentAlignment = Alignment.Center) {
            IcodeDialogCard(
                modifier = Modifier.widthIn(max = IcodeSizes.DialogMaxWidth).fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                    Text(
                        title,
                        style = IcodeType.Title.copy(color = if (destructive) IcodeColors.Error else IcodeColors.TextPrimary),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2), content = content)
                    TwoColumnActions(
                        left = { SecondaryButton(dismissLabel, onDismiss, Modifier.fillMaxWidth(), enabled = dismissEnabled) },
                        right = {
                            if (destructive) DangerButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), filled = true)
                            else PrimaryButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), enabled = confirmEnabled)
                        },
                        modifier = Modifier.padding(top = IcodeSpacing.Space1),
                    )
                }
            }
        }
    }
}

/** Three-way confirmation for an unsaved editor tab (or a set of tabs). */
@Composable
fun IcodeSaveDiscardDialog(
    title: String,
    description: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(IcodeSpacing.Space4), contentAlignment = Alignment.Center) {
            IcodeDialogCard(Modifier.widthIn(max = IcodeSizes.DialogMaxWidth).fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                    Text(title, style = IcodeType.Title)
                    Text(description, style = IcodeType.Body, color = IcodeColors.TextSecondary)
                    TwoColumnActions(
                        left = { DangerButton("Discard", onDiscard, Modifier.fillMaxWidth()) },
                        right = { PrimaryButton("Save", onSave, Modifier.fillMaxWidth()) },
                    )
                    SecondaryButton("Cancel", onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Bottom sheet surface: handle and title row, then [content], then [footer].
 *
 * Only [content] scrolls, in whatever height is left, so a long list cannot
 * push the title off the top or the footer off the bottom. The footer — say a
 * field and the button that submits it — stays in view above the keyboard.
 * Content that fits that height instead — a picture — does not [scroll].
 */
@Composable
fun IcodeBottomSheet(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Its cards stand a step above it, as a page's above the page.
    CompositionLocalProvider(LocalIcodeCardRaised provides true) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(IcodeColors.Surface, IcodeShapes.Sheet)
                // A tap on the sheet stays on it, never reaching the screen it is over.
                .pointerInput(Unit) {}
                .padding(IcodeSpacing.Space4),
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
            Column(
                Modifier.weight(1f, fill = false).then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                content = content,
            )
            if (footer != null) {
                Column(
                    Modifier.padding(top = IcodeSpacing.Space3),
                    verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                    content = footer,
                )
            }
        }
    }
}

/**
 * Modal confirmation for an action that cannot be undone.
 *
 * Destructive actions get a visible control and this dialog rather than a
 * hidden gesture: a long press gives no warning and no way back.
 */
@Composable
fun IcodeConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    detail: String? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        IcodeDialogCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
                Text(title, style = IcodeType.Title.copy(color = IcodeColors.Error))
                Text(message, style = IcodeType.Body, color = IcodeColors.TextSecondary)
                if (detail != null) {
                    MonoLine(detail, color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                }
                TwoColumnActions(
                    left = { SecondaryButton("Cancel", onDismiss, Modifier.fillMaxWidth()) },
                    right = {
                        DangerButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), filled = true)
                    },
                )
            }
        }
    }
}
