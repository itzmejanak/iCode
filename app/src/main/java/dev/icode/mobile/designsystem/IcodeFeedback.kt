package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * A question with an answer to confirm, as a sheet (owner's request, 2026-11-04: no dialogs): its
 * [title], [content], and Cancel beside the answer — red when it is [destructive]. Not
 * [dismissEnabled], it must be answered: no ×, no swipe, no tap outside.
 */
@Composable
fun IcodeConfirmSheet(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    /**
     * What the confirm started runs (docs/loading-plan.md §4.2): its button busy, the sheet held open
     * over it and not dismissed — Cancel would not stop it.
     */
    confirmBusy: Boolean = false,
    dismissEnabled: Boolean = true,
    dismissLabel: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit,
) {
    val dismissable = dismissEnabled && !confirmBusy
    val dismiss = { if (dismissable) onDismiss() }
    IcodeModalSheet(onClose = dismiss) {
        IcodeBottomSheet(
            title = title,
            onClose = dismiss,
            modifier = Modifier.align(Alignment.BottomCenter),
            closable = dismissable,
            footer = {
                TwoColumnActions(
                    left = { SecondaryButton(dismissLabel, onDismiss, Modifier.fillMaxWidth(), enabled = dismissable) },
                    right = {
                        if (destructive) DangerButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), filled = true, enabled = confirmEnabled, busy = confirmBusy)
                        else PrimaryButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), enabled = confirmEnabled, busy = confirmBusy)
                    },
                )
            },
            content = content,
        )
    }
}

/**
 * A confirmation for an action that cannot be undone: [message], the thing it acts on as [detail],
 * and the action in red. Destructive actions get a visible control and this sheet rather than a
 * hidden gesture: a long press gives no warning and no way back.
 */
@Composable
fun IcodeConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    detail: String? = null,
    confirmBusy: Boolean = false,
) {
    IcodeConfirmSheet(title, confirmLabel, onConfirm, onDismiss, destructive = true, confirmBusy = confirmBusy) {
        Text(message, style = IcodeType.Body, color = IcodeColors.TextSecondary)
        if (detail != null) MonoLine(detail, color = IcodeColors.Accent, style = IcodeType.MonoSmall)
    }
}

/** Three ways out of an unsaved editor tab (or a set of tabs), as a sheet: Discard, Save, or nothing (Cancel, ×). */
@Composable
fun IcodeSaveDiscardSheet(
    title: String,
    description: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    IcodeModalSheet(onClose = onDismiss) {
        IcodeBottomSheet(
            title = title,
            onClose = onDismiss,
            modifier = Modifier.align(Alignment.BottomCenter),
            footer = {
                TwoColumnActions(
                    left = { DangerButton("Discard", onDiscard, Modifier.fillMaxWidth()) },
                    right = { PrimaryButton("Save", onSave, Modifier.fillMaxWidth()) },
                )
                SecondaryButton("Cancel", onDismiss, Modifier.fillMaxWidth())
            },
        ) {
            Text(description, style = IcodeType.Body, color = IcodeColors.TextSecondary)
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
 * [subtitle] says the state of what it shows; [onBack] leads back to the sheet's page before this
 * one, by ← before the title (docs/assistant-sheets-plan.md §2.5). [leading] stands before the title:
 * the icon or thumbnail of the item the sheet is about.
 *
 * Swiped down, it closes as × does (owner's request, 2026-11-04): the sheet follows the finger —
 * from its title or footer, or from its content once that is at its top — and let go past a quarter
 * of its height, or flung down, it slides away; short of that it springs back. Not [closable] (a
 * question that must be answered), it has no × and does not move.
 *
 * [ground] is the sheet's own surface unless it shows something with a ground of its own — a terminal
 * typed into — and [action] stands at its title's end in place of × while it does; [edgeToEdge], its
 * content then reaches the sheet's sides and foot, as the Terminal panel's terminal and keys do.
 * [actions] stand before × or [action]: the sheet's own tools, as icons.
 */
@Composable
fun IcodeBottomSheet(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scroll: Boolean = true,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    closable: Boolean = true,
    /** Its title's lines at most: two for a name, all of it for a sheet whose title is what it says (a notice). */
    titleLines: Int = 2,
    ground: Color = IcodeColors.Surface,
    action: (@Composable () -> Unit)? = null,
    edgeToEdge: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val swipe = rememberSheetSwipe(onClose)
    val inset = IcodeSpacing.Space4
    val contentInset = if (edgeToEdge) IcodeSpacing.None else inset
    // Its cards stand a step above it, as a page's above the page.
    CompositionLocalProvider(LocalIcodeCardRaised provides true) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .then(if (closable) swipe.modifier else Modifier)
                .background(ground, IcodeShapes.Sheet)
                // A tap on the sheet stays on it, never reaching the screen it is over.
                .pointerInput(Unit) {}
                .padding(top = inset, bottom = contentInset),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(IcodeColors.Outline, IcodeShapes.Track),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = inset, vertical = IcodeSpacing.Space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onBack?.let { back ->
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = IcodeColors.TextSecondary)
                    }
                }
                leading?.let { Box(Modifier.padding(end = IcodeSpacing.Space3)) { it() } }
                Column(Modifier.weight(1f)) {
                    Text(title, style = IcodeType.Title, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, style = IcodeType.Caption) }
                }
                actions()
                if (action != null) {
                    action()
                } else if (closable) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = IcodeColors.TextSecondary)
                    }
                }
            }
            Column(
                Modifier.weight(1f, fill = false).padding(horizontal = contentInset)
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                content = content,
            )
            if (footer != null) {
                Column(
                    Modifier.padding(horizontal = inset).padding(top = IcodeSpacing.Space3),
                    verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                    content = footer,
                )
            }
        }
    }
}
