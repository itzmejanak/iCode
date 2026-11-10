package dev.icode.mobile.ui.screens.entry

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeActionSheet
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeConfirmSheet
import dev.icode.mobile.designsystem.IcodeMenuAction
import dev.icode.mobile.designsystem.rememberSheetPages
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.files.FileProperties
import dev.icode.mobile.domain.files.FileWords
import dev.icode.mobile.domain.files.FilesRules
import dev.icode.mobile.domain.files.TrashRules
import dev.icode.mobile.domain.files.TrashedItem
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.PropertyLine
import dev.icode.mobile.ui.SheetIcon

/**
 * What Trash's items do (docs/explorer-gaps-plan.md §3.7), from an item's sheet or a selection's bar:
 * Restore — back where each was, ending it ([onActed]) — then [properties] where there is one, and
 * Delete permanently, asked first ([onForGood]).
 */
internal fun trashActions(
    ids: List<String>,
    dispatch: (ICodeIntent) -> Unit,
    onForGood: () -> Unit,
    onActed: () -> Unit,
    properties: IcodeMenuAction? = null,
): List<IcodeMenuAction> = listOfNotNull(
    IcodeMenuAction("Restore", Icons.Outlined.RestoreFromTrash, { dispatch(FilesIntent.Restore(ids)); onActed() }),
    properties,
    IcodeMenuAction("Delete permanently", Icons.Outlined.DeleteForever, onForGood, destructive = true),
)

private enum class TrashPage { Properties }

/** One of Trash's items, its sheet: where it was and its size over [trashActions], and its Properties within it. */
@Composable
internal fun TrashSheet(item: TrashedItem, onForGood: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val gateway = LocalICodeGateway.current
    val pages = rememberSheetPages<TrashPage>(item.id)
    when (pages.current) {
        null -> IcodeActionSheet(
            title = item.name,
            actions = trashActions(
                listOf(item.id),
                gateway::dispatch,
                onForGood,
                onActed = {},
                properties = IcodeMenuAction("Properties", Icons.Outlined.Info, { pages.open(TrashPage.Properties) }, opensPage = true),
            ),
            onClose = onClose,
            modifier = modifier,
            detail = FilesRules.gatheredDetail(item.row),
            leading = { SheetIcon(item.row.name, item.row.folder) },
        )
        TrashPage.Properties -> IcodeBottomSheet(title = "Properties", onClose = onClose, modifier = modifier, subtitle = item.name, onBack = pages.back) {
            FileProperties.trashed(item, System.currentTimeMillis()).forEach { PropertyLine(it) }
        }
    }
}

/**
 * Delete permanently, or with [all] Empty Trash, asked first (§3.7): [items] are gone for good, with
 * no way back. Confirmed, the selection it came from ends ([onActed]).
 */
@Composable
internal fun ForGoodSheet(items: List<TrashedItem>, all: Boolean, onActed: () -> Unit, onDismiss: () -> Unit) {
    val gateway = LocalICodeGateway.current
    val what = FileWords.subject(items.map { it.name })
    IcodeConfirmSheet(
        title = if (all) "Empty Trash?" else "Delete permanently?",
        message = if (all) {
            "Everything in Trash — $what, ${ByteSize.format(TrashRules.summary(items).bytes)} — is deleted for good. It cannot be restored."
        } else {
            "$what is deleted for good. It cannot be restored."
        },
        confirmLabel = if (all) "Empty Trash" else "Delete permanently",
        onConfirm = {
            gateway.dispatch(if (all) FilesIntent.EmptyTrash else FilesIntent.DeleteForGood(items.map { it.id }))
            onActed()
            onDismiss()
        },
        onDismiss = onDismiss,
        detail = items.singleOrNull()?.path,
    )
}
