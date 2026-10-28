package dev.icode.mobile.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier

/**
 * The actions on one thing, or on a selection, as a sheet (docs/explorer-gaps-plan.md §3.1), on the
 * base every sheet is drawn on. Its head: [leading], the item's icon or thumbnail, then [title] and
 * [detail] ("12 items · main"). Then [actions] as rows: each one's icon, label and detail where one
 * helps, greyed when not possible, ✓ when chosen. A line comes before each group
 * ([IcodeMenuAction.beginsGroup]) and before the destructive one, last and in red. The dropdowns take
 * the same list, so a list of actions is written once. A tap closes the sheet and acts, unless the
 * action opens a page of the sheet ([IcodeMenuAction.opensPage]), which takes its place.
 */
@Composable
fun IcodeActionSheet(
    title: String,
    actions: List<IcodeMenuAction>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    IcodeBottomSheet(title = title, onClose = onClose, modifier = modifier, subtitle = detail, leading = leading) {
        // Rows touching, as a menu's: the lines between groups are its only breaks.
        Column {
            actions.forEachIndexed { index, action ->
                if (index > 0 && (action.beginsGroup || action.destructive)) Divider()
                val color = when {
                    !action.enabled -> IcodeColors.TextMuted
                    action.destructive -> IcodeColors.Error
                    else -> IcodeColors.TextPrimary
                }
                IcodeListRow(
                    title = action.label,
                    subtitle = action.detail,
                    titleColor = color,
                    onClick = if (action.enabled) ({ if (!action.opensPage) onClose(); action.onClick() }) else null,
                    leading = { Icon(action.icon, contentDescription = null, tint = color, modifier = Modifier.size(IcodeSizes.IconMd)) },
                    trailing = if (action.checked) {
                        { Icon(Icons.Outlined.Check, contentDescription = "Chosen", tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * The pages a sheet opens within itself (§3.1): [current] is the one shown, null for the sheet's own
 * first page. [open] goes to another; [back] steps back one, and Back does too, before it closes the
 * sheet.
 */
@Stable
class SheetPages<P : Any> internal constructor(private val stack: SnapshotStateList<P>) {
    val current: P? get() = stack.lastOrNull()

    fun open(page: P) {
        stack += page
    }

    /** ← in the sheet's head: null on its first page, which has nothing before it. */
    val back: (() -> Unit)? get() = if (stack.isEmpty()) null else ({ stack.removeAt(stack.lastIndex) })
}

/**
 * A sheet's pages, for the thing [key] names: begun at [opened], which Back steps through. Called
 * inside the sheet, after [IcodeSheetOverlay]'s Back, so a page goes back before the sheet closes.
 * Kept as the screen turns: [P] is a type a saved state holds — a String, an enum.
 */
@Composable
fun <P : Any> rememberSheetPages(key: Any?, opened: List<P> = emptyList()): SheetPages<P> {
    val stack = rememberSaveable(key, saver = listSaver<SnapshotStateList<P>, P>(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        opened.toMutableStateList()
    }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    return remember(stack) { SheetPages(stack) }
}
