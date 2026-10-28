package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.icode.mobile.designsystem.IcodeActionBar
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.domain.StorageMeasurement
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.FileHistoryState
import dev.icode.mobile.domain.files.FileSelection
import dev.icode.mobile.domain.files.FilesBrowserState
import dev.icode.mobile.domain.files.FilesSort
import dev.icode.mobile.domain.files.FolderPick
import dev.icode.mobile.domain.files.FolderPicks
import dev.icode.mobile.domain.files.StoragePlace

/** In selection, the top bar's own (docs/explorer-gaps-plan.md §3.2): Select all and Invert, on what is [shown]. */
@Composable
internal fun SelectionActions(shown: List<String>, ticked: Set<String>, onTicked: (Set<String>) -> Unit) {
    IconButton(onClick = { onTicked(FileSelection.all(shown)) }) {
        Icon(Icons.Outlined.SelectAll, contentDescription = "Select all", tint = IcodeColors.TextPrimary)
    }
    IconButton(onClick = { onTicked(FileSelection.inverted(ticked, shown)) }) {
        Icon(Icons.Outlined.FlipToBack, contentDescription = "Invert selection", tint = IcodeColors.TextPrimary)
    }
}

/** A selection's foot bar (§3.2): how many, what they are and weigh; Cut, Copy, Delete, and ⋯ for the selection's sheet. */
@Composable
internal fun SelectionBar(
    items: List<BrowsedItem>,
    sizes: Map<String, StorageMeasurement>,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
) {
    val any = items.isNotEmpty()
    IcodeActionBar(
        title = "${items.size} selected",
        caption = if (any) FileSelection.summary(items, sizes) else "Tap items to tick them",
        iconLast = true,
    ) {
        IconButton(onClick = onCut, enabled = any) { Icon(Icons.Outlined.ContentCut, contentDescription = "Cut", tint = tint(any)) }
        IconButton(onClick = onCopy, enabled = any) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = tint(any)) }
        IconButton(onClick = onDelete, enabled = any) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = if (any) IcodeColors.Error else IcodeColors.TextMuted) }
        IconButton(onClick = onMore, enabled = any) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More actions", tint = tint(any)) }
    }
}

@Composable
private fun tint(enabled: Boolean) = if (enabled) IcodeColors.TextPrimary else IcodeColors.TextMuted

/**
 * The folder picker's foot bar (§3.5): where it is, what goes there, and the button that puts it
 * there — or why it cannot. At Private's top ([top]) what goes there becomes a workspace.
 */
@Composable
internal fun PickBar(pick: FolderPick, browser: FilesBrowserState?, top: Boolean, onPick: (String) -> Unit) {
    val bar = FolderPicks.bar(pick, browser?.path.orEmpty(), browser?.let { it.runtimePathOf(it.path) }.orEmpty(), top)
    IcodeActionBar(
        title = bar.title,
        caption = bar.refusal ?: bar.caption,
        captionColor = if (bar.refusal != null) IcodeColors.Amber else IcodeColors.TextSecondary,
        titleStyle = IcodeType.Mono,
    ) {
        PrimaryButton(bar.action, { browser?.let { onPick(it.path) } }, enabled = browser != null && browser.access && bar.refusal == null)
    }
}

/**
 * The explorer's ⋮ (§3.14): Select, Undo and Redo — each with what it would do — and Storage
 * settings.
 */
@Composable
internal fun ExplorerMenu(history: FileHistoryState, onSelect: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onStorage: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "More options", tint = IcodeColors.TextPrimary)
        }
        IcodeActionMenu(
            expanded = open,
            onDismiss = { open = false },
            actions = listOf(
                IcodeMenuAction("Select", Icons.Outlined.Checklist, onSelect),
                IcodeMenuAction("Undo", Icons.AutoMirrored.Outlined.Undo, onUndo, enabled = history.undo != null, detail = history.undo, beginsGroup = true),
                IcodeMenuAction("Redo", Icons.AutoMirrored.Outlined.Redo, onRedo, enabled = history.redo != null, detail = history.redo),
                IcodeMenuAction("Storage settings", Icons.Outlined.Storage, onStorage, beginsGroup = true),
            ),
        )
    }
}

/** ▾'s menu: the two places, each with its path, the one shown checked. */
internal fun places(browser: FilesBrowserState?, onChoose: (StoragePlace) -> Unit): List<IcodeMenuAction> =
    StoragePlace.entries.map { place ->
        IcodeMenuAction(
            label = place.label,
            icon = if (place == StoragePlace.Private) Icons.Outlined.FolderCopy else Icons.Outlined.PhoneAndroid,
            onClick = { onChoose(place) },
            detail = if (place == StoragePlace.Private) WorkspaceRuntimePath.ROOT else browser?.sharedRoot?.takeIf { it.isNotEmpty() },
            checked = browser?.place == place,
        )
    }

/** ⇅: Name, Date or Size, the one in use checked; folders come first whichever it is. */
@Composable
internal fun SortButton(sort: FilesSort, onSort: (FilesSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort by ${sort.name}", tint = IcodeColors.TextPrimary)
        }
        IcodeActionMenu(
            expanded = open,
            onDismiss = { open = false },
            actions = FilesSort.entries.map { option ->
                val icon = when (option) {
                    FilesSort.Name -> Icons.Outlined.SortByAlpha
                    FilesSort.Date -> Icons.Outlined.Schedule
                    FilesSort.Size -> Icons.Outlined.Straighten
                    FilesSort.Type -> Icons.Outlined.Category
                }
                IcodeMenuAction(option.name, icon, { onSort(option) }, checked = option == sort)
            },
        )
    }
}

/** 🔍's field, under the bar, ready to type in. */
@Composable
internal fun SearchField(query: String, placeholder: String, onQuery: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    IcodeTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = IcodeSpacing.Gutter, vertical = IcodeSpacing.Space2),
        placeholder = placeholder,
        leadingIcon = Icons.Outlined.Search,
        inputModifier = Modifier.focusRequester(focus),
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
