package dev.icode.mobile.ui.screens.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeActionSheet
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeTabStrip
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.ui.SheetIcon

/** The open documents' tabs: a dirty file's dot before its name; a long press opens the tab's sheet ([onPress]), or drags it. */
@Composable
internal fun DocumentTabStrip(
    documents: List<OpenDocument>,
    selectedLocation: String?,
    onSelect: (String) -> Unit,
    onReorder: (String, String) -> Unit,
    onClose: (String) -> Unit,
    onPress: (String) -> Unit,
    closing: Set<String> = emptySet(),
) {
    IcodeTabStrip(
        tabs = documents,
        keyOf = { it.location },
        title = { it.name },
        description = { "${it.name}${if (it.dirty) ", unsaved" else ""}, long press for tab actions, or drag to reorder" },
        selected = selectedLocation,
        onSelect = onSelect,
        onReorder = onReorder,
        onClose = onClose,
        onPress = onPress,
        mark = { document, color -> if (document.dirty) StatusDot(color) },
        closing = { it.location in closing },
    )
}

/**
 * A tab's sheet (docs/explorer-gaps-plan.md §3.1), titled with its file and where the runtime has it
 * ([path]): New file, Close other tabs — all but this one — Close saved tabs, and Close it, last.
 */
@Composable
internal fun TabSheet(
    document: OpenDocument,
    path: String,
    documents: List<OpenDocument>,
    onClose: (String) -> Unit,
    onCloseOthers: (keep: String) -> Unit,
    onCloseSaved: () -> Unit,
    onNewFile: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, tint) = itemIcon(document.name, folder = false)
    IcodeActionSheet(
        title = document.name,
        actions = listOf(
            IcodeMenuAction("New file", Icons.Outlined.Add, onNewFile),
            IcodeMenuAction("Close other tabs", Icons.Outlined.Close, { onCloseOthers(document.location) }, enabled = documents.size > 1, beginsGroup = true),
            IcodeMenuAction("Close saved tabs", Icons.Outlined.Close, onCloseSaved, enabled = documents.any { !it.dirty }),
            IcodeMenuAction("Close ${document.name}", Icons.Outlined.Close, { onClose(document.location) }, destructive = true),
        ),
        onClose = onDismiss,
        modifier = modifier,
        detail = if (document.dirty) "$path · unsaved" else path,
        leading = { SheetIcon(icon, tint) },
    )
}
