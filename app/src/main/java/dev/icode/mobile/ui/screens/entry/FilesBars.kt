package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.designsystem.IcodeActionBar
import dev.icode.mobile.designsystem.IcodeActionMenu
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.IcodeOption
import dev.icode.mobile.designsystem.IcodeOptionGroup
import dev.icode.mobile.designsystem.IcodeOptionPanelButton
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.StorageMeasurement
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.domain.countOf
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.ExplorerState
import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.files.FileCategory
import dev.icode.mobile.domain.files.FileHistoryState
import dev.icode.mobile.domain.files.FileSelection
import dev.icode.mobile.domain.files.FilesBrowserState
import dev.icode.mobile.domain.files.FilesLook
import dev.icode.mobile.domain.files.FilesSort
import dev.icode.mobile.domain.files.FilesView
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

/** A selection's foot bar (§3.2): how many, what they are and weigh, and what can be done to them ([actions]). */
@Composable
internal fun SelectionBar(items: List<BrowsedItem>, sizes: Map<String, StorageMeasurement>, actions: List<IcodeMenuAction>) {
    val any = items.isNotEmpty()
    IcodeActionBar(
        title = "${items.size} selected",
        caption = if (any) FileSelection.summary(items, sizes) else "Tap items to tick them",
        iconLast = true,
    ) {
        actions.forEach { action ->
            IconButton(onClick = action.onClick, enabled = any) {
                Icon(action.icon, contentDescription = action.label, tint = if (any && action.destructive) IcodeColors.Error else tint(any))
            }
        }
    }
}

/**
 * What a selection of files does from its bar (§3.2): Cut, Copy and Delete — each ending it
 * ([onActed]) — and ⋯ for the selection's sheet.
 */
internal fun selectionActions(locations: List<String>, dispatch: (ICodeIntent) -> Unit, onActed: () -> Unit, onMore: () -> Unit) = listOf(
    IcodeMenuAction("Cut", Icons.Outlined.ContentCut, { dispatch(FilesIntent.Cut(locations)); onActed() }),
    IcodeMenuAction("Copy", Icons.Outlined.ContentCopy, { dispatch(FilesIntent.Copy(locations)); onActed() }),
    IcodeMenuAction("Delete", Icons.Outlined.DeleteOutline, { dispatch(FilesIntent.Trash(locations)); onActed() }, destructive = true),
    IcodeMenuAction("More actions", Icons.Outlined.MoreHoriz, onMore),
)

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

/**
 * ▾, "go to" (docs/explorer-gaps-plan.md §3.14): the two places, each with its path; each folder
 * pinned to Favorites, with its path; then Recent, and Trash with how much it holds — what is shown
 * ([view], or else [browser]'s place) ticked.
 */
internal fun goTo(
    browser: FilesBrowserState?,
    explorer: ExplorerState,
    view: ExplorerView?,
    onPlace: (StoragePlace) -> Unit,
    onFolder: (String) -> Unit,
    onView: (ExplorerView) -> Unit,
): List<IcodeMenuAction> =
    StoragePlace.entries.map { place ->
        IcodeMenuAction(
            label = place.label,
            icon = if (place == StoragePlace.Private) Icons.Outlined.FolderCopy else Icons.Outlined.PhoneAndroid,
            onClick = { onPlace(place) },
            detail = if (place == StoragePlace.Private) WorkspaceRuntimePath.ROOT else browser?.sharedRoot?.takeIf { it.isNotEmpty() },
            checked = view == null && browser?.place == place,
        )
    } + explorer.favorites.mapIndexed { index, favorite ->
        IcodeMenuAction(favorite.name, Icons.Outlined.Star, { onFolder(favorite.location) }, detail = favorite.path, beginsGroup = index == 0)
    } + listOf(ExplorerView.Recent, ExplorerView.Trash).map { shown ->
        IcodeMenuAction(
            shown.title,
            shown.icon,
            { onView(shown) },
            detail = countOf(explorer.trash.size, "item").takeIf { shown == ExplorerView.Trash },
            checked = view == shown,
            beginsGroup = shown == ExplorerView.Recent,
        )
    }

/**
 * ☰, the view panel (§3.12), as ZArchiver's: an option panel ([IcodeOptionPanelButton]) of what
 * [viewPanel] offers. Each choice is kept: [onLook] for the list shown, [onHidden] for every listing.
 */
@Composable
internal fun ViewPanelButton(look: FilesLook, showHidden: Boolean, onLook: (FilesLook) -> Unit, onHidden: ((Boolean) -> Unit)?) {
    IcodeOptionPanelButton(Icons.Outlined.Menu, "View and sort", viewPanel(look, showHidden, onLook, onHidden))
}

/**
 * What the view panel offers: View — Detailed, Compact, Grid and, set apart where it applies, Hidden
 * files — and Sort — Name, Size, Date, Type, and Descending; those in use marked. A new sort reads
 * its own way first.
 */
internal fun viewPanel(look: FilesLook, showHidden: Boolean, onLook: (FilesLook) -> Unit, onHidden: ((Boolean) -> Unit)?): List<IcodeOptionGroup> = listOf(
    IcodeOptionGroup(
        title = "View",
        options = FilesView.entries.map { view -> IcodeOption(view.name, view.icon, look.view == view, { onLook(look.copy(view = view)) }) },
        aside = listOfNotNull(onHidden?.let { IcodeOption("Hidden", Icons.Outlined.Visibility, showHidden, { it(!showHidden) }, description = "Hidden files") }),
    ),
    IcodeOptionGroup(
        title = "Sort",
        options = FilesSort.entries.map { sort -> IcodeOption(sort.name, sort.icon, look.sort == sort, { onLook(look.sortedBy(sort)) }) } +
            IcodeOption("Desc", Icons.Outlined.ArrowDownward, look.descending, { onLook(look.copy(descending = !look.descending)) }, description = "Descending"),
    ),
)

/** What stands for each view (§3.10): in ▾, and over one with nothing in it. */
internal val ExplorerView.icon: ImageVector
    get() = when (this) {
        ExplorerView.Recent -> Icons.Outlined.History
        ExplorerView.Trash -> Icons.Outlined.DeleteOutline
        is ExplorerView.Category -> category.icon
        is ExplorerView.Largest -> FilesSort.Size.icon
        is ExplorerView.Duplicates -> Icons.Outlined.FileCopy
    }

/** What stands for each kind of file Manage storage counts (§3.10). */
internal val FileCategory.icon: ImageVector
    get() = when (this) {
        FileCategory.Images -> Icons.Outlined.Image
        FileCategory.Videos -> Icons.Outlined.Movie
        FileCategory.Audio -> Icons.Outlined.MusicNote
        FileCategory.Documents -> Icons.Outlined.Description
        FileCategory.Archives -> Icons.Outlined.Archive
        FileCategory.Apps -> Icons.Outlined.Android
        FileCategory.Code -> Icons.Outlined.Code
        FileCategory.Other -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }

private val FilesView.icon: ImageVector
    get() = when (this) {
        FilesView.Detailed -> Icons.AutoMirrored.Outlined.ViewList
        FilesView.Compact -> Icons.Outlined.ViewHeadline
        FilesView.Grid -> Icons.Outlined.GridView
    }

private val FilesSort.icon: ImageVector
    get() = when (this) {
        FilesSort.Name -> Icons.Outlined.SortByAlpha
        FilesSort.Date -> Icons.Outlined.Schedule
        FilesSort.Size -> Icons.Outlined.Straighten
        FilesSort.Type -> Icons.Outlined.Category
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
