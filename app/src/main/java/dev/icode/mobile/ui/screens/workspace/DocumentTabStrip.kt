package dev.icode.mobile.ui.screens.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeTabStrip
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.domain.OpenDocument

/** The open documents' tabs: a dirty file's dot before its name; a long press opens the tab's actions, or drags it. */
@Composable
internal fun DocumentTabStrip(
    documents: List<OpenDocument>,
    selectedLocation: String?,
    onSelect: (String) -> Unit,
    onReorder: (String, String) -> Unit,
    onClose: (String) -> Unit,
    onCloseOthers: () -> Unit,
    onCloseSaved: () -> Unit,
    onNewFile: () -> Unit,
) {
    var menuLocation by remember { mutableStateOf<String?>(null) }
    IcodeTabStrip(
        tabs = documents,
        keyOf = { it.location },
        title = { it.name },
        description = { "${it.name}${if (it.dirty) ", unsaved" else ""}, long press for tab actions, or drag to reorder" },
        selected = selectedLocation,
        onSelect = onSelect,
        onReorder = onReorder,
        onClose = onClose,
        onPress = { menuLocation = it },
        mark = { document, color -> if (document.dirty) StatusDot(color) },
        menu = { document ->
            IcodeActionMenu(
                expanded = menuLocation == document.location,
                onDismiss = { menuLocation = null },
                actions = tabActions(
                    document = document,
                    documents = documents,
                    selectedLocation = selectedLocation,
                    onClose = onClose,
                    onCloseOthers = onCloseOthers,
                    onCloseSaved = onCloseSaved,
                    onNewFile = onNewFile,
                ),
            )
        },
    )
}

/** The actions the tab overflow used to carry, now scoped to the pressed tab. */
private fun tabActions(
    document: OpenDocument,
    documents: List<OpenDocument>,
    selectedLocation: String?,
    onClose: (String) -> Unit,
    onCloseOthers: () -> Unit,
    onCloseSaved: () -> Unit,
    onNewFile: () -> Unit,
): List<IcodeMenuAction> = listOf(
    IcodeMenuAction(
        label = "New file",
        icon = Icons.Outlined.Add,
        onClick = onNewFile,
    ),
    IcodeMenuAction(
        label = "Close other tabs",
        icon = Icons.Outlined.Close,
        enabled = documents.size > 1 && selectedLocation != null,
        onClick = onCloseOthers,
    ),
    IcodeMenuAction(
        label = "Close saved tabs",
        icon = Icons.Outlined.Close,
        enabled = documents.any { !it.dirty },
        onClick = onCloseSaved,
    ),
    IcodeMenuAction(
        label = "Close ${document.name}",
        icon = Icons.Outlined.Close,
        destructive = true,
        onClick = { onClose(document.location) },
    ),
)
