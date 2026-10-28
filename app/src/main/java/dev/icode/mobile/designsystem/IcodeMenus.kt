package dev.icode.mobile.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One action, as a dropdown and an action sheet ([IcodeActionSheet]) both draw it: app typography,
 * color, surface, and destructive grouping. [detail] is a second line under the label (a place's
 * path); [checked] marks the one chosen of a set (a sort). [beginsGroup] puts a line before it.
 * [opensPage]: in a sheet, it opens a page of the sheet (Rename…, Properties) instead of closing it.
 */
data class IcodeMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val detail: String? = null,
    val checked: Boolean = false,
    val beginsGroup: Boolean = false,
    val opensPage: Boolean = false,
)

@Composable
fun IcodeOverflowMenu(
    contentDescription: String,
    actions: List<IcodeMenuAction>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(IcodeSizes.TouchTarget)) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = contentDescription,
                tint = IcodeColors.TextSecondary,
                modifier = Modifier.size(IcodeSizes.IconMd),
            )
        }
        IcodeActionMenu(expanded = expanded, onDismiss = { expanded = false }, actions = actions)
    }
}

/**
 * The overflow menu's list, without the button that opens it.
 *
 * Lets a surface that already has a gesture for it — a long press on a tab —
 * show the same actions, styled identically, anchored to whatever it is placed
 * inside.
 */
@Composable
fun IcodeActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<IcodeMenuAction>,
) {
    IcodeDropdownMenu(expanded, onDismiss, Modifier.width(IcodeSizes.MenuWidth)) {
        actions.forEachIndexed { index, action ->
            if (action.destructive || (action.beginsGroup && index > 0)) {
                Divider()
            }
            val color = when {
                !action.enabled -> IcodeColors.TextMuted
                action.destructive -> IcodeColors.Error
                else -> IcodeColors.TextPrimary
            }
            DropdownMenuItem(
                text = {
                    Column {
                        Text(action.label, style = IcodeType.Body.copy(color = color))
                        action.detail?.let { MonoLine(it, color = IcodeColors.TextSecondary, style = IcodeType.MonoSmall, maxLines = 1) }
                    }
                },
                leadingIcon = {
                    Icon(action.icon, contentDescription = null, tint = color, modifier = Modifier.size(IcodeSizes.IconMd))
                },
                trailingIcon = if (action.checked) {
                    { Icon(Icons.Outlined.Check, contentDescription = "Chosen", tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd)) }
                } else {
                    null
                },
                enabled = action.enabled,
                contentPadding = PaddingValues(horizontal = IcodeSpacing.Space3),
                onClick = {
                    onDismiss()
                    action.onClick()
                },
            )
        }
    }
}

/** Every popup menu's surface: raised over the screen, its shadow kept, its [edgeLine] drawn. */
@Composable
internal fun IcodeDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = IcodeShapes.Small,
        containerColor = IcodeColors.SurfaceElevated,
        tonalElevation = 0.dp,
        border = edgeLine?.let { BorderStroke(1.dp, it) },
        content = content,
    )
}
