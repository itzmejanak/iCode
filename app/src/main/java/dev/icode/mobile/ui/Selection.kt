package dev.icode.mobile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.icode.mobile.designsystem.IcodeActionBar
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.files.FileSelection

/**
 * A selection in an explorer (docs/workspaces-and-files.md) — the file explorer's folder, a view,
 * the IDE tree — kept as the screen turns: what is [ticked], in the order ticked, none outside
 * selection; and whether its [sheet], from ⋯, is open.
 */
@Stable
internal class ExplorerSelection(ticked: List<String>?, sheet: Boolean) {
    var ticked: List<String>? by mutableStateOf(ticked)
        private set
    var sheet: Boolean by mutableStateOf(sheet)

    val selecting: Boolean get() = ticked != null

    /** Into selection, with [locations] ticked: Select in an item's sheet, ⋮ › Select, a tap on an icon. */
    fun start(locations: List<String> = emptyList()) {
        ticked = locations
    }

    /** [location] ticked, or unticked when it was. */
    fun toggle(location: String) {
        ticked = FileSelection.toggled(ticked.orEmpty().toSet(), location).toList()
    }

    /** Back, ✕, or the selection acted on: out of it, its sheet closed. */
    fun leave() {
        ticked = null
        sheet = false
    }

    /**
     * What an open sheet's action also does, taken as the sheet is drawn — it closes before the action
     * runs: the selection's sheet ends the selection; an item's leaves it be.
     */
    val acted: () -> Unit get() = if (sheet) ::leave else ({})

    companion object {
        val Saver = listSaver<ExplorerSelection, String>(
            save = { listOf(it.sheet.toString(), it.selecting.toString()) + it.ticked.orEmpty() },
            restore = { saved -> ExplorerSelection(saved.drop(2).takeIf { saved[1].toBoolean() }, saved[0].toBoolean()) },
        )
    }
}

/** A selection of what [key] names — a folder, a view, a workspace — begun with [ticked] where something is. */
@Composable
internal fun rememberSelection(key: Any?, ticked: List<String>? = null): ExplorerSelection =
    rememberSaveable(key, saver = ExplorerSelection.Saver) { ExplorerSelection(ticked, sheet = false) }

/**
 * Those of [items] ticked, in their order; none outside selection. Once all that was ticked has gone —
 * moved, renamed, deleted — the selection ends.
 */
@Composable
internal fun <T> ExplorerSelection.among(items: List<T>, location: (T) -> String): List<T>? {
    val chosen = ticked?.toSet()
    val selected = chosen?.let { set -> items.filter { location(it) in set } }
    LaunchedEffect(selected?.isEmpty()) { if (selected?.isEmpty() == true && ticked?.isNotEmpty() == true) leave() }
    return selected
}

/** In selection, the top bar's own, in every explorer: Select all and Invert, on what is [shown]. */
@Composable
internal fun SelectionActions(selection: ExplorerSelection, shown: List<String>) {
    IconButton(onClick = { selection.start(FileSelection.all(shown).toList()) }) {
        Icon(Icons.Outlined.SelectAll, contentDescription = "Select all", tint = IcodeColors.TextPrimary)
    }
    IconButton(onClick = { selection.start(FileSelection.inverted(selection.ticked.orEmpty().toSet(), shown).toList()) }) {
        Icon(Icons.Outlined.FlipToBack, contentDescription = "Invert selection", tint = IcodeColors.TextPrimary)
    }
}

/**
 * A selection's foot bar: how many ([count]), what they are — and weigh, where that is known
 * ([holds]) — and what can be done to them ([actions]).
 */
@Composable
internal fun SelectionBar(count: Int, holds: String, actions: List<IcodeMenuAction>) {
    val any = count > 0
    IcodeActionBar(
        title = "$count selected",
        caption = if (any) holds else "Tap items to tick them",
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
 * What a selection of files does from its bar: Cut, Copy and Delete — each ending it
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
